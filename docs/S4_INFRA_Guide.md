# S4-INFRA Deep Dive — Ahmed Yasser Tawfik (calendar-service)

## Your Slice Overview

**Slice #12 — S4-INFRA** covers:
1. calendar-service gateway route in api-gateway
2. Prometheus scrape job entry for calendar-service
3. Final dashboard JSON (calendar-dashboard.json)
4. Grafana K8s (Deployment + datasources ConfigMap + dashboards ConfigMap + PVC + NodePort 30030)
5. Cassandra K8s (StatefulSet + Service)

You own the **shared observability infrastructure** — Grafana and Cassandra for the entire team.

---

## 1. GATEWAY ROUTE — How Traffic Reaches calendar-service

### Concept: What is Spring Cloud Gateway?
A reverse proxy that sits in front of all services. ALL external HTTP requests go through the gateway. It validates JWT, adds headers (X-User-Id, X-User-Role), then routes to the correct service.

### Your Route in api-gateway/application.yml

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: calendar-service
          uri: ${CALENDAR_SERVICE_URL:http://calendar-service:8080}
          predicates:
            - Path=/api/timeslots/**,/api/calendar/**
```

**What this means:**
- Any request matching `/api/timeslots/**` or `/api/calendar/**` gets forwarded to `http://calendar-service:8080`
- The gateway first runs JwtGatewayFilter (validates JWT token, sets X-User-Id header)
- Then it proxies the request to your calendar-service

### How the URL is set in K8s

```yaml
# k8s/configmaps/gateway-configmap.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: gateway-config
  namespace: booking
data:
  CALENDAR_SERVICE_URL: http://calendar-service:8080  # K8s DNS
```

**Flow:**
```
User → curl http://springshoes.local/api/timeslots/available?date=2026-04-15
  → Ingress → api-gateway pod
  → JwtGatewayFilter validates JWT
  → Route match: /api/timeslots/** → forward to calendar-service:8080
  → calendar-service pod handles the request
  → Response flows back
```

---

## 2. PROMETHEUS SCRAPE JOB — How Metrics Are Collected

### Concept: What is a Scrape Job?
Prometheus doesn't receive metrics — it PULLS them. A scrape job tells Prometheus: "Every 15 seconds, call this URL and store the metrics."

### Your Entry in prometheus-configmap.yaml

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: prometheus-config
  namespace: booking
data:
  prometheus.yml: |
    global:
      scrape_interval: 15s     # Pull metrics every 15 seconds

    scrape_configs:
      - job_name: booking-services
        metrics_path: /actuator/prometheus    # Spring Boot actuator endpoint
        static_configs:
          - targets:
              - user-service.booking.svc.cluster.local:8080
              - provider-service.booking.svc.cluster.local:8080
              - booking-service.booking.svc.cluster.local:8080
              - calendar-service.booking.svc.cluster.local:8080   # ← YOUR ENTRY
              - api-gateway.booking.svc.cluster.local:8080

      - job_name: invoice-service
        metrics_path: /actuator/prometheus
        static_configs:
          - targets:
              - invoice-service.booking.svc.cluster.local:8080
```

**What happens every 15 seconds:**
```
Prometheus pod → GET http://calendar-service.booking.svc.cluster.local:8080/actuator/prometheus
  → Receives text like:
    http_server_requests_seconds_count{uri="/api/timeslots/available",status="200"} 1547
    hikaricp_connections_active{pool="HikariPool-1"} 3
  → Stores it in its time-series database
```

**Why full DNS name?** `calendar-service.booking.svc.cluster.local` — if Prometheus is in a different namespace (e.g., `monitoring`), it needs the full K8s DNS. Within the same namespace, `calendar-service:8080` would suffice.

---

## 3. FINAL DASHBOARD JSON

### File: k8s/monitoring/grafana/dashboards/calendar-dashboard.json

This file defines your calendar-service Grafana dashboard with 6 panels:

| Panel | Type | Datasource | What It Shows |
|-------|------|-----------|---------------|
| 1 | logs | Loki (LogQL) | Error rate — counts ERROR log lines per 5 min |
| 2 | logs | Loki (LogQL) | Feign call outcomes — shows Feign HTTP calls |
| 3 | logs | Loki (LogQL) | RabbitMQ events — booking.completed/cancelled |
| 4 | timeseries | Prometheus (PromQL) | HTTP request rate per endpoint per status code |
| 5 | timeseries | Prometheus (PromQL) | Latency P50/P95/P99 for /api/timeslots/* |
| 6 | timeseries | Prometheus (PromQL) | HikariCP connection pool (active/pending/total) |

This JSON gets embedded into the dashboards ConfigMap (see Section 4 below).

---

## 4. GRAFANA K8s — The Complete Stack

### What You Built: 5 Resources

You deployed Grafana to Kubernetes. This required creating multiple resources that work together:

```
Datasources ConfigMap → tells Grafana where Prometheus and Loki are
Dashboards ConfigMap  → contains ALL 5 service dashboard JSONs
PVC                   → persistent storage for Grafana settings
Deployment            → runs the Grafana container
NodePort Service      → exposes Grafana on port 30030
```

### 4a. Dashboards ConfigMap (ALL 5 Dashboards)

**File:** `k8s/monitoring/grafana/grafana-dashboards-configmap.yaml`

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: grafana-dashboards
  namespace: monitoring
  labels:
    grafana_dashboard: "1"
data:
  user-dashboard.json: |
    { "title": "User Service Dashboard", "uid": "user-service", ... }
  provider-dashboard.json: |
    { "title": "Provider Service Dashboard", "uid": "provider-service", ... }
  booking-dashboard.json: |
    { "title": "Booking Service Dashboard", "uid": "booking-service", ... }
  calendar-dashboard.json: |
    { "title": "Calendar Service Dashboard", "uid": "calendar-service", ... }
  invoice-dashboard.json: |
    { "title": "Invoice Service Dashboard", "uid": "invoice-service", ... }
```

**Why a ConfigMap for dashboards?**
Instead of manually importing dashboards through the Grafana UI, you embed them in a ConfigMap. Grafana's dashboard provisioning sidecar reads this ConfigMap and auto-loads all 5 dashboards on startup. If the pod restarts, all dashboards are still there.

**Why all 5 in one ConfigMap?**
Your slice (S4-INFRA) owns the Grafana infrastructure. You embed ALL team dashboards so the complete observability stack is deployed in one `kubectl apply`.

### 4b. Datasources — Pointing at Loki & Prometheus

Grafana needs to know WHERE Prometheus and Loki are. This is configured via a datasources provisioning file mounted as a ConfigMap:

```yaml
# Datasource provisioning (embedded in Grafana deployment or separate ConfigMap)
apiVersion: 1
datasources:
  - name: Prometheus
    type: prometheus
    url: http://prometheus.monitoring.svc.cluster.local:9090
    isDefault: true
    access: proxy
  - name: Loki
    type: loki
    url: http://loki.monitoring.svc.cluster.local:3100
    access: proxy
```

**Key:** Grafana runs in the `monitoring` namespace. Prometheus and Loki are in the same namespace, so short DNS names work. But from the `booking` namespace, you'd need the full names.

### 4c. PVC — Persistent Storage

```yaml
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: grafana-pvc
  namespace: monitoring
spec:
  accessModes: [ReadWriteOnce]
  resources:
    requests:
      storage: 1Gi
```

**Why PVC?** Without it, Grafana loses all its settings, alert rules, and custom dashboards every time the pod restarts. PVC stores Grafana's SQLite database persistently.

### 4d. Deployment — Running Grafana

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: grafana
  namespace: monitoring
spec:
  replicas: 1
  template:
    spec:
      containers:
        - name: grafana
          image: grafana/grafana:latest
          ports:
            - containerPort: 3000
          volumeMounts:
            - name: grafana-storage
              mountPath: /var/lib/grafana         # Grafana data directory
            - name: dashboards
              mountPath: /etc/grafana/provisioning/dashboards
            - name: datasources
              mountPath: /etc/grafana/provisioning/datasources
      volumes:
        - name: grafana-storage
          persistentVolumeClaim:
            claimName: grafana-pvc
        - name: dashboards
          configMap:
            name: grafana-dashboards              # The 5-dashboard ConfigMap
        - name: datasources
          configMap:
            name: grafana-datasources             # Prometheus + Loki URLs
```

**How provisioning works:**
Grafana looks at `/etc/grafana/provisioning/dashboards/` on startup. The ConfigMap is mounted there. Grafana reads the JSON files and creates the dashboards automatically.

### 4e. NodePort Service — External Access

```yaml
apiVersion: v1
kind: Service
metadata:
  name: grafana
  namespace: monitoring
spec:
  type: NodePort
  selector:
    app: grafana
  ports:
    - port: 3000
      targetPort: 3000
      nodePort: 30030        # ← Access via http://$(minikube ip):30030
```

**Why NodePort 30030?** Grafana is the UI that evaluators/developers access. NodePort exposes it on a fixed port so you can open `http://192.168.49.2:30030` in a browser. ClusterIP wouldn't work because it's only accessible inside the cluster.

---

## 5. CASSANDRA K8s — Shared Infrastructure

### Concept: What is Cassandra?
A NoSQL database optimized for TIME-SERIES data and high write throughput. In your project, calendar-service uses it for the Availability Snapshot feature (S4-F11) — recording provider availability over time.

### 5a. StatefulSet

```yaml
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: cassandra
  namespace: booking
spec:
  serviceName: cassandra
  replicas: 1
  template:
    spec:
      containers:
        - name: cassandra
          image: cassandra:4.1
          ports:
            - containerPort: 9042       # CQL port
              name: cql
          env:
            - name: CASSANDRA_CLUSTER_NAME
              value: booking-cluster
            - name: CASSANDRA_DC
              value: datacenter1
            - name: CASSANDRA_ENDPOINT_SNITCH
              value: SimpleSnitch
            - name: JVM_OPTS
              value: "-Xms256m -Xmx512m"
          resources:
            requests:
              memory: "512Mi"
              cpu: "200m"
            limits:
              memory: "1Gi"
              cpu: "500m"
          readinessProbe:
            exec:
              command: ["cqlsh", "-e", "describe cluster"]
            initialDelaySeconds: 60     # Cassandra is SLOW to start
            periodSeconds: 15
            failureThreshold: 10        # Give it 10 chances (2.5 min total)
          volumeMounts:
            - name: cassandra-data
              mountPath: /var/lib/cassandra
  volumeClaimTemplates:
    - metadata:
        name: cassandra-data
      spec:
        accessModes: ["ReadWriteOnce"]
        storageClassName: local-path
        resources:
          requests:
            storage: 2Gi
```

**Why StatefulSet?** Same as PostgreSQL — Cassandra is stateful. Needs persistent storage and stable pod names.

**Why initialDelaySeconds: 60?** Cassandra takes 60+ seconds to fully start (JVM warmup, gossip protocol, schema loading). Checking too early would cause K8s to think it's broken.

**Why failureThreshold: 10?** Combined with periodSeconds: 15, this gives Cassandra 60 + (10 × 15) = 210 seconds (3.5 minutes) to become ready. Cassandra genuinely needs this much time.

**Why JVM_OPTS: "-Xms256m -Xmx512m"?** Cassandra runs on the JVM. These set minimum (256MB) and maximum (512MB) heap size. In MiniKube with limited resources, you constrain Cassandra's memory appetite.

**readinessProbe:** `cqlsh -e "describe cluster"` — connects to Cassandra's CQL interface and runs a query. If it succeeds, Cassandra is ready.

### 5b. ClusterIP Service

```yaml
apiVersion: v1
kind: Service
metadata:
  name: cassandra
  namespace: booking
spec:
  selector:
    app: cassandra
  ports:
    - port: 9042
      targetPort: 9042
  type: ClusterIP
```

**How calendar-service connects:**
```yaml
# calendar-service application.yml
spring:
  cassandra:
    contact-points: ${CASSANDRA_CONTACT_POINTS:cassandra}  # K8s DNS name
    port: 9042
    keyspace-name: bookingks
```

---

## 6. HOW IT ALL CONNECTS — The Full Observability Pipeline

```
1. calendar-service handles HTTP requests
   └→ Spring Actuator auto-generates metrics (request counts, latency histograms, connection pool stats)
   └→ Loki4J pushes JSON logs to Loki

2. /actuator/prometheus endpoint exposes raw metrics text
   └→ Prometheus scrapes every 15s (configured in prometheus-configmap.yaml)

3. logback-spring.xml pushes structured JSON logs
   └→ Loki stores them (label-indexed)

4. Grafana reads from both:
   └→ PromQL panels query Prometheus (Panels 4, 5, 6)
   └→ LogQL panels query Loki (Panels 1, 2, 3)

5. You access Grafana at http://$(minikube ip):30030
   └→ Select "Calendar Service Dashboard"
   └→ See all 6 panels live
```

---

## 7. DEPLOYMENT ORDER — CRITICAL

When applying K8s manifests, ORDER MATTERS:

```bash
# 1. Namespaces first
kubectl create namespace booking
kubectl create namespace monitoring

# 2. Secrets (credentials needed by everything)
kubectl apply -f k8s/secrets/

# 3. PVCs (storage needed by databases)
kubectl apply -f k8s/pvcs/

# 4. Shared databases (StatefulSets — these take time to start)
kubectl apply -f k8s/statefulsets/cassandra-statefulset.yaml
kubectl apply -f k8s/statefulsets/calendar-postgres-statefulset.yaml

# 5. Database services (DNS names for databases)
kubectl apply -f k8s/services/cassandra-svc.yaml
kubectl apply -f k8s/services/calendar-postgres-svc.yaml

# 6. ConfigMaps (environment variables for app services)
kubectl apply -f k8s/configmaps/

# 7. Prometheus config
kubectl apply -f k8s/monitoring/prometheus/

# 8. Grafana (datasources + dashboards + deployment)
kubectl apply -f k8s/monitoring/grafana/

# 9. Application Deployments + Services
kubectl apply -f k8s/deployments/
kubectl apply -f k8s/services/

# 10. Gateway + Ingress (last — needs all services to be ready)
kubectl apply -f k8s/api-gateway/
```

---

## 8. EVALUATOR QUESTIONS & ANSWERS (25 Questions)

**Q1: What is the calendar-service gateway route?**
A: In api-gateway's application.yml, any request matching `/api/timeslots/**` or `/api/calendar/**` is forwarded to `http://calendar-service:8080`.

**Q2: How does the gateway know calendar-service's URL in K8s?**
A: The ConfigMap `gateway-config` sets `CALENDAR_SERVICE_URL: http://calendar-service:8080`. K8s DNS resolves `calendar-service` to the ClusterIP Service.

**Q3: What is a Prometheus scrape job?**
A: A config entry that tells Prometheus: "Every N seconds, pull metrics from this URL." Calendar-service is scraped at `/actuator/prometheus` every 15 seconds.

**Q4: Where is the calendar-service scrape entry?**
A: In `k8s/monitoring/prometheus/prometheus-configmap.yaml`, under `scrape_configs → job_name: booking-services → targets → calendar-service.booking.svc.cluster.local:8080`.

**Q5: Why is the full DNS name used in the scrape target?**
A: Because Prometheus may run in a different namespace (monitoring). Cross-namespace communication requires the full DNS: `service.namespace.svc.cluster.local`.

**Q6: What does the dashboards ConfigMap contain?**
A: All 5 service dashboard JSONs: user, provider, booking, calendar, invoice. Each contains 6 panels (3 LogQL + 3 PromQL).

**Q7: Why embed dashboards in a ConfigMap instead of importing manually?**
A: Automation. ConfigMap-based provisioning means dashboards are automatically loaded on Grafana startup. If the pod restarts, all dashboards are intact. No manual import needed.

**Q8: What does Grafana's dashboard provisioning do?**
A: Grafana reads JSON files from `/etc/grafana/provisioning/dashboards/` on startup. The ConfigMap is mounted at that path, so dashboards appear automatically.

**Q9: What datasources does Grafana connect to?**
A: Two: Prometheus (for PromQL metrics at `http://prometheus:9090`) and Loki (for LogQL logs at `http://loki:3100`).

**Q10: Why is Grafana exposed as NodePort 30030?**
A: Grafana is a UI that users access via browser. NodePort exposes it externally at `http://$(minikube ip):30030`. ClusterIP only works inside the cluster.

**Q11: What is the difference between NodePort and ClusterIP?**
A: ClusterIP = only reachable inside the cluster (for service-to-service). NodePort = reachable from outside on a specific port (30000-32767).

**Q12: Why does Grafana need a PVC?**
A: Grafana stores settings, user preferences, and alert rules in a SQLite database at `/var/lib/grafana`. Without PVC, everything is lost on pod restart.

**Q13: Why is Cassandra a StatefulSet?**
A: Cassandra stores data persistently. StatefulSet provides stable pod names and persistent volumes. A Deployment would lose data on restart.

**Q14: Why does Cassandra have initialDelaySeconds: 60?**
A: Cassandra takes 60+ seconds to start (JVM warmup, gossip protocol, schema loading). Without this delay, K8s would think the pod is broken and restart it in a loop.

**Q15: What does `cqlsh -e "describe cluster"` do?**
A: It's the readiness probe command. It connects to Cassandra's CQL shell and runs a query. If it succeeds, Cassandra is ready to accept connections.

**Q16: Why failureThreshold: 10 for Cassandra?**
A: Combined with periodSeconds: 15, this gives Cassandra 60 + (10 × 15) = 210 seconds to become ready. Cassandra genuinely needs this time in resource-constrained environments.

**Q17: How does calendar-service connect to Cassandra?**
A: Via Spring Data Cassandra. In application.yml: `spring.cassandra.contact-points: cassandra` → K8s DNS resolves to Cassandra's ClusterIP Service → port 9042.

**Q18: What does Cassandra store in this project?**
A: Time-series availability snapshots (S4-F11). Each record has (providerId, timestamp, totalSlots, availableSlots, bookedSlots, utilizationRate).

**Q19: What is the deployment order and why?**
A: Secrets → PVCs → StatefulSets → Services → ConfigMaps → Prometheus → Grafana → Deployments → Gateway. Databases must be ready before apps, and apps must be ready before the gateway routes to them.

**Q20: What happens if you apply the deployment before the database?**
A: Calendar-service starts, tries to connect to calendar-postgres, fails, and enters CrashLoopBackOff. It keeps restarting until the database is available.

**Q21: What is `scrape_interval: 15s`?**
A: Prometheus pulls metrics from all targets every 15 seconds. Lower = more data points but more resource usage. 15s is the standard.

**Q22: What are the two scrape jobs in prometheus-configmap?**
A: (1) `booking-services` — scrapes user, provider, booking, calendar, and api-gateway. (2) `invoice-service` — scrapes invoice-service separately (could be combined, but kept separate for clarity).

**Q23: What does `storageClassName: local-path` mean?**
A: Uses MiniKube's local-path provisioner to create storage on the host machine's filesystem. In production, you'd use cloud storage classes like `gp2` (AWS) or `pd-ssd` (GCP).

**Q24: How many namespaces are used?**
A: Two: `booking` (all application services + databases) and `monitoring` (Grafana + Prometheus + Loki).

**Q25: What is the `grafana_dashboard: "1"` label?**
A: A label selector used by Grafana's sidecar or provisioner to auto-discover ConfigMaps that contain dashboards. Only ConfigMaps with this label are picked up.
