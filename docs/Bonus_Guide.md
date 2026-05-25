# Bonus Features — Complete Study Guide

---

## BONUS 1: Full Testing Suite

### 1A — Unit Tests with @Mock on Feign Clients

**Concept: What is a Unit Test?**
A unit test checks ONE piece of logic in ISOLATION. You don't start the app, no database, no RabbitMQ, no other services. You FAKE all dependencies using Mockito.

**Concept: What is Mockito?**
A Java library that creates fake objects. You tell the fake: "When someone calls method X, return Y." Then you test that YOUR code does the right thing with Y.

**Concept: @Mock vs @MockBean**
- `@Mock` — plain Mockito. Creates a fake object manually. Used in pure unit tests.
- `@MockBean` — Spring's version. Replaces a real Spring bean with a fake inside the Spring context. Used when you need Spring to wire things up.

**Your code — TimeSlotServiceIdleProvidersTest.java:**

```java
@ExtendWith(MockitoExtension.class)  // Enable Mockito
class TimeSlotServiceIdleProvidersTest {

    @Mock private TimeSlotRepository timeSlotRepository;        // Fake DB
    @Mock private ProviderServiceClient providerServiceClient;  // Fake Feign

    private TimeSlotService timeSlotService;  // THE REAL CLASS being tested

    @BeforeEach
    void setUp() {
        // Inject the fakes into the real service
        timeSlotService = new TimeSlotService(
            timeSlotRepository, null, null, null, null, providerServiceClient);
    }
```

**Test 1: Happy path — Feign returns provider details, DTO is built correctly**
```java
@Test
void findIdleProviders_enrichesLocalAggregateWithProviderDetails() {
    // ARRANGE: Tell the fake DB what to return
    when(timeSlotRepository.findIdleProviderIds(eq(2), any(LocalDate.class)))
        .thenReturn(List.of(row(7L, 1L, 5L), row(9L, 0L, 3L)));
    // ARRANGE: Tell the fake Feign what to return
    when(providerServiceClient.getProvider(7L))
        .thenReturn(provider(7L, "Ahmed Plumbing", "Plumbing", 4.8));
    when(providerServiceClient.getProvider(9L))
        .thenReturn(provider(9L, "Cairo Cleaning", "Cleaning", 4.4));

    // ACT: Call the real method
    List<IdleProviderDTO> result = timeSlotService.findIdleProviders(2, 30);

    // ASSERT: Check the output is correct
    assertThat(result).containsExactly(
        new IdleProviderDTO(7L, "Ahmed Plumbing", "Plumbing", 4.8, 1L, 5L),
        new IdleProviderDTO(9L, "Cairo Cleaning", "Cleaning", 4.4, 0L, 3L)
    );
}
```

**Test 2: Feign returns 404 — service skips that provider**
```java
@Test
void findIdleProviders_skipsProviderWhenFeignReturns404() {
    when(providerServiceClient.getProvider(99L))
        .thenThrow(new FeignException.NotFound(...));  // Simulate 404

    List<IdleProviderDTO> result = timeSlotService.findIdleProviders(2, 30);
    // Provider 99 is skipped, only provider 7 returned
    assertThat(result).hasSize(1);
}
```

**Test 3: Feign throws 503 — service returns partial results**
```java
@Test
void findIdleProviders_returnsPartialResultsWhenProviderServiceFails() {
    when(providerServiceClient.getProvider(9L))
        .thenThrow(FeignException.errorStatus("getProvider", response(503)));
    // Provider 9 is skipped due to 503, only provider 7 returned
}
```

**Test 4: Invalid input — service rejects negative values**
```java
@Test
void findIdleProviders_rejectsNegativeInputs() {
    assertThatThrownBy(() -> timeSlotService.findIdleProviders(-1, 30))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("maxBookedSlots");
}
```

**The testing pattern is always: ARRANGE → ACT → ASSERT**

---

### 1B — RabbitMQ Integration Test with Testcontainers

**Concept: What is Testcontainers?**
A Java library that starts REAL Docker containers during your test. You get a real RabbitMQ, real PostgreSQL, etc. — not a fake. When the test ends, the container is destroyed.

**Concept: Why not just use @Mock for RabbitMQ?**
Unit tests with mocks prove your logic is correct. Integration tests prove your WIRING is correct — that your consumer actually connects to the queue, deserializes the message correctly, and processes it. Mocks can't test that.

**Your code — BookingPaymentEventListenerRabbitIT.java:**

