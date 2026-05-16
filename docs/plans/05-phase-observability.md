# Phase D — Observability: Actuator Config + 4 Grafana Dashboard JSONs

> Execute AFTER Phase C. This phase adds actuator prometheus config to 4 services and creates their Grafana dashboards. Invoice-service is already done — use `k8s/monitoring/grafana/dashboards/invoice-dashboard.json` as the template.

---

## D1. Add Actuator Config to 4 Services

For each of these 4 services, **append** the following block to the end of the service's `application.yml`. Check that the file does NOT already have a `management:` block before adding.

### Files to edit:
- `user-service/src/main/resources/application.yml`
- `provider-service/src/main/resources/application.yml`
- `booking-service/src/main/resources/application.yml`
- `calendar-service/src/main/resources/application.yml`

### Block to append (identical for all 4):
```yaml

management:
  endpoints:
    web:
      exposure:
        include: "prometheus,health,info"
  metrics:
    distribution:
      percentiles-histogram:
        http.server.requests: true
```

> **IMPORTANT:** This block must be at the top-level of the YAML (same indent level as `spring:` and `server:`). Do not nest it under `spring:`.

---

## D2. User Service Dashboard

### File: `k8s/monitoring/grafana/dashboards/user-dashboard.json`
```json
{
  "title": "User Service Dashboard",
  "uid": "user-service",
  "schemaVersion": 38,
  "version": 1,
  "refresh": "30s",
  "panels": [
    {
      "id": 1,
      "type": "logs",
      "title": "Error Rate (LogQL)",
      "datasource": { "type": "loki", "uid": "loki" },
      "gridPos": { "h": 6, "w": 12, "x": 0, "y": 0 },
      "targets": [
        {
          "expr": "sum(rate({app=\"user-service\"} |= \"ERROR\" [5m]))",
          "refId": "A"
        }
      ]
    },
    {
      "id": 2,
      "type": "logs",
      "title": "Feign Call Outcomes (LogQL)",
      "datasource": { "type": "loki", "uid": "loki" },
      "gridPos": { "h": 6, "w": 12, "x": 12, "y": 0 },
      "targets": [
        {
          "expr": "{app=\"user-service\"} |~ \"Feign (GET|POST)\"",
          "refId": "A"
        }
      ]
    },
    {
      "id": 3,
      "type": "logs",
      "title": "RabbitMQ Event Audit (LogQL)",
      "datasource": { "type": "loki", "uid": "loki" },
      "gridPos": { "h": 6, "w": 24, "x": 0, "y": 6 },
      "targets": [
        {
          "expr": "{app=\"user-service\"} |~ \"(Published|Consuming|Processed)\"",
          "refId": "A"
        }
      ]
    },
    {
      "id": 4,
      "type": "timeseries",
      "title": "HTTP Request Rate (PromQL)",
      "datasource": { "type": "prometheus", "uid": "prometheus" },
      "gridPos": { "h": 6, "w": 12, "x": 0, "y": 12 },
      "targets": [
        {
          "expr": "sum(rate(http_server_requests_seconds_count{job=\"user-service\"}[5m])) by (uri, status)",
          "legendFormat": "{{uri}} {{status}}",
          "refId": "A"
        }
      ]
    },
    {
      "id": 5,
      "type": "timeseries",
      "title": "GET /api/users/* Latency P50/P95/P99 (PromQL)",
      "datasource": { "type": "prometheus", "uid": "prometheus" },
      "gridPos": { "h": 6, "w": 12, "x": 12, "y": 12 },
      "targets": [
        {
          "expr": "histogram_quantile(0.50, sum(rate(http_server_requests_seconds_bucket{job=\"user-service\",uri=~\"/api/users/.*\"}[5m])) by (le))",
          "legendFormat": "P50",
          "refId": "A"
        },
        {
          "expr": "histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket{job=\"user-service\",uri=~\"/api/users/.*\"}[5m])) by (le))",
          "legendFormat": "P95",
          "refId": "B"
        },
        {
          "expr": "histogram_quantile(0.99, sum(rate(http_server_requests_seconds_bucket{job=\"user-service\",uri=~\"/api/users/.*\"}[5m])) by (le))",
          "legendFormat": "P99",
          "refId": "C"
        }
      ]
    },
    {
      "id": 6,
      "type": "timeseries",
      "title": "HikariCP Connection Pool (PromQL)",
      "datasource": { "type": "prometheus", "uid": "prometheus" },
      "gridPos": { "h": 6, "w": 24, "x": 0, "y": 18 },
      "targets": [
        {
          "expr": "hikaricp_connections_active{application=\"user-service\"}",
          "legendFormat": "Active",
          "refId": "A"
        },
        {
          "expr": "hikaricp_connections_pending{application=\"user-service\"}",
          "legendFormat": "Pending",
          "refId": "B"
        },
        {
          "expr": "hikaricp_connections{application=\"user-service\"}",
          "legendFormat": "Total",
          "refId": "C"
        }
      ]
    }
  ]
}
```

