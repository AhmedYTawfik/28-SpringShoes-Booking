# M2 Tests — Standalone Pack (Booking)

A complete Maven test project. Unzip, set the env vars below, then run:

    mvn test

The tests connect to YOUR running docker-compose stack over HTTP and direct
DB drivers — there is no grader involvement.

## Prerequisites

- JDK 25 (the course's pinned JDK)
- Maven 3.9+
- Your full docker-compose stack RUNNING in another terminal:

      docker compose up -d

## Required environment variables

Adjust ports/credentials to match what YOUR docker-compose actually publishes.
The defaults below assume the canonical port assignment.

```bash
# ── Service base URLs (host-side, from your docker-compose ports: list) ──
export USER_SERVICE_URL=http://localhost:8081
export CATALOG_SERVICE_URL=http://localhost:8082
export ORDER_SERVICE_URL=http://localhost:8083
export DELIVERY_SERVICE_URL=http://localhost:8084
export CHECKOUT_SERVICE_URL=http://localhost:8085

# ── PostgreSQL ──
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/bookingdb
export SPRING_DATASOURCE_USERNAME=postgres
export SPRING_DATASOURCE_PASSWORD=postgres

# ── MongoDB ──
export SPRING_DATA_MONGODB_URI=mongodb://root:rootpass@localhost:27017/bookingmongo?authSource=admin

# ── Redis ──
export SPRING_DATA_REDIS_HOST=localhost
export SPRING_DATA_REDIS_PORT=6379
export SPRING_DATA_REDIS_PASSWORD=redispass    # match --requirepass in your compose

# ── Neo4j ──
export SPRING_NEO4J_URI=bolt://localhost:7687
export SPRING_NEO4J_AUTHENTICATION_USERNAME=neo4j
export SPRING_NEO4J_AUTHENTICATION_PASSWORD=neo4jpass

# ── Cassandra ──
export SPRING_CASSANDRA_CONTACT_POINTS=localhost
export SPRING_CASSANDRA_PORT=9042
export SPRING_CASSANDRA_LOCAL_DATACENTER=datacenter1
export SPRING_CASSANDRA_KEYSPACE_NAME=bookingks

# ── Elasticsearch ──
export SPRING_ELASTICSEARCH_URIS=http://localhost:9200

# ── Source-scan tests (TC379+ design-pattern checks) need your source tree ──
export REPO_PATH=/absolute/path/to/your/project/root

# ── JWT (tests forge bearer tokens that your services will accept) ──
# Must match the JWT_SECRET your services boot with. Easiest setup:
# pick one base64 secret (>= 32 bytes), put it in your docker-compose .env
# AND export it here before running tests.
export JWT_SECRET="<paste the same base64 secret your services use>"
export JWT_EXPIRATION=3600000
```

## Run the tests

    mvn test

Run a subset by classname pattern:

    mvn test -Dtest='TC01_*'                  # one specific TC
    mvn test -Dtest='TC4[8-9]_*,TC50_*'       # ranges
    mvn test -Dtest='DP_PatternTests'         # design-pattern tests only

Surefire writes per-class XML reports under `target/surefire-reports/`.

## Re-generating manifest.json

The bundled `src/test/resources/manifest.json` is a snapshot of your source
at the moment this pack was generated. If you rename an entity/controller/
enum or add new ones, regenerate it:

    bash scanner/regen-manifest.sh /absolute/path/to/your/project

## What's in this pack

- `src/test/java/com/testgen/booking/` — the test files (PublicTests, DP_PatternTests, TestBase, TestAuthHelper)
- `src/test/resources/manifest.json` — your project's discovered structure
- `src/test/resources/theme.json` — Booking spec constants
- `pom.xml` — Spring Boot 4.0.3 BOM, JDK 25, all DB-driver test deps
- `scanner/` — the manifest regenerator (use after refactoring)
- `README.md` — this file