```java
@Testcontainers(disabledWithoutDocker = true)  // Skip if Docker isn't available
class BookingPaymentEventListenerRabbitIT {

    @Container  // Start a REAL RabbitMQ Docker container
    static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3.13-management");
```

**The test flow:**

Step 1 — Connect to the real RabbitMQ container:
```java
CachingConnectionFactory cf = new CachingConnectionFactory(rabbit.getHost(), rabbit.getAmqpPort());
```

Step 2 — Declare queues and exchanges (just like Spring @Bean config does):
```java
Queue feedbackQueue = new Queue("booking.saga-feedback.test");
DirectExchange paymentExchange = new DirectExchange("payment.events.test");
admin.declareQueue(feedbackQueue);
admin.declareExchange(paymentExchange);
admin.declareBinding(BindingBuilder.bind(feedbackQueue).to(paymentExchange).with("payment.failed"));
```

Step 3 — Wire up the consumer (BookingPaymentEventListener):
```java
MessageListenerAdapter adapter = new MessageListenerAdapter(
    new BookingPaymentEventListener(repository, publisher), "handlePaymentFailed");
container.setQueueNames(feedbackQueue.getName());
container.setMessageListener(adapter);
container.start();
```

Step 4 — Publish a real event:
```java
template.convertAndSend("payment.events.test", "payment.failed",
    new PaymentFailedEvent(100L, 55L, "card declined"));
```

Step 5 — Wait and assert the consumer processed it:
```java
await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
    assertThat(localStatus.get()).isEqualTo(Booking.Status.PAYMENT_FAILED));
```

Step 6 — Assert the compensation event was published:
```java
BookingCancelledEvent compensation = (BookingCancelledEvent) template.receiveAndConvert(...);
assertThat(compensation.bookingId()).isEqualTo(55L);
assertThat(compensation.reason()).isEqualTo("card declined");
```

**What this proves:** The ENTIRE chain works with real RabbitMQ: event arrives → consumer deserializes it → updates DB → publishes compensation event.

---

### 1C — Saga End-to-End Test

**Concept: What is an E2E test?**
Tests the ENTIRE system. All services must be running. You make real HTTP calls and verify the saga flows correctly across services.

**Your code — SagaScenarioAIT.java (Happy Path):**

```
Step 1: POST /api/users/register       → Create user (201)
Step 2: POST /api/providers             → Create provider (201)
Step 3: POST /api/bookings              → Create booking (201) → status: REQUESTED
Step 4: PUT /api/bookings/{id}/confirm  → Confirm (200) → status: CONFIRMED
Step 5: PUT /api/bookings/{id}/complete → Complete (200) → status: COMPLETING
    ↓ (booking.completed event published via RabbitMQ)
Step 6: POLL invoice-service until invoice status = PENDING (async!)
Step 7: POST /api/invoices/process      → Pay (201)
Step 8: POLL invoice-service until invoice status = COMPLETED
```

**SagaScenarioBIT.java (Payment Failure):**
Same steps 1-6, but at step 7 inject `?simulateFailure=true` → payment fails → assert compensation runs → booking ends up REFUNDED.

**SagaScenarioCIT.java (Pre-check Failure):**
Tests what happens when the saga pre-checks fail (e.g., no time slot exists in calendar-service).

---

## BONUS 2: CI/CD Pipeline (GitHub Actions)

**Concept: What is CI/CD?**
- **CI (Continuous Integration):** Every time code is pushed, automatically build and test it. Catch bugs early.
- **CD (Continuous Delivery):** After tests pass, automatically push Docker images to a registry so they can be deployed.

**Concept: What is GitHub Actions?**
GitHub's built-in automation platform. You write a YAML file in `.github/workflows/` and GitHub runs it on every push/PR.

**Concept: What is GHCR?**
GitHub Container Registry — a place to store Docker images, like Docker Hub but hosted by GitHub.

**Your code — .github/workflows/ci.yml:**

**Two jobs:**

**Job 1: `build-test-docker` (runs on `feat/*` branches only)**
```yaml
if: startsWith(github.ref_name, 'feat/')  # Only feature branches
steps:
  - uses: actions/checkout@v4              # Clone the repo
  - uses: actions/setup-java@v4            # Install Java 25
  - run: mvn -B clean verify              # Build + run ALL JUnit tests
  - run: docker build -t ... ./user-service  # Build Docker images (but DON'T push)
```
Purpose: Verify your code compiles and tests pass before merging.

