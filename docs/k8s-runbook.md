# K8s Local Dev Runbook

## Current Stack (all verified `1/1 Running`)

| Pod | Image | Notes |
|---|---|---|
| api-gateway | team28/api-gateway:latest | NodePort 30080 — only external entry point |
| booking-service | team28/booking-service:latest | ClusterIP, 1 replica |
| calendar-service | team28/calendar-service:latest | ClusterIP, 1 replica |
| invoice-service | team28/invoice-service:latest | ClusterIP, 1 replica |
| provider-service | team28/provider-service:latest | ClusterIP, 1 replica |
| user-service | team28/user-service:latest | ClusterIP, 1 replica |
| booking/calendar/invoice/provider/user-postgres | postgres:17 | StatefulSet, emptyDir (data lost on pod restart) |
| rabbitmq | rabbitmq:3-management | StatefulSet, emptyDir |
| redis | redis:7-alpine | StatefulSet, emptyDir |
| mongo | mongo:7 | StatefulSet, emptyDir, WiredTiger cache 0.25 GB |
| cassandra | cassandra:4.1 | StatefulSet, emptyDir, JVM heap 256-512 MB |

> **emptyDir**: DB data is ephemeral in local dev. On Docker Desktop, the `docker.io/hostpath` provisioner is broken so PVCs are replaced with `emptyDir`. Data resets if a pod is deleted. This is fine for local testing.

---

## Prerequisites

- **Docker Desktop** (macOS) with Kubernetes enabled  
  Settings → Kubernetes → Enable Kubernetes → Apply & Restart
- **kubectl** — bundled with Docker Desktop
- **Apache Maven 3.9+** — `brew install maven`

Verify cluster is ready:
```bash
kubectl config use-context docker-desktop
kubectl get nodes
# Expected: docker-desktop   Ready   control-plane
```

---

## One-time Setup (first run only)

### 1. Build all JARs
```bash
mvn clean package -Dmaven.test.skip=true
```
> `-Dmaven.test.skip=true` skips test compilation. Some test files reference removed methods (M3 refactoring). `-DskipTests` alone still compiles tests and will fail.

### 2. Build Docker images
```bash
docker build -t team28/user-service:latest     user-service/
docker build -t team28/provider-service:latest provider-service/
docker build -t team28/booking-service:latest  booking-service/
docker build -t team28/calendar-service:latest calendar-service/
docker build -t team28/invoice-service:latest  invoice-service/
docker build -t team28/api-gateway:latest      api-gateway/
```
Docker Desktop's Kubernetes uses the same local Docker daemon — no push needed.

### 3. Create namespace and apply all manifests
```bash
kubectl create namespace booking --dry-run=client -o yaml | kubectl apply -f -

# Infrastructure (order matters)
kubectl apply -f k8s/pvcs/
kubectl apply -f k8s/statefulsets/
kubectl apply -f k8s/services/

# Wait for databases to be ready (~60s)
kubectl rollout status statefulset -n booking --timeout=180s

# Config, secrets, apps
kubectl apply -f k8s/configmaps/
kubectl apply -f k8s/secrets/
kubectl apply -f k8s/deployments/
kubectl apply -f k8s/api-gateway/
kubectl apply -f k8s/monitoring/ --recursive
```

### 4. Create Cassandra keyspace (required for calendar-service)
```bash
kubectl exec -n booking cassandra-0 -- cqlsh -e \
  "CREATE KEYSPACE IF NOT EXISTS bookingks WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1};"
```

### 5. Watch pods come up
```bash
kubectl get pods -n booking -w
```
All pods should reach `1/1 Running`. Allow up to 5 minutes — Spring Boot services take ~45–60s to start.

---

## Daily Usage

### Start / resume after Docker Desktop restart
All pods restart automatically when Docker Desktop restarts. Just watch them come back up:
```bash
kubectl get pods -n booking -w
```
If any pod is stuck in `CrashLoopBackOff`, check logs:
```bash
kubectl logs -n booking <pod-name>
```

### Access the API
Docker Desktop does **not** expose NodePorts to `localhost` directly on macOS. Use port-forward:
```bash
kubectl port-forward -n booking svc/api-gateway 8080:8080
```
Then in another terminal:
```bash
# Health check
curl http://localhost:8080/actuator/health
# {"groups":["liveness","readiness"],"status":"UP"}

# Login (no auth required)
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com","password":"password"}'

# Authenticated request
curl -H "Authorization: Bearer <token>" http://localhost:8080/api/bookings
```

