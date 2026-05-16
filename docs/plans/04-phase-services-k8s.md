# Phase C — Service K8s Manifests: ConfigMaps, Deployments, Services, API Gateway

> Execute AFTER Phase B. This phase creates K8s deployment manifests for user, provider, booking, calendar services and the API gateway. Invoice-service already has all of these — use its files as reference.

---

## C1. User Service

### File: `k8s/configmaps/user-service-configmap.yaml`
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: user-service-config
  namespace: booking
data:
  POSTGRES_HOST: user-postgres
  POSTGRES_PORT: "5432"
  POSTGRES_DB: bookingdb-users
  POSTGRES_USER: user
  REDIS_HOST: redis
  REDIS_PORT: "6379"
  MONGO_HOST: mongo
  MONGO_PORT: "27017"
  MONGO_DATABASE: bookingmongo
  MONGO_USERNAME: root
  RABBITMQ_HOST: rabbitmq
  RABBITMQ_PORT: "5672"
  RABBITMQ_USER: guest
  LOKI_HOST: loki.monitoring.svc.cluster.local
  FEIGN_USER_SERVICE_URL: user-service:8080
  FEIGN_PROVIDER_SERVICE_URL: provider-service:8080
  FEIGN_BOOKING_SERVICE_URL: booking-service:8080
  FEIGN_CALENDAR_SERVICE_URL: calendar-service:8080
  FEIGN_INVOICE_SERVICE_URL: invoice-service:8080
  MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE: prometheus,health,info
  MANAGEMENT_METRICS_DISTRIBUTION_PERCENTILES_HISTOGRAM_HTTP_SERVER_REQUESTS: "true"
```

### File: `k8s/deployments/user-service-deployment.yaml`
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: user-service
  namespace: booking
  labels:
    app: user-service
spec:
  replicas: 2
  selector:
    matchLabels:
      app: user-service
  template:
    metadata:
      labels:
        app: user-service
    spec:
      containers:
        - name: user-service
          image: team28/user-service:latest
          ports:
            - containerPort: 8080
          envFrom:
            - configMapRef:
                name: user-service-config
            - secretRef:
                name: user-service-secret
          readinessProbe:
            httpGet:
              path: /actuator/health
              port: 8080
            initialDelaySeconds: 30
            periodSeconds: 10
            failureThreshold: 3
          livenessProbe:
            httpGet:
              path: /actuator/health
              port: 8080
            initialDelaySeconds: 60
            periodSeconds: 20
            failureThreshold: 5
          resources:
            requests:
              cpu: "250m"
              memory: "512Mi"
            limits:
              cpu: "1"
              memory: "1Gi"
```

### File: `k8s/secrets/user-service-secret.yaml`
```yaml
apiVersion: v1
kind: Secret
metadata:
  name: user-service-secret
  namespace: booking
type: Opaque
stringData:
  POSTGRES_PASSWORD: password
  REDIS_PASSWORD: redispass
  MONGO_PASSWORD: rootpass
  RABBITMQ_PASS: guest
  JWT_SECRET: fJ8z2vK6yQ3pX9mR4wT5sN7bE1uI0aH8oL2cD6gV4eK3=
```

### File: `k8s/services/user-service-svc.yaml`
```yaml
apiVersion: v1
kind: Service
metadata:
  name: user-service
  namespace: booking
  labels:
    app: user-service
spec:
  selector:
    app: user-service
  ports:
    - name: http
      port: 8080
      targetPort: 8080
  type: ClusterIP
```

---

## C2. Provider Service

### File: `k8s/configmaps/provider-service-configmap.yaml`
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: provider-service-config
  namespace: booking
