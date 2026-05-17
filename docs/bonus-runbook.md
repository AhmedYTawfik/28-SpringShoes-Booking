# Bonus Runbook

## CI/CD

- Pushes to `feat/*` run `mvn clean verify` and Docker builds for all services.
- Pushes to `main` run the same build and push images to GHCR as `latest` and `${GITHUB_SHA}`.
- Workflow: `.github/workflows/ci.yml`.

## Circuit Breaker

Feign circuit breakers are enabled with `spring.cloud.openfeign.circuitbreaker.enabled=true`.
Each Feign client has a fallback in `contracts/src/main/java/com/team28/booking/contracts/feign`.

Demo:

```bash
kubectl -n booking scale deploy provider-service --replicas=0
for i in {1..6}; do curl -i http://springshoes.local/api/bookings/1/complete; done
kubectl -n booking logs deploy/booking-service | grep -i circuit
kubectl -n booking scale deploy provider-service --replicas=1
```

Repeated provider-service failures open the circuit, fallback responses return `UNAVAILABLE`, then the circuit half-opens and recovers after `wait-duration-in-open-state`.

## Ingress

```bash
minikube addons enable ingress
kubectl apply -f k8s/api-gateway/gateway-service.yaml
kubectl apply -f k8s/api-gateway/gateway-ingress.yaml
echo "$(minikube ip) springshoes.local" | sudo tee -a /etc/hosts
curl http://springshoes.local/api/actuator/health
```

The gateway service is now `ClusterIP`; external traffic enters through `k8s/api-gateway/gateway-ingress.yaml`.

## HPA

```bash
minikube addons enable metrics-server
kubectl apply -f k8s/deployments/booking-service-deployment.yaml
kubectl apply -f k8s/deployments/booking-service-hpa.yaml
kubectl -n booking get hpa booking-service --watch
```

Load example:

```bash
kubectl -n booking run booking-load --rm -it --image=busybox:1.36 --restart=Never -- \
  sh -c 'while true; do wget -q -O- http://api-gateway:8080/api/bookings >/dev/null; done'
```