**Job 2: `publish-images` (runs on `main` branch only)**
```yaml
if: github.ref_name == 'main'
steps:
  - run: mvn -B clean verify              # Build + test again
  - uses: docker/login-action@v3           # Login to GitHub Container Registry
  - run: |                                 # Build AND PUSH images
      for service in user-service provider-service ...; do
        docker build -t "${image}:${SHA}" -t "${image}:latest" "./${service}"
        docker push "${image}:${SHA}"      # Tag with commit hash
        docker push "${image}:latest"      # Tag with "latest"
      done
```
Purpose: After merging to main, push images so K8s can pull and deploy them.

**The flow:**
```
Developer pushes to feat/M3/calendar/S4-READ-DB/55-8947
  → GitHub Actions: mvn build + test + docker build (no push)
  → Tests pass ✓

Developer merges PR to main
  → GitHub Actions: mvn build + test + docker build + docker push to GHCR
  → Images available at ghcr.io/team28/springshoes-booking/calendar-service:latest
```

---

## BONUS 3: Circuit Breaker (Resilience4j)

**Concept: What problem does it solve?**
If provider-service is DOWN, every Feign call waits for a timeout (e.g., 5 seconds) then fails. With 100 requests, that's 500 seconds of wasted waiting. A circuit breaker STOPS calling after detecting failures and returns a fallback INSTANTLY.

**Concept: The 3 States**

```
CLOSED (normal operation)
  │  Calls go through normally
  │  Tracking: last 5 calls (sliding window)
  │
  ▼  If 50% of last 5 calls FAIL...
OPEN (circuit tripped)
  │  ALL calls immediately return FALLBACK
  │  No HTTP request is even attempted
  │  Timer: wait 5 seconds
  │
  ▼  After 5 seconds...
HALF-OPEN (testing)
  │  Allow 2 test calls through
  │  If they succeed → go back to CLOSED
  │  If they fail → go back to OPEN
```

**Your config — application.yml:**
```yaml
resilience4j:
  circuitbreaker:
    instances:
      provider-service:
        sliding-window-size: 5                           # Track last 5 calls
        failure-rate-threshold: 50                       # 50% failure = trip
        wait-duration-in-open-state: 5s                  # Wait 5s before retry
        permitted-number-of-calls-in-half-open-state: 2  # 2 test calls
```

**Your Feign client with fallback:**
```java
@FeignClient(name = "calendar-service", url = "...",
             fallback = CalendarServiceClientFallback.class)  // ← Fallback class
public interface CalendarServiceClient { ... }
```

**Your fallback class:**
```java
@Component
public class CalendarServiceClientFallback implements CalendarServiceClient {
    @Override
    public TimeSlotDTO getSlotForBooking(Long providerId, String date, String startTime) {
        // Return a safe default instead of crashing
        return new TimeSlotDTO(null, providerId, parsedDate, parsedStart, null, false,
                               Map.of("fallback", true));  // Mark as fallback
    }
}
```

**Enable in application.yml:**
```yaml
spring:
  cloud:
    openfeign:
      circuitbreaker:
        enabled: true   # Enable circuit breaker for ALL Feign clients
```

**Real-world analogy:** Electric fuse. Too much current → fuse blows → protects the house. After you fix the problem, you reset the fuse. Circuit breaker = automatic fuse that resets itself after a cooldown.

---

## BONUS 4: Kubernetes Ingress

**Concept: What is the problem with NodePort?**
```
# NodePort = ugly:
curl http://192.168.49.2:30080/api/bookings
# What is 192.168.49.2? What is 30080? Nobody remembers this.

# Ingress = clean:
curl http://springshoes.local/api/bookings
# Human-readable domain name
```

**Concept: What is an Ingress?**
An Ingress is a K8s resource that acts as a REVERSE PROXY. It sits at the edge of your cluster and routes HTTP traffic to internal services based on hostname and path.

**Concept: What is an Ingress Controller?**
The Ingress YAML just defines rules. The Ingress Controller (nginx) is the software that actually reads those rules and does the routing. You enable it with: `minikube addons enable ingress`.

**Your code — gateway-ingress.yaml:**
```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: api-gateway
  namespace: booking
  annotations:
    nginx.ingress.kubernetes.io/rewrite-target: /$2  # Strip path prefix
spec:
  ingressClassName: nginx
  rules:
    - host: springshoes.local          # Match this hostname
      http:
        paths:
          - path: /api(/|$)(.*)        # Anything starting with /api
            pathType: ImplementationSpecific
            backend:
              service:
                name: api-gateway      # Forward to api-gateway service
                port:
                  number: 8080
          - path: /actuator(/|$)(.*)   # Health check endpoints
            backend:
              service:
                name: api-gateway
                port:
                  number: 8080
```