data:
  POSTGRES_HOST: provider-postgres
  POSTGRES_PORT: "5432"
  POSTGRES_DB: bookingdb-providers
  POSTGRES_USER: user
  REDIS_HOST: redis
  REDIS_PORT: "6379"
  MONGO_HOST: mongo
  MONGO_PORT: "27017"
  MONGO_DATABASE: bookingmongo
  MONGO_USERNAME: root
  RABBITMQ_HOST: rabbitmq
  RABBITMQ_PORT: "5672"
  RABBITMQ_USER: guest
  LOKI_HOST: loki.monitoring.svc.cluster.local
  ELASTICSEARCH_URIS: http://elasticsearch:9200
  FEIGN_USER_SERVICE_URL: user-service:8080
  FEIGN_PROVIDER_SERVICE_URL: provider-service:8080
  FEIGN_BOOKING_SERVICE_URL: booking-service:8080
  FEIGN_CALENDAR_SERVICE_URL: calendar-service:8080
  FEIGN_INVOICE_SERVICE_URL: invoice-service:8080
  MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE: prometheus,health,info
  MANAGEMENT_METRICS_DISTRIBUTION_PERCENTILES_HISTOGRAM_HTTP_SERVER_REQUESTS: "true"
```

> **Note:** `ELASTICSEARCH_URIS` is unique to provider-service (used for search).

### File: `k8s/deployments/provider-service-deployment.yaml`
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: provider-service
  namespace: booking
  labels:
    app: provider-service
spec:
  replicas: 2
  selector:
    matchLabels:
      app: provider-service
  template:
    metadata:
      labels:
        app: provider-service
    spec:
      containers:
        - name: provider-service
          image: team28/provider-service:latest
          ports:
            - containerPort: 8080
          envFrom:
            - configMapRef:
                name: provider-service-config
            - secretRef:
                name: provider-service-secret
          readinessProbe:
            httpGet:
              path: /actuator/health
              port: 8080
            initialDelaySeconds: 30
            periodSeconds: 10
            failureThreshold: 3
          livenessProbe:
            httpGet:
              path: /actuator/health
              port: 8080
            initialDelaySeconds: 60
            periodSeconds: 20
            failureThreshold: 5
          resources:
            requests:
              cpu: "250m"
              memory: "512Mi"
            limits:
              cpu: "1"
              memory: "1Gi"
```

### File: `k8s/secrets/provider-service-secret.yaml`
```yaml
apiVersion: v1
kind: Secret
metadata:
  name: provider-service-secret
  namespace: booking
type: Opaque
stringData:
  POSTGRES_PASSWORD: password
  REDIS_PASSWORD: redispass
  MONGO_PASSWORD: rootpass
  RABBITMQ_PASS: guest
  JWT_SECRET: fJ8z2vK6yQ3pX9mR4wT5sN7bE1uI0aH8oL2cD6gV4eK3=
```

### File: `k8s/services/provider-service-svc.yaml`
```yaml
apiVersion: v1
kind: Service
metadata:
  name: provider-service
  namespace: booking
  labels:
    app: provider-service
spec:
  selector:
    app: provider-service
  ports:
    - name: http
      port: 8080
      targetPort: 8080
  type: ClusterIP
```

---

## C3. Booking Service

### File: `k8s/configmaps/booking-service-configmap.yaml`
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: booking-service-config
  namespace: booking
data:
  POSTGRES_HOST: booking-postgres
  POSTGRES_PORT: "5432"
  POSTGRES_DB: bookingdb-bookings
  POSTGRES_USER: user
  REDIS_HOST: redis
  REDIS_PORT: "6379"
  MONGO_HOST: mongo
  MONGO_PORT: "27017"
  MONGO_DATABASE: bookingmongo
  MONGO_USERNAME: root
  RABBITMQ_HOST: rabbitmq
  RABBITMQ_PORT: "5672"
  RABBITMQ_USER: guest
  LOKI_HOST: loki.monitoring.svc.cluster.local
  NEO4J_URI: bolt://neo4j:7687
  NEO4J_USERNAME: neo4j
  NEO4J_PASSWORD: neo4jpass
  FEIGN_USER_SERVICE_URL: user-service:8080
  FEIGN_PROVIDER_SERVICE_URL: provider-service:8080
  FEIGN_BOOKING_SERVICE_URL: booking-service:8080
  FEIGN_CALENDAR_SERVICE_URL: calendar-service:8080
  FEIGN_INVOICE_SERVICE_URL: invoice-service:8080
  MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE: prometheus,health,info
  MANAGEMENT_METRICS_DISTRIBUTION_PERCENTILES_HISTOGRAM_HTTP_SERVER_REQUESTS: "true"
