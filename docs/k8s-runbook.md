# K8s Local Dev Runbook

## Current Stack (all verified `1/1 Running`)

| Pod | Image | Storage | Notes |
|---|---|---|---|
| api-gateway | team28/api-gateway:latest | — | NodePort 30080 — only external entry point |
| booking-service | team28/booking-service:latest | — | ClusterIP, 1 replica |
| calendar-service | team28/calendar-service:latest | — | ClusterIP, 1 replica |
| invoice-service | team28/invoice-service:latest | — | ClusterIP, 1 replica |
| provider-service | team28/provider-service:latest | — | ClusterIP, 1 replica |
| user-service | team28/user-service:latest | — | ClusterIP, 1 replica |
| booking-postgres | postgres:17 | 1Gi PVC | StatefulSet, local-path provisioner |
| calendar-postgres | postgres:17 | 1Gi PVC | StatefulSet, local-path provisioner |
| invoice-postgres | postgres:17 | 1Gi PVC | StatefulSet, local-path provisioner |
| provider-postgres | postgres:17 | 1Gi PVC | StatefulSet, local-path provisioner |
| user-postgres | postgres:17 | 1Gi PVC | StatefulSet, local-path provisioner |
| rabbitmq | rabbitmq:3-management | 1Gi PVC | StatefulSet, local-path provisioner |
| redis | redis:7-alpine | 512Mi PVC | StatefulSet, local-path provisioner |
| mongo | mongo:7 | 2Gi PVC | StatefulSet, WiredTiger cache 0.25 GB |
| cassandra | cassandra:4.1 | 2Gi PVC | StatefulSet, JVM heap 256–512 MB |

> **Data persistence**: All databases use PersistentVolumeClaims backed by the Rancher local-path provisioner. Data survives pod restarts and pod deletions. Data is lost only if the PVC itself is deleted (e.g. `kubectl delete namespace booking` or Docker Desktop full reset / factory reset).

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

### 0. Install Rancher local-path provisioner

Docker Desktop's built-in `docker.io/hostpath` StorageClass provisioner does not work on macOS. Install the Rancher local-path provisioner first:

```bash
kubectl apply -f k8s/infra/local-path-provisioner.yaml
```

Wait for it to be ready:
```bash
kubectl rollout status deployment/local-path-provisioner -n local-path-storage
```

This creates a `local-path` StorageClass that stores data in `/opt/local-path-provisioner/` inside Docker Desktop's VM. All StatefulSet `volumeClaimTemplates` reference this StorageClass.

### 1. Build all JARs
```bash
mvn clean package -Dmaven.test.skip=true
```
> `-Dmaven.test.skip=true` skips test compilation. `-DskipTests` alone still compiles tests and will fail because some test files reference methods removed during M3 refactoring.

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

# Infrastructure (order matters — provisioner must be ready before StatefulSets)
kubectl apply -f k8s/statefulsets/
kubectl apply -f k8s/services/

# Wait for databases to be ready (~60–120s)
kubectl rollout status statefulset -n booking --timeout=300s

# Config, secrets, apps
kubectl apply -f k8s/configmaps/
kubectl apply -f k8s/secrets/
kubectl apply -f k8s/deployments/
kubectl apply -f k8s/api-gateway/
kubectl apply -f k8s/monitoring/ --recursive
```

> **PVC binding**: local-path uses `WaitForFirstConsumer` — PVCs show `Pending` until a pod is scheduled that claims them. This is expected and resolves automatically.

### 4. Create Cassandra keyspace (required for calendar-service)
```bash
kubectl exec -n booking cassandra-0 -- cqlsh -e \
  "CREATE KEYSPACE IF NOT EXISTS bookingks WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1};"
```
Run this after Cassandra is `1/1 Running`. calendar-service will crash without the keyspace.

### 5. Watch pods come up
```bash
kubectl get pods -n booking -w
```
All pods should reach `1/1 Running`. Allow up to 5 minutes — Spring Boot services take ~45–60s to start.

---

## Seed Users

`user-service` seeds these accounts on first startup (if the database is empty):

| Email | Password | Role |
|---|---|---|
| admin@springshoes.com | Admin@1234 | ADMIN |
| alice@springshoes.com | Alice@1234 | CLIENT |
| bob@springshoes.com | Bob@1234 | CLIENT |

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
  -d '{"email":"alice@springshoes.com","password":"Alice@1234"}'

# Authenticated request
curl -H "Authorization: Bearer <token>" http://localhost:8080/api/bookings
```

### Useful kubectl commands
```bash
# All pods
kubectl get pods -n booking

# All PVCs (verify Bound status)
kubectl get pvc -n booking

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

# Delete and recreate everything (full reset — also deletes PVC data)
kubectl delete namespace booking
# Then redo steps 3–5 above
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
| PVCs stuck `Pending` indefinitely | Docker Desktop `docker.io/hostpath` provisioner doesn't respond on macOS | Installed Rancher `local-path` provisioner; all StatefulSets use `storageClassName: local-path` |
| App crash on startup | `MANAGEMENT_METRICS_DISTRIBUTION_PERCENTILES_HISTOGRAM_HTTP_SERVER_REQUESTS: "true"` bound to `double[]`, not boolean | Removed the property from all ConfigMaps |
| MongoDB driver connects to `localhost:27017` | Kubernetes injects `MONGO_PORT=tcp://...` for any service named `mongo`, corrupting the URI | Added `SPRING_DATA_MONGODB_URI` directly to ConfigMaps |
| Health endpoint timeout (>1s) | `/actuator/health` checks Neo4j (not deployed), causing slow blocking | Switched probes to `/actuator/health/readiness` and `/actuator/health/liveness`; disabled Neo4j and MongoDB health indicators |
| `calendar-service` crash: no Cassandra bean | `application.yml` excluded all Cassandra autoconfiguration but code requires `CalendarAvailabilityEventRepository` | Deployed Cassandra StatefulSet, created keyspace, set `SPRING_AUTOCONFIGURE_EXCLUDE: ""` and `SPRING_CASSANDRA_*` env vars |
| MongoDB OOMKilled | 256Mi limit too low for MongoDB 7 (WiredTiger needs ≥256MB cache alone) | Increased limit to 768Mi, set `--wiredTigerCacheSizeGB 0.25` |
| calendar-service logback crash | Used Loki4j 1.x `<labels>` API with 2.0.0 library | Migrated to `<format><label><pattern>` structure |
| Maven test compilation failure | Test files reference methods removed during M3 refactoring | Use `-Dmaven.test.skip=true` (not `-DskipTests`) |