**Setup steps:**
```bash
# 1. Enable ingress controller in minikube
minikube addons enable ingress

# 2. Add hostname to /etc/hosts
echo "$(minikube ip) springshoes.local" | sudo tee -a /etc/hosts

# 3. Apply the ingress
kubectl apply -f k8s/api-gateway/gateway-ingress.yaml

# 4. Now access via clean URL
curl http://springshoes.local/api/bookings
```

**The rewrite-target annotation:**
`/api(/|$)(.*)` captures everything after `/api` into `$2`. Then `rewrite-target: /$2` forwards it. So `http://springshoes.local/api/bookings/1` becomes `http://api-gateway:8080/api/bookings/1`.

---

## BONUS 5: Horizontal Pod Autoscaler (HPA)

**Concept: What is the problem?**
If booking-service gets flooded with requests, ONE pod can't handle it all. CPU goes to 100%, responses slow down, timeouts happen.

**Concept: What does HPA do?**
Watches CPU usage. If it's too high → creates more pods. If it's low → removes pods. Automatic horizontal scaling.

**Your code — booking-service-hpa.yaml:**
```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: booking-service
  namespace: booking
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: booking-service     # Watch THIS deployment
  minReplicas: 2              # Never go below 2 pods
  maxReplicas: 6              # Never go above 6 pods
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 50   # Target 50% CPU
```

**How the math works:**
```
Current: 2 pods, each at 80% CPU → average = 80%
Target: 50%
Formula: desiredReplicas = ceil(currentReplicas × (currentUtil / targetUtil))
         = ceil(2 × (80/50)) = ceil(3.2) = 4 pods

K8s creates 2 more pods → now 4 pods → CPU drops to ~40% each → stable
```

**Setup:**
```bash
# 1. Enable metrics-server (required for HPA to read CPU)
minikube addons enable metrics-server

# 2. Apply HPA
kubectl apply -f k8s/deployments/booking-service-hpa.yaml

# 3. Watch it
kubectl get hpa -n booking -w

# 4. Simulate load (in another terminal)
kubectl run load-gen --image=busybox -- /bin/sh -c \
  "while true; do wget -q -O- http://booking-service:8080/api/bookings; done"

# 5. Watch pods scale up
kubectl get pods -n booking -w
```

**Requirements:**
- `metrics-server` must be running (provides CPU/memory data)
- booking-service Deployment must have `resources.requests.cpu` set (so K8s knows what 100% means)

---

## 50 EVALUATION QUESTIONS & ANSWERS

### Testing (Q1-Q15)

**Q1: What is the difference between a unit test and an integration test?**
A: Unit test = test ONE class in isolation with mocked dependencies. Integration test = test multiple components working together with real infrastructure (real RabbitMQ, real DB).

**Q2: What does @Mock do?**
A: Creates a fake implementation of an interface/class. You program it with `when().thenReturn()` to return specific values when specific methods are called.

**Q3: Why do you mock ProviderServiceClient in unit tests?**
A: Because provider-service isn't running during unit tests. We fake its responses so we can test our calendar-service logic in isolation.

**Q4: What does `when(repo.findIdleProviderIds(...)).thenReturn(...)` do?**
A: It tells the fake repository: "When someone calls findIdleProviderIds with these parameters, return this fake list instead of querying a real database."

**Q5: What is the Arrange-Act-Assert pattern?**
A: Arrange = set up fakes and data. Act = call the method being tested. Assert = verify the output is correct.

**Q6: What is Testcontainers?**
A: A Java library that starts real Docker containers (RabbitMQ, PostgreSQL, etc.) during tests. The container is created before the test and destroyed after.

**Q7: Why use Testcontainers instead of mocking RabbitMQ?**
A: Mocks test logic but not wiring. Testcontainers proves the actual message serialization, queue binding, routing key matching, and consumer processing all work together.

**Q8: What does @Container do?**
A: Marks a field as a Testcontainers-managed Docker container. The container starts before any test in the class runs and stops after all tests finish.

**Q9: What does `await().atMost(Duration.ofSeconds(10)).untilAsserted(...)` do?**
A: Polls repeatedly (every 100ms by default) for up to 10 seconds until the assertion passes. This is needed because RabbitMQ processing is async — the consumer might not have processed the message instantly.