```

> **Note:** `NEO4J_URI`, `NEO4J_USERNAME`, `NEO4J_PASSWORD` are unique to booking-service (used for recommendations).

### File: `k8s/deployments/booking-service-deployment.yaml`
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: booking-service
  namespace: booking
  labels:
    app: booking-service
spec:
  replicas: 2
  selector:
    matchLabels:
      app: booking-service
  template:
    metadata:
      labels:
        app: booking-service
    spec:
      containers:
        - name: booking-service
          image: team28/booking-service:latest
          ports:
            - containerPort: 8080
          envFrom:
            - configMapRef:
                name: booking-service-config
            - secretRef:
                name: booking-service-secret
          readinessProbe:
            httpGet:
              path: /actuator/health
              port: 8080
            initialDelaySeconds: 30
            periodSeconds: 10
            failureThreshold: 3
          livenessProbe:
            httpGet:
              path: /actuator/health
              port: 8080
            initialDelaySeconds: 60
            periodSeconds: 20
            failureThreshold: 5
          resources:
            requests:
              cpu: "250m"
              memory: "512Mi"
            limits:
              cpu: "1"
              memory: "1Gi"
```

### File: `k8s/secrets/booking-service-secret.yaml`
```yaml
apiVersion: v1
kind: Secret
metadata:
  name: booking-service-secret
  namespace: booking
type: Opaque
stringData:
  POSTGRES_PASSWORD: password
  REDIS_PASSWORD: redispass
  MONGO_PASSWORD: rootpass
  RABBITMQ_PASS: guest
  JWT_SECRET: fJ8z2vK6yQ3pX9mR4wT5sN7bE1uI0aH8oL2cD6gV4eK3=
```

### File: `k8s/services/booking-service-svc.yaml`
```yaml
apiVersion: v1
kind: Service
metadata:
  name: booking-service
  namespace: booking
  labels:
    app: booking-service
spec:
  selector:
    app: booking-service
  ports:
    - name: http
      port: 8080
      targetPort: 8080
  type: ClusterIP
```

---

## C4. Calendar Service

### File: `k8s/configmaps/calendar-service-configmap.yaml`
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: calendar-service-config
  namespace: booking
data:
  POSTGRES_HOST: calendar-postgres
  POSTGRES_PORT: "5432"
  POSTGRES_DB: bookingdb-calendar
  POSTGRES_USER: user
  REDIS_HOST: redis
  REDIS_PORT: "6379"
  MONGO_HOST: mongo
  MONGO_PORT: "27017"
  MONGO_DATABASE: bookingmongo
  MONGO_USERNAME: root
  RABBITMQ_HOST: rabbitmq
  RABBITMQ_PORT: "5672"
  RABBITMQ_USER: guest
  LOKI_HOST: loki.monitoring.svc.cluster.local
  CASSANDRA_CONTACT_POINTS: cassandra
  CASSANDRA_PORT: "9042"
  FEIGN_USER_SERVICE_URL: user-service:8080
  FEIGN_PROVIDER_SERVICE_URL: provider-service:8080
  FEIGN_BOOKING_SERVICE_URL: booking-service:8080
  FEIGN_CALENDAR_SERVICE_URL: calendar-service:8080
  FEIGN_INVOICE_SERVICE_URL: invoice-service:8080
  MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE: prometheus,health,info
  MANAGEMENT_METRICS_DISTRIBUTION_PERCENTILES_HISTOGRAM_HTTP_SERVER_REQUESTS: "true"
```

> **Note:** `CASSANDRA_CONTACT_POINTS`, `CASSANDRA_PORT` are unique to calendar-service.

### File: `k8s/deployments/calendar-service-deployment.yaml`
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: calendar-service
  namespace: booking
  labels:
    app: calendar-service
spec:
  replicas: 2
  selector:
    matchLabels:
      app: calendar-service
  template:
    metadata:
      labels:
        app: calendar-service
    spec:
      containers:
        - name: calendar-service
          image: team28/calendar-service:latest
          ports:
            - containerPort: 8080
          envFrom:
            - configMapRef:
                name: calendar-service-config
            - secretRef:
                name: calendar-service-secret
          readinessProbe:
            httpGet:
              path: /actuator/health
              port: 8080
            initialDelaySeconds: 30
            periodSeconds: 10
            failureThreshold: 3
          livenessProbe:
            httpGet:
              path: /actuator/health
              port: 8080
            initialDelaySeconds: 60
            periodSeconds: 20
            failureThreshold: 5
          resources:
            requests:
              cpu: "250m"
              memory: "512Mi"
            limits:
              cpu: "1"
              memory: "1Gi"
```