---

## D3. Provider Service Dashboard

### File: `k8s/monitoring/grafana/dashboards/provider-dashboard.json`
```json
{
  "title": "Provider Service Dashboard",
  "uid": "provider-service",
  "schemaVersion": 38,
  "version": 1,
  "refresh": "30s",
  "panels": [
    {
      "id": 1,
      "type": "logs",
      "title": "Error Rate (LogQL)",
      "datasource": { "type": "loki", "uid": "loki" },
      "gridPos": { "h": 6, "w": 12, "x": 0, "y": 0 },
      "targets": [
        {
          "expr": "sum(rate({app=\"provider-service\"} |= \"ERROR\" [5m]))",
          "refId": "A"
        }
      ]
    },
    {
      "id": 2,
      "type": "logs",
      "title": "Feign Call Outcomes (LogQL)",
      "datasource": { "type": "loki", "uid": "loki" },
      "gridPos": { "h": 6, "w": 12, "x": 12, "y": 0 },
      "targets": [
        {
          "expr": "{app=\"provider-service\"} |~ \"Feign (GET|POST)\"",
          "refId": "A"
        }
      ]
    },
    {
      "id": 3,
      "type": "logs",
      "title": "RabbitMQ Event Audit (LogQL)",
      "datasource": { "type": "loki", "uid": "loki" },
      "gridPos": { "h": 6, "w": 24, "x": 0, "y": 6 },
      "targets": [
        {
          "expr": "{app=\"provider-service\"} |~ \"(booking\\\\.(placed|cancelled|completed))\"",
          "refId": "A"
        }
      ]
    },
    {
      "id": 4,
      "type": "timeseries",
      "title": "HTTP Request Rate (PromQL)",
      "datasource": { "type": "prometheus", "uid": "prometheus" },
      "gridPos": { "h": 6, "w": 12, "x": 0, "y": 12 },
      "targets": [
        {
          "expr": "sum(rate(http_server_requests_seconds_count{job=\"provider-service\"}[5m])) by (uri, status)",
          "legendFormat": "{{uri}} {{status}}",
          "refId": "A"
        }
      ]
    },
    {
      "id": 5,
      "type": "timeseries",
      "title": "GET /api/providers/* Latency P50/P95/P99 (PromQL)",
      "datasource": { "type": "prometheus", "uid": "prometheus" },
      "gridPos": { "h": 6, "w": 12, "x": 12, "y": 12 },
      "targets": [
        {
          "expr": "histogram_quantile(0.50, sum(rate(http_server_requests_seconds_bucket{job=\"provider-service\",uri=~\"/api/providers/.*\"}[5m])) by (le))",
          "legendFormat": "P50",
          "refId": "A"
        },
        {
          "expr": "histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket{job=\"provider-service\",uri=~\"/api/providers/.*\"}[5m])) by (le))",
          "legendFormat": "P95",
          "refId": "B"
        },
        {
          "expr": "histogram_quantile(0.99, sum(rate(http_server_requests_seconds_bucket{job=\"provider-service\",uri=~\"/api/providers/.*\"}[5m])) by (le))",
          "legendFormat": "P99",
          "refId": "C"
        }
      ]
    },
    {
      "id": 6,
      "type": "timeseries",
      "title": "HikariCP Connection Pool (PromQL)",
      "datasource": { "type": "prometheus", "uid": "prometheus" },
      "gridPos": { "h": 6, "w": 24, "x": 0, "y": 18 },
      "targets": [
        {
          "expr": "hikaricp_connections_active{application=\"provider-service\"}",
          "legendFormat": "Active",
          "refId": "A"
        },
        {
          "expr": "hikaricp_connections_pending{application=\"provider-service\"}",
          "legendFormat": "Pending",
          "refId": "B"
        },
        {
          "expr": "hikaricp_connections{application=\"provider-service\"}",
          "legendFormat": "Total",
          "refId": "C"
        }
      ]
    }
  ]
}
```

