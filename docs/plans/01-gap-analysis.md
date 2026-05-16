# Gap Analysis — What Exists vs. What's Missing

> Cross-referenced against `docs/booking-m3.md` §10 and §11. Generated 2026-05-16 from `main`.

---

## Section 10 — K8s Deployment

### ✅ Already Exists on `main` (DO NOT RECREATE)

| Category | Files |
|---|---|
| **Postgres Secrets** | `k8s/secrets/{user,provider,booking,calendar,invoice}-postgres-secret.yaml` (5/5) |
| **Postgres PVCs** | `k8s/pvcs/{user,provider,booking,calendar,invoice}-postgres-pvc.yaml` (5/5) |
| **Postgres StatefulSets** | `k8s/statefulsets/{user,provider,booking,calendar,invoice}-postgres-statefulset.yaml` (5/5) |
| **Postgres headless Services** | `k8s/services/{user,provider,booking,calendar,invoice}-postgres-svc.yaml` (5/5) |
| **RabbitMQ** | `k8s/pvcs/rabbitmq-pvc.yaml`, `k8s/statefulsets/rabbitmq-statefulset.yaml`, `k8s/services/rabbitmq-svc.yaml` |
| **Elasticsearch** | `k8s/pvcs/elasticsearch-pvc.yaml`, `k8s/statefulsets/elasticsearch-statefulset.yaml`, `k8s/services/elasticsearch-svc.yaml` |
| **Invoice Service (complete)** | `k8s/configmaps/invoice-service-configmap.yaml`, `k8s/deployments/invoice-service-deployment.yaml`, `k8s/services/invoice-service-svc.yaml`, `k8s/secrets/invoice-service-secret.yaml` |
| **Dockerfiles** | `{user,provider,booking,calendar,invoice}-service/Dockerfile`, `api-gateway/Dockerfile` |

### ❌ Missing (Must Create)

| Category | Files to Create |
|---|---|
| **Namespaces** | `k8s/namespaces/namespace.yaml`, `k8s/namespaces/monitoring-namespace.yaml` |
| **JWT Secret** | `k8s/secrets/jwt-secret.yaml` |
| **MongoDB K8s** | `k8s/pvcs/mongo-pvc.yaml`, `k8s/statefulsets/mongo-statefulset.yaml`, `k8s/services/mongo-svc.yaml` |
| **Redis K8s** | `k8s/pvcs/redis-pvc.yaml`, `k8s/statefulsets/redis-statefulset.yaml`, `k8s/services/redis-svc.yaml` |
| **Neo4j K8s** | `k8s/pvcs/neo4j-pvc.yaml`, `k8s/statefulsets/neo4j-statefulset.yaml`, `k8s/services/neo4j-svc.yaml` |
| **Cassandra K8s** | `k8s/pvcs/cassandra-pvc.yaml`, `k8s/statefulsets/cassandra-statefulset.yaml`, `k8s/services/cassandra-svc.yaml` |
| **Service ConfigMaps** | `k8s/configmaps/{user,provider,booking,calendar}-service-configmap.yaml`, `k8s/configmaps/gateway-configmap.yaml` |
| **Service Deployments** | `k8s/deployments/{user,provider,booking,calendar}-service-deployment.yaml` |
| **Service ClusterIP SVCs** | `k8s/services/{user,provider,booking,calendar}-service-svc.yaml` |
| **API Gateway K8s** | `k8s/api-gateway/gateway-deployment.yaml`, `k8s/api-gateway/gateway-service.yaml` |

---

## Section 11 — Observability

### ✅ Already Exists on `main` (DO NOT RECREATE)

| Item | Details |
|---|---|
| `logback-spring.xml` with Loki4J | All 5 services have it |
| `CorrelationIdFilter` | All 5 services have it |
| `FeignCorrelationConfig` | All 5 services have it |
| Prometheus scrape ConfigMap | `k8s/monitoring/prometheus/prometheus-configmap.yaml` (covers all 5 + gateway) |
| Grafana dashboards ConfigMap | `k8s/monitoring/grafana/grafana-dashboards-configmap.yaml` (invoice only) |
| Invoice dashboard JSON | `k8s/monitoring/grafana/dashboards/invoice-dashboard.json` |
| Invoice actuator config | `invoice-service/src/main/resources/application.yml` already has `management:` block |