### File: `k8s/secrets/calendar-service-secret.yaml`
```yaml
apiVersion: v1
kind: Secret
metadata:
  name: calendar-service-secret
  namespace: booking
type: Opaque
stringData:
  POSTGRES_PASSWORD: password
  REDIS_PASSWORD: redispass
  MONGO_PASSWORD: rootpass
  RABBITMQ_PASS: guest
  JWT_SECRET: fJ8z2vK6yQ3pX9mR4wT5sN7bE1uI0aH8oL2cD6gV4eK3=
```

### File: `k8s/services/calendar-service-svc.yaml`
```yaml
apiVersion: v1
kind: Service
metadata:
  name: calendar-service
  namespace: booking
  labels:
    app: calendar-service
spec:
  selector:
    app: calendar-service
  ports:
    - name: http
      port: 8080
      targetPort: 8080
  type: ClusterIP
```

---

## C5. API Gateway

### File: `k8s/configmaps/gateway-configmap.yaml`
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: gateway-config
  namespace: booking
data:
  USER_SERVICE_URL: http://user-service:8080
  PROVIDER_SERVICE_URL: http://provider-service:8080
  BOOKING_SERVICE_URL: http://booking-service:8080
  CALENDAR_SERVICE_URL: http://calendar-service:8080
  INVOICE_SERVICE_URL: http://invoice-service:8080
```

> **Note:** The gateway reads routes from its own `application.yml` using `${USER_SERVICE_URL:...}` etc. The ConfigMap just overrides the defaults for K8s.

### File: `k8s/api-gateway/gateway-deployment.yaml`
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: api-gateway
  namespace: booking
  labels:
    app: api-gateway
spec:
  replicas: 1
  selector:
    matchLabels:
      app: api-gateway
  template:
    metadata:
      labels:
        app: api-gateway
    spec:
      containers:
        - name: api-gateway
          image: team28/api-gateway:latest
          ports:
            - containerPort: 8080
          envFrom:
            - configMapRef:
                name: gateway-config
          env:
            - name: JWT_SECRET
              valueFrom:
                secretKeyRef:
                  name: jwt-secret
                  key: jwt-secret
          readinessProbe:
            httpGet:
              path: /actuator/health
              port: 8080
            initialDelaySeconds: 20
            periodSeconds: 10
          livenessProbe:
            httpGet:
              path: /actuator/health
              port: 8080
            initialDelaySeconds: 40
            periodSeconds: 30
```

### File: `k8s/api-gateway/gateway-service.yaml`
```yaml
apiVersion: v1
kind: Service
metadata:
  name: api-gateway
  namespace: booking
spec:
  type: NodePort
  selector:
    app: api-gateway
  ports:
    - port: 8080
      targetPort: 8080
      nodePort: 30080
```

> **This is the ONLY externally-accessible service.** All others are ClusterIP.

---

## Phase C Commit

```
feat(infra): add K8s deployments for user, provider, booking, calendar services and API gateway (<STUDENT-ID>)
```

**Files created this phase (21 files):**
- `k8s/configmaps/{user,provider,booking,calendar}-service-configmap.yaml` (4)
- `k8s/configmaps/gateway-configmap.yaml` (1)
- `k8s/deployments/{user,provider,booking,calendar}-service-deployment.yaml` (4)
- `k8s/secrets/{user,provider,booking,calendar}-service-secret.yaml` (4)
- `k8s/services/{user,provider,booking,calendar}-service-svc.yaml` (4)
- `k8s/api-gateway/gateway-deployment.yaml` (1)
- `k8s/api-gateway/gateway-service.yaml` (1)
