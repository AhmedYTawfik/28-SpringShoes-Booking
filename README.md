<![CDATA[<div align="center">

# 👟 SpringShoes Booking Platform

### A Cloud-Native Microservices Booking System

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.3-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Spring Cloud](https://img.shields.io/badge/Spring%20Cloud-2025.1.1-6DB33F?style=for-the-badge&logo=spring&logoColor=white)](https://spring.io/projects/spring-cloud)
[![Java](https://img.shields.io/badge/Java-25-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?style=for-the-badge&logo=docker&logoColor=white)](https://docs.docker.com/compose/)
[![Kubernetes](https://img.shields.io/badge/Kubernetes-Minikube-326CE5?style=for-the-badge&logo=kubernetes&logoColor=white)](https://kubernetes.io/)
[![License](https://img.shields.io/badge/License-Academic-blue?style=for-the-badge)](#)

> A production-grade, event-driven booking platform built with **5 domain microservices**, an **API Gateway**, a **choreography-based Saga**, and full **Kubernetes orchestration** — complete with observability, CI/CD, and resilience patterns.

---

</div>

## 📑 Table of Contents

- [Overview](#-overview)
- [Architecture](#-architecture)
- [Tech Stack](#-tech-stack)
- [Services](#-services)
- [Design Patterns](#-design-patterns)
- [Event-Driven Saga](#-event-driven-saga)
- [Infrastructure](#-infrastructure)
- [Observability](#-observability)
- [Resilience](#-resilience)
- [CI/CD Pipeline](#-cicd-pipeline)
- [Getting Started](#-getting-started)
- [Kubernetes Deployment](#-kubernetes-deployment)
- [API Reference](#-api-reference)
- [Testing](#-testing)
- [Team](#-team)

---

## 🔭 Overview

**SpringShoes** is a full-featured service booking platform where **users** can discover and book **providers** for various services (plumbing, cleaning, tutoring, etc.). The system handles the entire lifecycle — from user registration and provider discovery, through time-slot scheduling and booking confirmation, to invoice generation and payment processing — all orchestrated via asynchronous events.

### ✨ Key Highlights

| Feature | Description |
|---|---|
| 🏗️ **Microservices** | 5 domain services + API Gateway, each with independent databases |
| 📨 **Event-Driven** | Choreography-based Saga with RabbitMQ for cross-service workflows |
| 🗄️ **Polyglot Persistence** | PostgreSQL · MongoDB · Cassandra · Neo4j · Elasticsearch · Redis |
| 🔐 **Security** | JWT authentication with gateway-level enforcement |
| ☸️ **Cloud-Native** | Full Kubernetes manifests with StatefulSets, Ingress, and HPA |
| 📊 **Observability** | Prometheus metrics + Grafana dashboards + Actuator health |
| 🛡️ **Resilience** | Resilience4j circuit breakers on all Feign clients |
| 🔄 **CI/CD** | GitHub Actions pipeline → GHCR image publishing |
| 🧪 **Comprehensive Tests** | Unit (Mockito) · Integration (Testcontainers) · E2E Saga tests |

---

## 🏛️ Architecture

```
                        ┌──────────────────────┐
                        │   springshoes.local   │
                        │      (Ingress)        │
                        └──────────┬───────────┘
                                   │
                        ┌──────────▼───────────┐
                        │    API Gateway :8080  │
                        │  (Spring Cloud GW)    │
                        │  JWT Auth · Routing   │
                        └──────────┬───────────┘
                                   │
          ┌────────────┬───────────┼───────────┬────────────┐
          │            │           │           │            │
   ┌──────▼──────┐ ┌───▼────┐ ┌───▼────┐ ┌────▼───┐ ┌─────▼────┐
   │    User     │ │Provider│ │Booking │ │Calendar│ │ Invoice  │
   │  Service    │ │Service │ │Service │ │Service │ │ Service  │
   │   :8081     │ │ :8082  │ │ :8083  │ │ :8084  │ │  :8085   │
   └──────┬──────┘ └───┬────┘ └───┬────┘ └────┬───┘ └─────┬────┘
          │            │          │            │           │
   ┌──────▼──┐  ┌──────▼───┐ ┌───▼─────┐ ┌───▼────┐ ┌────▼─────┐
   │Postgres │  │Postgres  │ │Postgres │ │Postgres│ │Postgres  │
   │  :5433  │  │  :5434   │ │  :5435  │ │ :5436  │ │  :5437   │
   └─────────┘  └──────────┘ └─────────┘ └────────┘ └──────────┘
          │            │          │            │           │
          └────────────┴──────────┴──────┬─────┴───────────┘
                                         │
              ┌──────────────────────────┼──────────────────────────┐
              │                          │                          │
       ┌──────▼──────┐          ┌────────▼──────┐          ┌───────▼───────┐
       │  RabbitMQ   │          │    MongoDB    │          │    Redis      │
       │  :5672      │          │   :27017      │          │    :6379      │
       │  (Events)   │          │  (Audit Log)  │          │   (Cache)     │
       └─────────────┘          └───────────────┘          └───────────────┘
              │
    ┌─────────┼──────────┬────────────┐
    │         │          │            │
┌───▼───┐ ┌──▼───┐ ┌────▼────┐ ┌────▼─────┐
│Neo4j  │ │Cassan│ │Elastic  │ │Prometheus│
│:7687  │ │-dra  │ │Search   │ │+ Grafana │
│(Graph)│ │:9042 │ │:9200    │ │(Metrics) │
└───────┘ └──────┘ └─────────┘ └──────────┘
```

---

## 🛠️ Tech Stack

### Core Framework
| Technology | Version | Purpose |
|---|---|---|
| **Spring Boot** | 4.0.3 | Service framework |
| **Spring Cloud** | 2025.1.1 | Gateway, OpenFeign, Circuit Breakers |
| **Java** | 25 | Language runtime |
| **Maven** | 3.9+ | Build & dependency management |

### Data Stores
| Database | Usage |
|---|---|
| **PostgreSQL 17** | Primary relational store (one per service) |
| **MongoDB** | Audit trail / event sourcing log |
| **Apache Cassandra** | High-throughput time-slot read model (calendar-service) |
| **Neo4j** | Provider recommendation graph (booking-service) |
| **Elasticsearch 9** | Full-text provider search (provider-service) |
| **Redis** | Distributed caching layer |

### Messaging & Communication
| Technology | Purpose |
|---|---|
| **RabbitMQ** | Asynchronous event bus (Saga choreography) |
| **OpenFeign** | Synchronous inter-service HTTP calls |
| **Spring Cloud Gateway** | API routing, JWT validation |

### Infrastructure & DevOps
| Technology | Purpose |
|---|---|
| **Docker Compose** | Local multi-container orchestration |
| **Kubernetes** | Production-grade container orchestration |
| **GitHub Actions** | CI/CD pipeline |
| **GHCR** | Container image registry |
| **Prometheus** | Metrics collection |
| **Grafana** | Metrics visualization |

---

## 🧩 Services

### User Service — `:8081`
> Manages user registration, authentication, profiles, and JWT token issuance.

| Aspect | Detail |
|---|---|
| **Database** | PostgreSQL (`bookingdb-users`) |
| **Secondary Stores** | MongoDB (audit) · Redis (cache) |
| **Key Endpoints** | `/api/users/**` · `/api/auth/**` |
| **Patterns** | Observer · Factory · Adapter |

### Provider Service — `:8082`
> Handles provider registration, service catalogs, ratings, availability, and full-text search.

| Aspect | Detail |
|---|---|
| **Database** | PostgreSQL (`bookingdb-providers`) |
| **Secondary Stores** | MongoDB · Redis · Elasticsearch (search index) |
| **Key Endpoints** | `/api/providers/**` |
| **Patterns** | Observer · Factory · Adapter (Elasticsearch hits) |

### Booking Service — `:8083`
> The **Saga orchestrator** — manages the booking lifecycle from request to payment completion.

| Aspect | Detail |
|---|---|
| **Database** | PostgreSQL (`bookingdb-bookings`) |
| **Secondary Stores** | MongoDB · Redis · Neo4j (recommendations) |
| **Key Endpoints** | `/api/bookings/**` · `/api/booking-services/**` |
| **Patterns** | Observer · Factory · Adapter (Neo4j, Mongo) |

### Calendar Service — `:8084`
> Manages provider time-slot scheduling, availability queries, and idle-provider analytics.

| Aspect | Detail |
|---|---|
| **Database** | PostgreSQL (`bookingdb-calendar`) |
| **Secondary Stores** | MongoDB · Redis · Cassandra (read model) |
| **Key Endpoints** | `/api/timeslots/**` · `/api/calendar/**` |
| **Patterns** | Observer · Factory · Adapter (Cassandra rows) |

### Invoice Service — `:8085`
> Processes payments, generates invoices, applies discounts, and handles refunds.

| Aspect | Detail |
|---|---|
| **Database** | PostgreSQL (`bookingdb-invoices`) |
| **Secondary Stores** | MongoDB · Redis |
| **Key Endpoints** | `/api/invoices/**` · `/api/discounts/**` · `/api/invoice-discounts/**` |
| **Patterns** | Observer · Factory · Adapter · **Strategy** (refund policies) |

### API Gateway — `:8080`
> Single entry point — routes requests, validates JWTs, and exposes health endpoints.

| Aspect | Detail |
|---|---|
| **Framework** | Spring Cloud Gateway (WebFlux) |
| **Auth** | JWT validation via `jjwt` library |
| **Routing** | Path-based routing to all 5 services |

---

## 🎨 Design Patterns

The codebase leverages **four GoF design patterns** consistently across every service:

| Pattern | Implementation | Purpose |
|---|---|---|
| **Observer** | `EntityObserver` | React to domain events (e.g., write audit logs on entity changes) |
| **Factory** | `EventFactory` | Create domain event objects in a centralized, type-safe manner |
| **Adapter** | `MongoDocumentAdapter`, `CassandraRowAdapter`, `Neo4jRecordAdapter`, `ElasticsearchHitAdapter` | Convert between database-specific records and domain DTOs |
| **Strategy** | `RefundStrategy`, `FullRefundStrategy`, `PartialRefundStrategy`, `NoRefundStrategy` | Pluggable refund calculation policies (invoice-service) |

---

## 🔄 Event-Driven Saga

The booking lifecycle is orchestrated via a **choreography-based Saga** over RabbitMQ:

```
 ┌──────────┐    BookingPlaced     ┌──────────────┐   SlotReserved    ┌──────────────┐
 │ Booking  │ ──────────────────▶  │   Calendar   │ ────────────────▶ │   Booking    │
 │ Service  │                      │   Service    │                   │   Service    │
 └──────────┘                      └──────────────┘                   └──────┬───────┘
                                                                             │
                                                                    BookingCompleted
                                                                             │
 ┌──────────┐   PaymentCompleted   ┌──────────────┐   InvoiceCreated  ┌──────▼───────┐
 │ Booking  │ ◀──────────────────  │   Invoice    │ ◀──────────────── │   Invoice    │
 │ Service  │                      │   Service    │                   │   Service    │
 └──────────┘                      └──────────────┘                   └──────────────┘
```

### Saga States
```
REQUESTED → CONFIRMED → COMPLETING → PAID ✅
                ↓ (on failure)
         PAYMENT_FAILED → REFUNDED 🔄
```

### Shared Event Contracts
All inter-service events are defined in the `contracts` module:
`BookingPlacedEvent` · `BookingCompletedEvent` · `BookingCancelledEvent` · `SlotReservedEvent` · `SlotReleasedEvent` · `PaymentInitiatedEvent` · `PaymentCompletedEvent` · `PaymentFailedEvent` · `PaymentRefundedEvent` · `ProviderRatedEvent` · `UserRegisteredEvent` · `UserDeactivatedEvent`

---

## 🏗️ Infrastructure

### Docker Compose (Local Development)

All 11 infrastructure containers + 6 application containers are defined in `docker-compose.yaml`:

```bash
# Start everything
docker compose up -d

# Check health
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"

# Tear down (preserve data)
docker compose down

# Tear down (wipe all data)
docker compose down -v
```

### Kubernetes (Production)

Full K8s manifests are organized under `k8s/`:

```
k8s/
├── namespaces/          # booking namespace
├── secrets/             # DB credentials, JWT secret
├── configmaps/          # Service configuration
├── pvcs/                # Persistent volume claims
├── statefulsets/         # PostgreSQL ×5, Cassandra, Elasticsearch,
│                        #   MongoDB, Neo4j, Redis, RabbitMQ
├── services/            # ClusterIP services
├── deployments/         # Service deployments + HPA
├── api-gateway/         # Gateway deployment + Ingress
├── infra/               # Local path provisioner
└── monitoring/          # Prometheus + Grafana
```

---

## 📊 Observability

| Tool | Endpoint | Purpose |
|---|---|---|
| **Prometheus** | `/actuator/prometheus` | Metrics scraping from all services |
| **Grafana** | Dashboards | Visualization of HTTP latency, throughput, JVM stats |
| **Actuator** | `/actuator/health` | Service health checks with details |

Prometheus is pre-configured to scrape all 6 services at 15-second intervals.

---

## 🛡️ Resilience

### Circuit Breakers (Resilience4j)

Every inter-service Feign call is protected by a circuit breaker:

```
CLOSED ─── 50% of last 5 calls fail ──▶ OPEN ─── 5s cooldown ──▶ HALF-OPEN
  ▲                                                                    │
  └──────────────── 2 test calls succeed ◀─────────────────────────────┘
```

| Parameter | Value |
|---|---|
| Sliding window size | `5` calls |
| Failure rate threshold | `50%` |
| Wait in open state | `5 seconds` |
| Half-open test calls | `2` |

Each Feign client has a dedicated **fallback class** that returns safe defaults when the target service is unavailable.

---

## 🔄 CI/CD Pipeline

GitHub Actions workflow (`.github/workflows/ci.yml`) with two jobs:

```
┌─────────────────────────────────────────────────────────────────┐
│                    Push to feat/* branch                        │
│  ┌──────────┐   ┌──────────────┐   ┌───────────────────────┐   │
│  │ Checkout │ → │ Java 25 JDK  │ → │ mvn clean verify      │   │
│  └──────────┘   └──────────────┘   │ + docker build (no    │   │
│                                     │   push)               │   │
│                                     └───────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                    Push to main branch                          │
│  ┌──────────┐   ┌───────────┐   ┌──────────┐   ┌───────────┐  │
│  │ Checkout │ → │ mvn clean │ → │ GHCR     │ → │ Build &   │  │
│  │          │   │  verify   │   │  Login   │   │ Push imgs │  │
│  └──────────┘   └───────────┘   └──────────┘   └───────────┘  │
│                                                                 │
│  Images: ghcr.io/<owner>/springshoes-booking/<service>:latest  │
│          ghcr.io/<owner>/springshoes-booking/<service>:<sha>    │
└─────────────────────────────────────────────────────────────────┘
```

---

## 🚀 Getting Started

### Prerequisites

| Tool | Version |
|---|---|
| Java (JDK) | 25+ |
| Maven | 3.9+ |
| Docker & Docker Compose | Recent |

### 1. Clone & Start Infrastructure

```bash
git clone https://github.com/AhmedYTawfik/28-SpringShoes-Booking.git
cd 28-SpringShoes-Booking

# Start all databases and infrastructure
docker compose up -d

# Verify containers are healthy
docker ps
```

### 2. Build All Services

```bash
mvn clean install -DskipTests
```

### 3. Run Services

Each service runs in its own terminal:

```bash
# Terminal 1 — User Service
cd user-service && mvn spring-boot:run

# Terminal 2 — Provider Service
cd provider-service && mvn spring-boot:run

# Terminal 3 — Booking Service
cd booking-service && mvn spring-boot:run

# Terminal 4 — Calendar Service
cd calendar-service && mvn spring-boot:run

# Terminal 5 — Invoice Service
cd invoice-service && mvn spring-boot:run

# Terminal 6 — API Gateway
cd api-gateway && mvn spring-boot:run
```

> ⚠️ Infrastructure containers must be **healthy** before starting services.

### 4. Verify

```bash
# Health checks
curl http://localhost:8080/actuator/health   # Gateway
curl http://localhost:8081/api/users/health
curl http://localhost:8082/api/providers/health
curl http://localhost:8083/api/bookings/health
curl http://localhost:8084/api/timeslots/health
curl http://localhost:8085/api/invoices/health
```

---

## ☸️ Kubernetes Deployment

### Quick Start with Minikube

```bash
# 1. Start Minikube
minikube start --memory=8192 --cpus=4

# 2. Enable required addons
minikube addons enable ingress
minikube addons enable metrics-server

# 3. Apply manifests (order matters)
kubectl apply -f k8s/namespaces/
kubectl apply -f k8s/secrets/
kubectl apply -f k8s/configmaps/
kubectl apply -f k8s/pvcs/
kubectl apply -f k8s/statefulsets/
kubectl apply -f k8s/services/
kubectl apply -f k8s/deployments/
kubectl apply -f k8s/api-gateway/

# 4. Configure local DNS
echo "$(minikube ip) springshoes.local" | sudo tee -a /etc/hosts

# 5. Access via Ingress
curl http://springshoes.local/api/bookings
```

### Horizontal Pod Autoscaler

The booking-service is configured with HPA for automatic scaling:

| Parameter | Value |
|---|---|
| Min replicas | `2` |
| Max replicas | `6` |
| Target CPU | `50%` average utilization |

```bash
# Watch autoscaling in action
kubectl get hpa -n booking -w
```

---

## 📡 API Reference

### Routing (via API Gateway `:8080`)

| Route Pattern | Target Service |
|---|---|
| `/api/users/**` · `/api/auth/**` | User Service |
| `/api/providers/**` | Provider Service |
| `/api/bookings/**` · `/api/booking-services/**` | Booking Service |
| `/api/timeslots/**` · `/api/calendar/**` | Calendar Service |
| `/api/invoices/**` · `/api/discounts/**` · `/api/invoice-discounts/**` | Invoice Service |

### Direct Service Ports (Local Development)

| Service | Port | Base URL |
|---|---|---|
| API Gateway | `8080` | `http://localhost:8080` |
| User Service | `8081` | `http://localhost:8081/api/users` |
| Provider Service | `8082` | `http://localhost:8082/api/providers` |
| Booking Service | `8083` | `http://localhost:8083/api/bookings` |
| Calendar Service | `8084` | `http://localhost:8084/api/timeslots` |
| Invoice Service | `8085` | `http://localhost:8085/api/invoices` |

---

## 🧪 Testing

### Test Pyramid

| Layer | Framework | Location | Description |
|---|---|---|---|
| **Unit** | JUnit 5 + Mockito | `*Test.java` | Isolated logic with mocked Feign clients & repos |
| **Integration** | Testcontainers | `*RabbitIT.java` | Real RabbitMQ in Docker — tests event wiring |
| **E2E Saga** | RestAssured | `SagaScenario[A/B/C]IT.java` | Full lifecycle across all running services |

### Running Tests

```bash
# All tests (unit + integration)
mvn clean verify

# Unit tests only
mvn test

# Integration tests only (requires Docker)
mvn failsafe:integration-test failsafe:verify
```

### Saga E2E Scenarios

| Scenario | File | Flow |
|---|---|---|
| ✅ Happy Path | `SagaScenarioAIT.java` | Register → Book → Confirm → Complete → Pay → PAID |
| ❌ Payment Failure | `SagaScenarioBIT.java` | ... → Complete → Payment fails → Compensate → REFUNDED |
| 🚫 Pre-check Failure | `SagaScenarioCIT.java` | Book fails validation → Saga aborts early |

---

## 🏆 Team

<div align="center">

### Team 28 — SpringShoes

</div>

| Service | Member | GitHub |
|---|---|---|
| **User Service** | Paula Maged Michael | [@PaulaMaged](https://github.com/PaulaMaged) |
| **User Service** | Mahmoud Ayman | [@TheSant0x](https://github.com/TheSant0x) |
| **User Service** | Ragaa Aly | [@ragaaaly](https://github.com/ragaaaly) |
| **Invoice Service** | Abdelrahim Abdelazim | [@abdoo303](https://github.com/abdoo303) |
| **Invoice Service** | Ali Hossam Eldeen | [@Ali-Hosam](https://github.com/Ali-Hosam) |
| **Invoice Service** | Ziad Sherif Ibrahim | [@zeyadalaaser](https://github.com/zeyadalaaser) |
| **Provider Service** | Abdelrahman Atef Saad | [@abdlrhman08](https://github.com/abdlrhman08) |
| **Provider Service** | Yaseen Ashraf | [@zoatel](https://github.com/zoatel) |
| **Provider Service** | Mohamed Youssef | [@M-aboelsafa](https://github.com/M-aboelsafa) |
| **Booking Service** | Ahmed Mohamed El-Gohary | [@AhmedEl-Gohary](https://github.com/AhmedEl-Gohary) |
| **Booking Service** | Rofaeil Samuel Fayez | [@Rofaeil478](https://github.com/Rofaeil478) |
| **Booking Service** | Ahmed Kamal | [@AhmedKamal18](https://github.com/AhmedKamal18) |
| **Calendar Service** | Ahmed Hussien Ali | [@ahmedhussein107](https://github.com/ahmedhussein107) |
| **Calendar Service** | Ahmed Yasser Tawfik | [@AhmedYTawfik](https://github.com/AhmedYTawfik) |

---

<div align="center">

**Built with ❤️ by Team 28** · Spring 2026

</div>
]]>