### ❌ Missing (Must Create/Edit)

| Item | Details |
|---|---|
| **Actuator config** | Add `management:` block to `{user,provider,booking,calendar}-service/src/main/resources/application.yml` |
| **Dashboard JSONs** | Create `k8s/monitoring/grafana/dashboards/{user,provider,booking,calendar}-dashboard.json` |
| **Prometheus ConfigMap namespace fix** | Change `namespace: booking` → `namespace: monitoring` in existing `prometheus-configmap.yaml` |
| **Monitoring stack** | Create `k8s/monitoring/loki/` (ConfigMap, PVC, StatefulSet, Service) |
| | Create `k8s/monitoring/prometheus/` (PVC, Deployment, Service) |
| | Create `k8s/monitoring/grafana/` (datasources ConfigMap, PVC, Deployment, NodePort Service 30030) |
| **Update dashboards ConfigMap** | Add user/provider/booking/calendar dashboards to `grafana-dashboards-configmap.yaml` |

---

## Environment Variable Reference (from each service's application.yml)

These are the `${VAR:default}` patterns used. ConfigMaps must set these exact env var names.

| Service | Env Var | Default | Purpose |
|---|---|---|---|
| ALL | `POSTGRES_HOST` | `<svc>-postgres` | PostgreSQL host |
| ALL | `POSTGRES_PORT` | `5432` | PostgreSQL port |
| ALL | `POSTGRES_DB` | `bookingdb-<svc>` | PostgreSQL database |
| ALL | `POSTGRES_USER` | `user` | PostgreSQL username |
| ALL | `POSTGRES_PASSWORD` | `password` | PostgreSQL password |
| ALL | `REDIS_HOST` | `redis` | Redis host |
| ALL | `REDIS_PORT` | `6379` | Redis port |
| ALL | `REDIS_PASSWORD` | `redispass` | Redis password |
| ALL | `RABBITMQ_HOST` | `rabbitmq` | RabbitMQ host |
| ALL | `RABBITMQ_PORT` | `5672` | RabbitMQ port |
| ALL | `RABBITMQ_USER` | `guest` | RabbitMQ user |
| ALL | `RABBITMQ_PASS` | `guest` | RabbitMQ password |
| ALL | `MONGO_HOST` | `mongo` | MongoDB host |
| ALL | `MONGO_USERNAME` | `root` | MongoDB user |
| ALL | `MONGO_PASSWORD` | `rootpass` | MongoDB password |
| ALL | `MONGO_DATABASE` | `bookingmongo` | MongoDB database |
| ALL | `LOKI_HOST` | `loki` | Loki host (used in logback-spring.xml) |
| booking | `NEO4J_URI` | `bolt://neo4j:7687` | Neo4j bolt URI |
| booking | `NEO4J_USERNAME` | `neo4j` | Neo4j user |
| booking | `NEO4J_PASSWORD` | `neo4jpass` | Neo4j password |
| provider | `ELASTICSEARCH_URIS` | `http://elasticsearch:9200` | Elasticsearch URI |
| calendar | `CASSANDRA_CONTACT_POINTS` | `cassandra` | Cassandra host |
| calendar | `CASSANDRA_PORT` | `9042` | Cassandra port |
| gateway | `USER_SERVICE_URL` | `http://user-service:8080` | Route target |
| gateway | `PROVIDER_SERVICE_URL` | `http://provider-service:8080` | Route target |
| gateway | `BOOKING_SERVICE_URL` | `http://booking-service:8080` | Route target |
| gateway | `CALENDAR_SERVICE_URL` | `http://calendar-service:8080` | Route target |
| gateway | `INVOICE_SERVICE_URL` | `http://invoice-service:8080` | Route target |
| gateway | `JWT_SECRET` | `fJ8z2vK6yQ3pX9mR4wT5sN7bE1uI0aH8oL2cD6gV4eK3=` | JWT signing key |