---

## D4. Booking Service Dashboard

### File: `k8s/monitoring/grafana/dashboards/booking-dashboard.json`
```json
{
  "title": "Booking Service Dashboard",
  "uid": "booking-service",
  "schemaVersion": 38,
  "version": 1,
  "refresh": "30s",
  "panels": [
    {
      "id": 1,
      "type": "logs",
      "title": "Error Rate (LogQL)",
      "datasource": { "type": "loki", "uid": "loki" },
      "gridPos": { "h": 6, "w": 12, "x": 0, "y": 0 },
      "targets": [
        {
          "expr": "sum(rate({app=\"booking-service\"} |= \"ERROR\" [5m]))",
          "refId": "A"
        }
      ]
    },
    {
      "id": 2,
      "type": "logs",
      "title": "Saga State Transitions (LogQL)",
      "datasource": { "type": "loki", "uid": "loki" },
      "gridPos": { "h": 6, "w": 12, "x": 12, "y": 0 },
      "targets": [
        {
          "expr": "{app=\"booking-service\"} |= \"transitioning\"",
          "refId": "A"
        }
      ]
    },
    {
      "id": 3,
      "type": "logs",
      "title": "RabbitMQ Event Audit (LogQL)",
      "datasource": { "type": "loki", "uid": "loki" },
      "gridPos": { "h": 6, "w": 24, "x": 0, "y": 6 },
      "targets": [
        {
          "expr": "{app=\"booking-service\"} |~ \"(Published|Consuming|Processed)\"",
          "refId": "A"
        }
      ]
    },
    {
      "id": 4,
      "type": "timeseries",
      "title": "HTTP Request Rate (PromQL)",
      "datasource": { "type": "prometheus", "uid": "prometheus" },
      "gridPos": { "h": 6, "w": 12, "x": 0, "y": 12 },
      "targets": [
        {
          "expr": "sum(rate(http_server_requests_seconds_count{job=\"booking-service\"}[5m])) by (uri, status)",
          "legendFormat": "{{uri}} {{status}}",
          "refId": "A"
        }
      ]
    },
    {
      "id": 5,
      "type": "timeseries",
      "title": "PUT /api/bookings/*/complete Latency P50/P95/P99 (PromQL)",
      "datasource": { "type": "prometheus", "uid": "prometheus" },
      "gridPos": { "h": 6, "w": 12, "x": 12, "y": 12 },
      "targets": [
        {
          "expr": "histogram_quantile(0.50, sum(rate(http_server_requests_seconds_bucket{job=\"booking-service\",uri=~\"/api/bookings/.*/complete\"}[5m])) by (le))",
          "legendFormat": "P50",
          "refId": "A"
        },
        {
          "expr": "histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket{job=\"booking-service\",uri=~\"/api/bookings/.*/complete\"}[5m])) by (le))",
          "legendFormat": "P95",
          "refId": "B"
        },
        {
          "expr": "histogram_quantile(0.99, sum(rate(http_server_requests_seconds_bucket{job=\"booking-service\",uri=~\"/api/bookings/.*/complete\"}[5m])) by (le))",
          "legendFormat": "P99",
          "refId": "C"
        }
      ]
    },
    {
      "id": 6,
      "type": "timeseries",
      "title": "HikariCP Connection Pool (PromQL)",
      "datasource": { "type": "prometheus", "uid": "prometheus" },
      "gridPos": { "h": 6, "w": 24, "x": 0, "y": 18 },
      "targets": [
        {
          "expr": "hikaricp_connections_active{application=\"booking-service\"}",
          "legendFormat": "Active",
          "refId": "A"
        },
        {
          "expr": "hikaricp_connections_pending{application=\"booking-service\"}",
          "legendFormat": "Pending",
          "refId": "B"
        },
        {
          "expr": "hikaricp_connections{application=\"booking-service\"}",
          "legendFormat": "Total",
          "refId": "C"
        }
      ]
    }
  ]
}
```