**Q10: What does the RabbitMQ integration test actually prove?**
A: That when a PaymentFailedEvent arrives on the queue, the BookingPaymentEventListener (1) deserializes it correctly, (2) updates booking status to PAYMENT_FAILED, and (3) publishes a BookingCancelledEvent as compensation.

**Q11: What is a Saga E2E test?**
A: A test that exercises the entire booking lifecycle across ALL running services via real HTTP calls. It verifies the complete event chain: booking completed → invoice created → payment processed → booking paid.

**Q12: Why does the E2E test use polling?**
A: Because the saga is asynchronous. After calling PUT /complete, invoice-service creates the invoice via a RabbitMQ event. The test polls until the invoice appears because there's no way to know exactly when the async event will be processed.

**Q13: What are the 3 saga test scenarios?**
A: Scenario A = happy path (booking → paid). Scenario B = payment failure (booking → payment failed → compensation → refunded). Scenario C = pre-check failure (saga aborts before publishing booking.completed).

**Q14: What does `assertThat(result).containsExactly(...)` do?**
A: AssertJ assertion that checks the list contains exactly the specified elements in exactly the specified order. Fails if there are extra elements, missing elements, or wrong order.

**Q15: What assertion library do you use?**
A: AssertJ (`assertThat(...).isEqualTo(...)`) for fluent assertions, and JUnit 5 (`assertEquals(...)`) for simple ones.

### CI/CD (Q16-Q25)

**Q16: What is CI/CD?**
A: CI = Continuous Integration: auto-build and test on every push. CD = Continuous Delivery: auto-push Docker images to a registry after tests pass.

**Q17: What is GitHub Actions?**
A: GitHub's built-in CI/CD platform. You define workflows in `.github/workflows/*.yml` and GitHub runs them automatically on triggers (push, PR, etc.).

**Q18: What triggers the build-test-docker job?**
A: Pushing to any `feat/*` or `feat/**` branch. The `if: startsWith(github.ref_name, 'feat/')` condition ensures it only runs on feature branches.

**Q19: What triggers the publish-images job?**
A: Pushing to the `main` branch. The `if: github.ref_name == 'main'` condition ensures it only runs on main.

**Q20: What is `mvn -B clean verify`?**
A: `-B` = batch mode (no interactive prompts). `clean` = delete old build files. `verify` = compile + run all tests + package. If any test fails, the build fails and the pipeline stops.

**Q21: What does GHCR stand for?**
A: GitHub Container Registry. It's where Docker images are stored, similar to Docker Hub. Each image is tied to your GitHub repo.

**Q22: Why tag images with both `${SHA}` and `latest`?**
A: SHA tag = specific version tied to a commit (for rollbacks). Latest tag = always points to the newest version (for convenience). You push both for flexibility.

**Q23: What is `${{ secrets.GITHUB_TOKEN }}`?**
A: An automatically generated token by GitHub Actions that allows the workflow to push images to GHCR. No manual secret setup needed.

**Q24: Why doesn't the feat/* job push images?**
A: Feature branches are work-in-progress. You only push to the registry when code is merged to main (reviewed and approved). Publishing broken images from feature branches would be bad.

**Q25: What happens if `mvn verify` fails?**
A: The entire pipeline stops. Docker images are NOT built or pushed. The developer gets a notification that the build failed.

### Circuit Breaker (Q26-Q35)

**Q26: What problem does a circuit breaker solve?**
A: Prevents cascading failures. If provider-service is down, without a circuit breaker every request waits for timeout (slow). With a circuit breaker, after detecting failures it immediately returns a fallback (fast).

**Q27: What are the 3 states of a circuit breaker?**
A: CLOSED (normal — calls go through), OPEN (tripped — all calls return fallback immediately), HALF-OPEN (testing — allows a few calls through to check if the service recovered).

**Q28: What is `sliding-window-size: 5`?**
A: The circuit breaker tracks the last 5 calls. It calculates the failure rate based on these 5 calls.

**Q29: What is `failure-rate-threshold: 50`?**
A: If 50% or more of the last 5 calls fail (i.e., 3 out of 5), the circuit trips from CLOSED to OPEN.

**Q30: What is `wait-duration-in-open-state: 5s`?**
A: When the circuit is OPEN, it stays open for 5 seconds. During this time ALL calls return the fallback. After 5 seconds it transitions to HALF-OPEN.