### Useful kubectl commands
```bash
# All pods
kubectl get pods -n booking

# Logs (live)
kubectl logs -n booking deployment/booking-service -f

# Describe a crashing pod
kubectl describe pod -n booking <pod-name>

# Exec into a pod
kubectl exec -it -n booking deployment/user-service -- bash

# Restart a single service (e.g. after config change)
kubectl rollout restart deployment/booking-service -n booking

# Restart everything
kubectl rollout restart deployment -n booking

# Delete and recreate everything (full reset)
kubectl delete namespace booking
# Then redo step 3 above
```

---

## Rebuild and Redeploy a Service

After changing code in e.g. `booking-service`:
```bash
# 1. Rebuild JAR
mvn package -pl booking-service -am -Dmaven.test.skip=true

# 2. Rebuild image (Docker Desktop reuses local tag)
docker build -t team28/booking-service:latest booking-service/

# 3. Force pod restart (imagePullPolicy: Never uses local image)
kubectl rollout restart deployment/booking-service -n booking

# 4. Watch rollout
kubectl rollout status deployment/booking-service -n booking
```

---

## Known Issues and Fixes Applied

| Issue | Root Cause | Fix Applied |
|---|---|---|
| `ErrImagePull` on app pods | Kubernetes tried to pull from Docker Hub | Added `imagePullPolicy: Never` to all deployments |
| PVCs stuck `Pending` | Docker Desktop hostpath provisioner broken | Replaced all PVC mounts with `emptyDir` in StatefulSets |
| App crash on startup | `MANAGEMENT_METRICS_DISTRIBUTION_PERCENTILES_HISTOGRAM_HTTP_SERVER_REQUESTS: "true"` bound to `double[]`, not boolean | Removed the property from all ConfigMaps |
| MongoDB driver connects to `localhost:27017` | Kubernetes injects `MONGO_PORT=tcp://...` for any service named `mongo`, corrupting the URI | Added `SPRING_DATA_MONGODB_URI` directly to ConfigMaps |
| Health endpoint timeout (>1s) | `/actuator/health` checks Neo4j (not deployed), causing slow blocking | Switched probes to `/actuator/health/readiness` and `/actuator/health/liveness`; disabled Neo4j and MongoDB health indicators |
| `calendar-service` crash: no Cassandra bean | `application.yml` excluded all Cassandra autoconfiguration but code requires `CalendarAvailabilityEventRepository` | Cleared exclusions via `SPRING_AUTOCONFIGURE_EXCLUDE: ""` and set connection via `SPRING_CASSANDRA_*` env vars |
| MongoDB OOMKilled | 256Mi limit too low for MongoDB 7 (WiredTiger needs ≥256MB cache alone) | Increased limit to 768Mi, set `--wiredTigerCacheSizeGB 0.25` |
| calendar-service logback crash | Used Loki4j 1.x `<labels>` API with 2.0.0 library | Migrated to `<format><label><pattern>` structure |

---

## Commit and Push Remaining Changes

The following files were changed locally but not yet committed:

```bash
# Stage all local changes
git add \
  calendar-service/src/main/resources/logback-spring.xml \
  k8s/statefulsets/booking-postgres-statefulset.yaml \
  k8s/statefulsets/calendar-postgres-statefulset.yaml \
  k8s/statefulsets/elasticsearch-statefulset.yaml \
  k8s/statefulsets/invoice-postgres-statefulset.yaml \
  k8s/statefulsets/provider-postgres-statefulset.yaml \
  k8s/statefulsets/rabbitmq-statefulset.yaml \
  k8s/statefulsets/user-postgres-statefulset.yaml \
  k8s/statefulsets/mongo-statefulset.yaml \
  k8s/statefulsets/redis-statefulset.yaml \
  k8s/statefulsets/cassandra-statefulset.yaml \
  k8s/services/mongo-svc.yaml \
  k8s/services/redis-svc.yaml \
  k8s/services/cassandra-svc.yaml \
  docs/k8s-runbook.md

git commit -m "fix(infra): resolve local K8s startup issues and add missing statefulsets (55-24423)"

git push
```

This commit covers:
- `emptyDir` volumes on all StatefulSets (fixes Docker Desktop PVC provisioner)
- MongoDB StatefulSet with WiredTiger cache limit
- Redis StatefulSet
- Cassandra StatefulSet + keyspace setup
- calendar-service Loki4j 2.x logback fix
