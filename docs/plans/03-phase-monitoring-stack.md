# Phase B — Monitoring Stack: Loki, Prometheus, Grafana

> Execute AFTER Phase A. This phase creates the observability infrastructure in the `monitoring` namespace.

---

## B1. Fix Prometheus ConfigMap Namespace

The existing file `k8s/monitoring/prometheus/prometheus-configmap.yaml` has `namespace: booking`. Per §11.5, Prometheus runs in `monitoring`. **Edit the existing file** — change line 5 from `namespace: booking` to `namespace: monitoring`. The scrape targets already use FQDNs (`*.booking.svc.cluster.local`) so cross-namespace scraping works.

Additionally, the spec in §11.4 requires **separate job_name per service** (not a combined `booking-services` job) so the dashboard `{job="user-service"}` selectors work. Replace the entire `data` section.

### Edit: `k8s/monitoring/prometheus/prometheus-configmap.yaml` — REPLACE ENTIRE FILE with:
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: prometheus-config
  namespace: monitoring
data:
  prometheus.yml: |
    global:
      scrape_interval: 15s
    scrape_configs:
      - job_name: user-service
        metrics_path: /actuator/prometheus
        static_configs:
          - targets: ['user-service.booking.svc.cluster.local:8080']
      - job_name: provider-service
        metrics_path: /actuator/prometheus
        static_configs:
          - targets: ['provider-service.booking.svc.cluster.local:8080']
      - job_name: booking-service
        metrics_path: /actuator/prometheus
        static_configs:
          - targets: ['booking-service.booking.svc.cluster.local:8080']
      - job_name: calendar-service
        metrics_path: /actuator/prometheus
        static_configs:
          - targets: ['calendar-service.booking.svc.cluster.local:8080']
      - job_name: invoice-service
        metrics_path: /actuator/prometheus
        static_configs:
          - targets: ['invoice-service.booking.svc.cluster.local:8080']
      - job_name: api-gateway
        metrics_path: /actuator/prometheus
        static_configs:
          - targets: ['api-gateway.booking.svc.cluster.local:8080']
```

---

## B2. Loki Stack

### File: `k8s/monitoring/loki/loki-configmap.yaml`
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: loki-config
  namespace: monitoring
data:
  local-config.yaml: |
    auth_enabled: false
    server:
      http_listen_port: 3100
    common:
      ring:
        instance_addr: 127.0.0.1
        kvstore:
          store: inmemory
      replication_factor: 1
      path_prefix: /loki
    schema_config:
      configs:
        - from: 2020-05-15
          store: tsdb
          object_store: filesystem
          schema: v13
          index:
            prefix: index_
            period: 24h
    storage_config:
      filesystem:
        directory: /loki/chunks
```

### File: `k8s/monitoring/loki/loki-pvc.yaml`
```yaml
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: loki-pvc
  namespace: monitoring
spec:
  accessModes:
    - ReadWriteOnce
  resources:
    requests:
      storage: 5Gi
```

### File: `k8s/monitoring/loki/loki-statefulset.yaml`
```yaml
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: loki
  namespace: monitoring
spec:
  serviceName: loki
  replicas: 1
  selector:
    matchLabels:
      app: loki
  template:
    metadata:
      labels:
        app: loki
    spec:
      containers:
        - name: loki
          image: grafana/loki:2.9.4
          args:
            - -config.file=/etc/loki/local-config.yaml
          ports:
            - containerPort: 3100
          volumeMounts:
            - name: config
              mountPath: /etc/loki
            - name: storage
              mountPath: /loki
      volumes:
        - name: config
          configMap:
            name: loki-config
        - name: storage
          persistentVolumeClaim:
            claimName: loki-pvc
```

### File: `k8s/monitoring/loki/loki-service.yaml`
```yaml
apiVersion: v1
kind: Service
metadata:
  name: loki
  namespace: monitoring
spec:
  selector:
    app: loki
  ports:
    - port: 3100
      targetPort: 3100
```

---

## B3. Prometheus Deployment & Service

The ConfigMap already exists (edited in B1). Create the remaining files.

### File: `k8s/monitoring/prometheus/prometheus-pvc.yaml`
```yaml
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: prometheus-pvc
  namespace: monitoring
spec:
  accessModes:
    - ReadWriteOnce
  resources:
    requests:
      storage: 5Gi
```

### File: `k8s/monitoring/prometheus/prometheus-deployment.yaml`
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: prometheus
  namespace: monitoring