---

## D5. Calendar Service Dashboard

### File: `k8s/monitoring/grafana/dashboards/calendar-dashboard.json`
```json
{
  "title": "Calendar Service Dashboard",
  "uid": "calendar-service",
  "schemaVersion": 38,
  "version": 1,
  "refresh": "30s",
  "panels": [
    {
      "id": 1,
      "type": "logs",
      "title": "Error Rate (LogQL)",
      "datasource": { "type": "loki", "uid": "loki" },
      "gridPos": { "h": 6, "w": 12, "x": 0, "y": 0 },
      "targets": [
        {
          "expr": "sum(rate({app=\"calendar-service\"} |= \"ERROR\" [5m]))",
          "refId": "A"
        }
      ]
    },
    {
      "id": 2,
      "type": "logs",
      "title": "Feign Call Outcomes (LogQL)",
      "datasource": { "type": "loki", "uid": "loki" },
      "gridPos": { "h": 6, "w": 12, "x": 12, "y": 0 },
      "targets": [
        {
          "expr": "{app=\"calendar-service\"} |~ \"Feign (GET|POST)\"",
          "refId": "A"
        }
      ]
    },
    {
      "id": 3,
      "type": "logs",
      "title": "RabbitMQ Event Audit (LogQL)",
      "datasource": { "type": "loki", "uid": "loki" },
      "gridPos": { "h": 6, "w": 24, "x": 0, "y": 6 },
      "targets": [
        {
          "expr": "{app=\"calendar-service\"} |~ \"(booking\\\\.(completed|cancelled))\"",
          "refId": "A"
        }
      ]
    },
    {
      "id": 4,
      "type": "timeseries",
      "title": "HTTP Request Rate (PromQL)",
      "datasource": { "type": "prometheus", "uid": "prometheus" },
      "gridPos": { "h": 6, "w": 12, "x": 0, "y": 12 },
      "targets": [
        {
          "expr": "sum(rate(http_server_requests_seconds_count{job=\"calendar-service\"}[5m])) by (uri, status)",
          "legendFormat": "{{uri}} {{status}}",
          "refId": "A"
        }
      ]
    },
    {
      "id": 5,
      "type": "timeseries",
      "title": "GET /api/timeslots/* Latency P50/P95/P99 (PromQL)",
      "datasource": { "type": "prometheus", "uid": "prometheus" },
      "gridPos": { "h": 6, "w": 12, "x": 12, "y": 12 },
      "targets": [
        {
          "expr": "histogram_quantile(0.50, sum(rate(http_server_requests_seconds_bucket{job=\"calendar-service\",uri=~\"/api/timeslots/.*\"}[5m])) by (le))",
          "legendFormat": "P50",
          "refId": "A"
        },
        {
          "expr": "histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket{job=\"calendar-service\",uri=~\"/api/timeslots/.*\"}[5m])) by (le))",
          "legendFormat": "P95",
          "refId": "B"
        },
        {
          "expr": "histogram_quantile(0.99, sum(rate(http_server_requests_seconds_bucket{job=\"calendar-service\",uri=~\"/api/timeslots/.*\"}[5m])) by (le))",
          "legendFormat": "P99",
          "refId": "C"
        }
      ]
    },
    {
      "id": 6,
      "type": "timeseries",
      "title": "HikariCP Connection Pool (PromQL)",
      "datasource": { "type": "prometheus", "uid": "prometheus" },
      "gridPos": { "h": 6, "w": 24, "x": 0, "y": 18 },
      "targets": [
        {
          "expr": "hikaricp_connections_active{application=\"calendar-service\"}",
          "legendFormat": "Active",
          "refId": "A"
        },
        {
          "expr": "hikaricp_connections_pending{application=\"calendar-service\"}",
          "legendFormat": "Pending",
          "refId": "B"
        },
        {
          "expr": "hikaricp_connections{application=\"calendar-service\"}",
          "legendFormat": "Total",
          "refId": "C"
        }
      ]
    }
  ]
}
```

