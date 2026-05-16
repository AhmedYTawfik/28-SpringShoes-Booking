# Phase A — Shared Infrastructure: Namespaces, JWT Secret, NoSQL K8s Manifests

> Execute AFTER reading `01-gap-analysis.md`. This phase creates the foundational K8s resources that all other phases depend on.

---

## A1. Namespaces

### File: `k8s/namespaces/namespace.yaml`
```yaml
apiVersion: v1
kind: Namespace
metadata:
  name: booking
```

### File: `k8s/namespaces/monitoring-namespace.yaml`
```yaml
apiVersion: v1
kind: Namespace
metadata:
  name: monitoring
```

---

## A2. JWT Secret

### File: `k8s/secrets/jwt-secret.yaml`
```yaml
apiVersion: v1
kind: Secret
metadata:
  name: jwt-secret
  namespace: booking
type: Opaque
stringData:
  jwt-secret: "fJ8z2vK6yQ3pX9mR4wT5sN7bE1uI0aH8oL2cD6gV4eK3="
```

> This value comes from `docker-compose.yaml` → `api-gateway` → `JWT_SECRET` and matches all services' `jwt.secret` default.

---

## A3. MongoDB

### File: `k8s/pvcs/mongo-pvc.yaml`
```yaml
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: mongo-pvc
  namespace: booking
spec:
  accessModes:
    - ReadWriteOnce
  resources:
    requests:
      storage: 2Gi
```

### File: `k8s/statefulsets/mongo-statefulset.yaml`
```yaml
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: mongo
  namespace: booking
spec:
  serviceName: mongo
  replicas: 1
  selector:
    matchLabels:
      app: mongo
  template:
    metadata:
      labels:
        app: mongo
    spec:
      containers:
        - name: mongo
          image: mongo:latest
          ports:
            - containerPort: 27017
          env:
            - name: MONGO_INITDB_ROOT_USERNAME
              value: root
            - name: MONGO_INITDB_ROOT_PASSWORD
              value: rootpass
            - name: MONGO_INITDB_DATABASE
              value: bookingmongo
          volumeMounts:
            - name: mongo-data
              mountPath: /data/db
      volumes:
        - name: mongo-data
          persistentVolumeClaim:
            claimName: mongo-pvc
```

### File: `k8s/services/mongo-svc.yaml`
```yaml
apiVersion: v1
kind: Service
metadata:
  name: mongo
  namespace: booking
spec:
  selector:
    app: mongo
  ports:
    - port: 27017
      targetPort: 27017
```

---

## A4. Redis

### File: `k8s/pvcs/redis-pvc.yaml`
```yaml
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: redis-pvc
  namespace: booking
spec:
  accessModes:
    - ReadWriteOnce
  resources:
    requests:
      storage: 1Gi
```

### File: `k8s/statefulsets/redis-statefulset.yaml`
```yaml
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: redis
  namespace: booking
spec:
  serviceName: redis
  replicas: 1
  selector:
    matchLabels:
      app: redis
  template:
    metadata:
      labels:
        app: redis
    spec:
      containers:
        - name: redis
          image: redis:latest
          command: ["redis-server", "--requirepass", "redispass", "--maxmemory", "256mb", "--maxmemory-policy", "allkeys-lru"]
          ports:
            - containerPort: 6379
          volumeMounts:
            - name: redis-data
              mountPath: /data
      volumes:
        - name: redis-data
          persistentVolumeClaim:
            claimName: redis-pvc
```

### File: `k8s/services/redis-svc.yaml`
```yaml
apiVersion: v1
kind: Service
metadata:
  name: redis
  namespace: booking
spec:
  selector:
    app: redis
  ports:
    - port: 6379
      targetPort: 6379
```

---

## A5. Neo4j

### File: `k8s/pvcs/neo4j-pvc.yaml`
```yaml
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: neo4j-pvc
  namespace: booking
spec:
  accessModes:
    - ReadWriteOnce
  resources:
    requests:
      storage: 2Gi
```

### File: `k8s/statefulsets/neo4j-statefulset.yaml`
```yaml
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: neo4j
  namespace: booking
spec:
  serviceName: neo4j
  replicas: 1
  selector:
    matchLabels:
      app: neo4j
  template:
    metadata:
      labels:
        app: neo4j
    spec:
      containers:
        - name: neo4j
          image: neo4j:latest
          ports:
            - containerPort: 7474
              name: http
            - containerPort: 7687
              name: bolt
          env:
            - name: NEO4J_AUTH
              value: neo4j/neo4jpass
            - name: NEO4J_server_memory_heap_max__size
              value: "512m"
            - name: NEO4J_server_memory_heap_initial__size
              value: "256m"
          volumeMounts:
            - name: neo4j-data
              mountPath: /data
      volumes:
        - name: neo4j-data
          persistentVolumeClaim:
            claimName: neo4j-pvc
```

### File: `k8s/services/neo4j-svc.yaml`
```yaml
apiVersion: v1
kind: Service
metadata:
  name: neo4j
  namespace: booking
spec:
  selector:
    app: neo4j
  ports:
    - name: http
      port: 7474
      targetPort: 7474
    - name: bolt
      port: 7687
      targetPort: 7687
```

---

## A6. Cassandra

### File: `k8s/pvcs/cassandra-pvc.yaml`
```yaml
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: cassandra-pvc
  namespace: booking
spec:
  accessModes:
    - ReadWriteOnce
  resources:
    requests:
      storage: 2Gi
```

### File: `k8s/statefulsets/cassandra-statefulset.yaml`
```yaml
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: cassandra
  namespace: booking
spec:
  serviceName: cassandra
  replicas: 1
  selector:
    matchLabels:
      app: cassandra
  template:
    metadata:
      labels:
        app: cassandra
    spec:
      containers:
        - name: cassandra
          image: cassandra:latest
          ports:
            - containerPort: 9042
          env:
            - name: CASSANDRA_CLUSTER_NAME
              value: bookingcluster
            - name: CASSANDRA_DC
              value: datacenter1
            - name: CASSANDRA_KEYSPACE
              value: bookingks
            - name: MAX_HEAP_SIZE
              value: "512M"
            - name: HEAP_NEWSIZE
              value: "128M"
          volumeMounts:
            - name: cassandra-data
              mountPath: /var/lib/cassandra
      volumes:
        - name: cassandra-data
          persistentVolumeClaim:
            claimName: cassandra-pvc
```

### File: `k8s/services/cassandra-svc.yaml`
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
```

---

## Phase A Commit

```
feat(infra): add namespaces, JWT secret, and NoSQL K8s manifests (<STUDENT-ID>)
```

**Files created this phase (16 files):**
- `k8s/namespaces/namespace.yaml`
- `k8s/namespaces/monitoring-namespace.yaml`
- `k8s/secrets/jwt-secret.yaml`
- `k8s/pvcs/{mongo,redis,neo4j,cassandra}-pvc.yaml` (4)
- `k8s/statefulsets/{mongo,redis,neo4j,cassandra}-statefulset.yaml` (4)
- `k8s/services/{mongo,redis,neo4j,cassandra}-svc.yaml` (4)