spec:
  replicas: 1
  selector:
    matchLabels:
      app: prometheus
  template:
    metadata:
      labels:
        app: prometheus
    spec:
      containers:
        - name: prometheus
          image: prom/prometheus:v2.51.2
          args:
            - --config.file=/etc/prometheus/prometheus.yml
            - --storage.tsdb.path=/prometheus
          ports:
            - containerPort: 9090
          volumeMounts:
            - name: config
              mountPath: /etc/prometheus
            - name: storage
              mountPath: /prometheus
          readinessProbe:
            httpGet:
              path: /-/ready
              port: 9090
            initialDelaySeconds: 30
            periodSeconds: 10
      volumes:
        - name: config
          configMap:
            name: prometheus-config
        - name: storage
          persistentVolumeClaim:
            claimName: prometheus-pvc
```

### File: `k8s/monitoring/prometheus/prometheus-service.yaml`
```yaml
apiVersion: v1
kind: Service
metadata:
  name: prometheus
  namespace: monitoring
spec:
  selector:
    app: prometheus
  ports:
    - port: 9090
      targetPort: 9090
```

---

## B4. Grafana Stack

### File: `k8s/monitoring/grafana/grafana-datasources.yaml`
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: grafana-datasources
  namespace: monitoring
data:
  datasources.yaml: |
    apiVersion: 1
    datasources:
      - name: Prometheus
        type: prometheus
        access: proxy
        url: http://prometheus.monitoring.svc.cluster.local:9090
        isDefault: true
      - name: Loki
        type: loki
        access: proxy
        url: http://loki.monitoring.svc.cluster.local:3100
```

### File: `k8s/monitoring/grafana/grafana-pvc.yaml`
```yaml
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: grafana-pvc
  namespace: monitoring
spec:
  accessModes:
    - ReadWriteOnce
  resources:
    requests:
      storage: 1Gi
```

### File: `k8s/monitoring/grafana/grafana-deployment.yaml`
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: grafana
  namespace: monitoring
spec:
  replicas: 1
  selector:
    matchLabels:
      app: grafana
  template:
    metadata:
      labels:
        app: grafana
    spec:
      containers:
        - name: grafana
          image: grafana/grafana:10.4.2
          ports:
            - containerPort: 3000
          env:
            - name: GF_SECURITY_ADMIN_PASSWORD
              value: admin
          volumeMounts:
            - name: datasources
              mountPath: /etc/grafana/provisioning/datasources
            - name: dashboards-provisioning
              mountPath: /etc/grafana/provisioning/dashboards
            - name: dashboards-json
              mountPath: /var/lib/grafana/dashboards
            - name: storage
              mountPath: /var/lib/grafana
          readinessProbe:
            httpGet:
              path: /api/health
              port: 3000
            initialDelaySeconds: 30
            periodSeconds: 10
      volumes:
        - name: datasources
          configMap:
            name: grafana-datasources
        - name: dashboards-provisioning
          configMap:
            name: grafana-dashboard-provisioning
        - name: dashboards-json
          configMap:
            name: grafana-dashboards
        - name: storage
          persistentVolumeClaim:
            claimName: grafana-pvc
```

### File: `k8s/monitoring/grafana/grafana-dashboard-provisioning.yaml`
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: grafana-dashboard-provisioning
  namespace: monitoring
data:
  dashboards.yaml: |
    apiVersion: 1
    providers:
      - name: 'default'
        orgId: 1
        folder: 'Booking'
        type: file
        disableDeletion: false
        updateIntervalSeconds: 10
        options:
          path: /var/lib/grafana/dashboards
```

### File: `k8s/monitoring/grafana/grafana-service.yaml`
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
      nodePort: 30030
```

---

## Phase B Commit

```
feat(infra): add Loki, Prometheus, and Grafana monitoring stack (<STUDENT-ID>)
```

**Files created/edited this phase (12 files):**
- EDITED: `k8s/monitoring/prometheus/prometheus-configmap.yaml` (namespace + separate jobs)
- `k8s/monitoring/loki/loki-configmap.yaml`
- `k8s/monitoring/loki/loki-pvc.yaml`
- `k8s/monitoring/loki/loki-statefulset.yaml`
- `k8s/monitoring/loki/loki-service.yaml`
- `k8s/monitoring/prometheus/prometheus-pvc.yaml`
- `k8s/monitoring/prometheus/prometheus-deployment.yaml`
- `k8s/monitoring/prometheus/prometheus-service.yaml`
- `k8s/monitoring/grafana/grafana-datasources.yaml`
- `k8s/monitoring/grafana/grafana-pvc.yaml`
- `k8s/monitoring/grafana/grafana-deployment.yaml`
- `k8s/monitoring/grafana/grafana-dashboard-provisioning.yaml`
- `k8s/monitoring/grafana/grafana-service.yaml`