---

## D6. Update Grafana Dashboards ConfigMap

The existing `k8s/monitoring/grafana/grafana-dashboards-configmap.yaml` only contains the invoice dashboard. **Replace the entire file** with a new ConfigMap named `grafana-dashboards` in the `monitoring` namespace that references all 5 dashboard JSON files. Since the dashboards are loaded from a mounted directory, the simplest approach is to create a new ConfigMap:

### File: `k8s/monitoring/grafana/grafana-dashboards-configmap.yaml` — REPLACE ENTIRE FILE:
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: grafana-dashboards
  namespace: monitoring
data:
  user-dashboard.json: |
    <PASTE FULL CONTENT OF k8s/monitoring/grafana/dashboards/user-dashboard.json HERE>
  provider-dashboard.json: |
    <PASTE FULL CONTENT OF k8s/monitoring/grafana/dashboards/provider-dashboard.json HERE>
  booking-dashboard.json: |
    <PASTE FULL CONTENT OF k8s/monitoring/grafana/dashboards/booking-dashboard.json HERE>
  calendar-dashboard.json: |
    <PASTE FULL CONTENT OF k8s/monitoring/grafana/dashboards/calendar-dashboard.json HERE>
  invoice-dashboard.json: |
    <PASTE FULL CONTENT OF k8s/monitoring/grafana/dashboards/invoice-dashboard.json HERE>
```

> **IMPORTANT:** Each JSON block must be indented by 4 spaces (YAML block scalar). Read the content of each dashboard JSON file from `k8s/monitoring/grafana/dashboards/` and embed it verbatim with proper indentation. The invoice dashboard JSON already exists at `k8s/monitoring/grafana/dashboards/invoice-dashboard.json` — copy it in too.

---

## Phase D Commit

```
feat(infra): add actuator config and Grafana dashboards for user, provider, booking, calendar services (<STUDENT-ID>)
```

**Files created/edited this phase:**
- EDITED: `user-service/src/main/resources/application.yml` (appended management block)
- EDITED: `provider-service/src/main/resources/application.yml` (appended management block)
- EDITED: `booking-service/src/main/resources/application.yml` (appended management block)
- EDITED: `calendar-service/src/main/resources/application.yml` (appended management block)
- Created: `k8s/monitoring/grafana/dashboards/user-dashboard.json`
- Created: `k8s/monitoring/grafana/dashboards/provider-dashboard.json`
- Created: `k8s/monitoring/grafana/dashboards/booking-dashboard.json`
- Created: `k8s/monitoring/grafana/dashboards/calendar-dashboard.json`
- REPLACED: `k8s/monitoring/grafana/grafana-dashboards-configmap.yaml` (all 5 dashboards, monitoring namespace)
