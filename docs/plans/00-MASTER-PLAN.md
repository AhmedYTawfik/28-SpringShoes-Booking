# MASTER PLAN — Sections 10 & 11 Complete Implementation

## What This Is

This is the master orchestrator for implementing **Section 10 (Kubernetes Deployment)** and **Section 11 (Observability)** from `docs/booking-m3.md`. When every phase below is complete, both sections will be **100% done**.

## How to Use This Plan

1. **Read `01-gap-analysis.md` FIRST.** It tells you exactly what already exists on `main` and what is missing. Do NOT recreate files that already exist.
2. **Execute phases in order.** Each phase has its own file. They MUST be done sequentially because later phases depend on earlier ones.
3. **Each phase file is self-contained.** It has the exact file paths, exact YAML/JSON content, and exact `application.yml` edits. Copy them verbatim.
4. **Reference the existing invoice-service files as templates.** Invoice-service is fully done. If something is unclear, look at these files on `main` for the canonical format:
   - `k8s/configmaps/invoice-service-configmap.yaml`
   - `k8s/deployments/invoice-service-deployment.yaml`
   - `k8s/services/invoice-service-svc.yaml`
   - `k8s/secrets/invoice-service-secret.yaml`
   - `k8s/monitoring/grafana/dashboards/invoice-dashboard.json`
   - `invoice-service/src/main/resources/application.yml` (for actuator config)
5. **Commit after each phase.** Use Conventional Commits with the format specified in each phase file.
6. **The spec is `docs/booking-m3.md`.** If any detail here contradicts the spec, the spec wins.

## Execution Order

| Order | File | Phase | Description |
|---|---|---|---|
| 1 | `01-gap-analysis.md` | READ ONLY | Understand what exists and what's missing |
| 2 | `02-phase-shared-infra.md` | Phase A | Namespaces, JWT secret, NoSQL K8s manifests (Mongo, Redis, Neo4j, Cassandra) |
| 3 | `03-phase-monitoring-stack.md` | Phase B | Loki, Prometheus, Grafana K8s manifests (monitoring namespace) |
| 4 | `04-phase-services-k8s.md` | Phase C | ConfigMap + Deployment + ClusterIP Service for user, provider, booking, calendar + API Gateway |
| 5 | `05-phase-observability.md` | Phase D | Actuator config for 4 services + 4 Grafana dashboard JSONs + update dashboards ConfigMap |

## Git Workflow

- **Branch from latest `main`** before starting. Use branch name: `feat/M3/infra/s10-s11-complete/<STUDENT-ID>`
- **Commit after each phase** with the commit message specified at the bottom of each phase file.
- **Never squash-merge.** Use regular merge commits per project convention.
- **Push after all phases are done**, then open a PR to `main`.

## Completion Checklist

When done, verify every item from `docs/booking-m3.md` §10 "K8s Deliverables" (line ~1766) and §11 "Observability Deliverables" (line ~2118):

### Section 10 Deliverables
- [ ] `k8s/namespaces/namespace.yaml` — namespace `booking`
- [ ] `k8s/namespaces/monitoring-namespace.yaml` — namespace `monitoring`
- [ ] `k8s/secrets/jwt-secret.yaml` — shared JWT secret
- [ ] 5 PostgreSQL secrets (one per service) — ✅ already exist
- [ ] 5 PostgreSQL StatefulSets + PVCs — ✅ already exist
- [ ] 5 PostgreSQL headless Services — ✅ already exist
- [ ] RabbitMQ StatefulSet + PVC + Service — ✅ already exist
- [ ] Elasticsearch StatefulSet + PVC + Service — ✅ already exist
- [ ] MongoDB StatefulSet + PVC + Service
- [ ] Redis StatefulSet + PVC + Service
- [ ] Neo4j StatefulSet + PVC + Service
- [ ] Cassandra StatefulSet + PVC + Service
- [ ] 5 Spring Boot Deployments with readiness/liveness probes
- [ ] 5 ClusterIP Services for Spring Boot services
- [ ] 6 ConfigMaps (5 services + gateway)
- [ ] API Gateway Deployment + NodePort Service (port 30080)

### Section 11 Deliverables
- [ ] `logback-spring.xml` in all 5 services — ✅ already exist
- [ ] `management.endpoints.web.exposure.include: prometheus,health,info` in all 5 services
- [ ] 5 Grafana dashboard JSONs (≥3 LogQL + ≥3 PromQL each)
- [ ] `k8s/namespaces/monitoring-namespace.yaml`
- [ ] `k8s/monitoring/loki/` — ConfigMap + PVC + StatefulSet + Service
- [ ] `k8s/monitoring/prometheus/` — ConfigMap + PVC + Deployment + Service
- [ ] `k8s/monitoring/grafana/` — datasources ConfigMap + dashboards ConfigMap + PVC + Deployment + NodePort Service (30030)

## Important Notes

- The **Prometheus ConfigMap** already exists at `k8s/monitoring/prometheus/prometheus-configmap.yaml` but has `namespace: booking`. It MUST be changed to `namespace: monitoring` (Phase B handles this).
- The **Grafana dashboards ConfigMap** already exists at `k8s/monitoring/grafana/grafana-dashboards-configmap.yaml` (invoice only). Phase D updates it to include all 5 dashboards.
- All services read env vars with `${VAR:default}` syntax in `application.yml`. The ConfigMaps must use the correct env var names that match. Check each service's `application.yml` for the exact names.
- The `JWT_SECRET` value used across the project is: `fJ8z2vK6yQ3pX9mR4wT5sN7bE1uI0aH8oL2cD6gV4eK3=`