**Q31: What is `permitted-number-of-calls-in-half-open-state: 2`?**
A: In HALF-OPEN state, allow 2 test calls through. If both succeed → CLOSED. If either fails → back to OPEN for another 5 seconds.

**Q32: What is a fallback class?**
A: A class that implements the same Feign interface but returns safe default values. It's used when the circuit is open or the service is unreachable.

**Q33: What does `Map.of("fallback", true)` in the fallback response indicate?**
A: It signals to the caller that this response is degraded/fallback data, not real data from the service. The caller can check this flag and display a warning to the user.

**Q34: What dependency enables circuit breakers?**
A: `spring-cloud-starter-circuitbreaker-resilience4j` in pom.xml, plus `spring.cloud.openfeign.circuitbreaker.enabled: true` in application.yml.

**Q35: How do you demonstrate the circuit breaker working?**
A: (1) Stop provider-service. (2) Make 5 calls to calendar-service that use Feign → provider-service. (3) First 5 calls fail with timeout. (4) 6th call returns fallback INSTANTLY (circuit is now OPEN). (5) Wait 5 seconds, restart provider-service. (6) Next call succeeds (HALF-OPEN → CLOSED).

### Ingress (Q36-Q42)

**Q36: What is a Kubernetes Ingress?**
A: A K8s resource that defines HTTP routing rules. It maps external URLs (hostname + path) to internal services.

**Q37: What is an Ingress Controller?**
A: The actual software (nginx) that reads Ingress rules and performs the routing. The Ingress YAML is just config; the controller does the work.

**Q38: Why Ingress instead of NodePort?**
A: NodePort exposes random high ports (30000-32767) — ugly and hard to remember. Ingress provides clean domain names, path-based routing, optional SSL, and rate limiting.

**Q39: What does `minikube addons enable ingress` do?**
A: Deploys an nginx Ingress Controller pod in your MiniKube cluster. Without this, Ingress YAML rules do nothing.

**Q40: What does `rewrite-target: /$2` do?**
A: Strips the matched path prefix. The regex `/api(/|$)(.*)` captures everything after `/api` into `$2`. So `/api/bookings/1` gets rewritten to `/bookings/1`... wait no — in your case the path IS `/api/bookings/1` and `$2` = `bookings/1`, so rewrite-target `/$2` = `/bookings/1`. But your gateway already expects `/api/...`, so the annotation preserves the full path.

**Q41: What does `pathType: ImplementationSpecific` mean?**
A: The path matching behavior is delegated to the Ingress Controller (nginx). Different from `Exact` (exact match only) or `Prefix` (prefix match).

**Q42: How do you test the Ingress?**
A: Add `$(minikube ip) springshoes.local` to `/etc/hosts`, then `curl http://springshoes.local/api/bookings` — it should route through the Ingress to the api-gateway.

### HPA (Q43-Q50)

**Q43: What is HPA?**
A: Horizontal Pod Autoscaler — automatically scales the number of pod replicas up or down based on CPU (or custom metrics).

**Q44: Why is HPA on booking-service specifically?**
A: Booking-service has the highest traffic — it's the saga orchestrator. More bookings = more CPU = needs more pods.

**Q45: What does `minReplicas: 2` mean?**
A: K8s will ALWAYS keep at least 2 booking-service pods running, even if CPU is at 0%. This provides high availability — if one pod crashes, the other still serves traffic.

**Q46: What does `averageUtilization: 50` mean?**
A: The target is 50% average CPU across all pods. If average goes above 50% → scale up. Below 50% → scale down (to minimum 2).

**Q47: What is metrics-server?**
A: A K8s component that collects CPU and memory metrics from all pods. HPA reads these metrics to make scaling decisions. Without it, HPA can't see CPU usage.

**Q48: How does HPA calculate desired replicas?**
A: Formula: `desired = ceil(current × (currentCPU / targetCPU))`. Example: 2 pods at 80% CPU, target 50% → `ceil(2 × 80/50) = ceil(3.2) = 4` pods.

**Q49: What is "horizontal" vs "vertical" scaling?**
A: Horizontal = add MORE pods (scale OUT). Vertical = give existing pods MORE CPU/RAM (scale UP). HPA does horizontal.

**Q50: How do you demonstrate HPA?**
A: (1) `minikube addons enable metrics-server` (2) Apply HPA yaml (3) Run a load generator: `kubectl run load --image=busybox -- sh -c "while true; do wget -q -O- http://booking-service:8080/api/bookings; done"` (4) Watch: `kubectl get hpa -n booking -w` — see TARGETS column rise and REPLICAS increase.
