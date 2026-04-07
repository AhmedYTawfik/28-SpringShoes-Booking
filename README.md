# SpringShoes Booking — Setup & Run Guide

## Prerequisites

| Tool | Version |
|---|---|
| Java (JDK) | 25+ |
| Maven | 3.9+ |
| Docker & Docker Compose | any recent version |

---

## Project Structure

```
28-SpringShoes-Booking/
├── docker-compose.yaml       # PostgreSQL database
├── pom.xml                   # Root Maven multi-module POM
├── booking-service/          # Port 8080
├── user-service/             # Port 8081
├── provider-service/         # Port 8082
├── invoice-service/          # Port 8083
└── calendar-service/         # Port 8084
```

All services share a single PostgreSQL database (`bookingdb`).

---

## Step 1 — Set Up the Database

All services connect to a PostgreSQL database named `bookingdb` on `localhost:5432`.

### Option A — Docker (recommended if you have no local PostgreSQL)

From the project root:

```bash
docker compose up -d
```

Verify it is healthy:

```bash
docker ps
# booking-db should show status: healthy
```

To stop: `docker compose down`  
To stop and wipe all data: `docker compose down -v`

### Option B — Local PostgreSQL already installed

If you have PostgreSQL installed locally (e.g. via Homebrew), it likely already owns port 5432 and the Docker container will be unreachable. In this case, create the database directly in your local Postgres:

```bash
psql -h 127.0.0.1 -p 5432 -U <your-mac-username> -d postgres -c "CREATE DATABASE bookingdb;"
```

> **How to tell which case applies:** run `lsof -iTCP:5432 -sTCP:LISTEN`. If you see a `postgres` process (not Docker), you have a local installation — use Option B.

**Database connection details (same for both options):**

| Property | Value |
|---|---|
| Host | `localhost` |
| Port | `5432` |
| Database | `bookingdb` |
| Username | `postgres` |
| Password | `postgres` |

---

## Step 2 — Build All Services

From the project root:

```bash
mvn clean install -DskipTests
```

---

## Step 3 — Run Services

Each service must run in its **own terminal**. Run them from the project root:

```bash
# Terminal 1
cd booking-service && mvn spring-boot:run

# Terminal 2
cd user-service && mvn spring-boot:run

# Terminal 3
cd provider-service && mvn spring-boot:run

# Terminal 4
cd invoice-service && mvn spring-boot:run

# Terminal 5
cd calendar-service && mvn spring-boot:run
```

> The database (Step 1) must be running before starting any service.

---

## Service Endpoints

### Health Checks

| Service | URL |
|---|---|
| booking-service | http://localhost:8080/api/bookings/health |
| user-service | http://localhost:8081/api/users/health |
| provider-service | http://localhost:8082/api/providers/health |
| invoice-service | http://localhost:8083/api/invoices/health |
| calendar-service | http://localhost:8084/api/timeslots/health |

### API Base URLs

| Service | Base URL |
|---|---|
| booking-service | http://localhost:8080/api/bookings |
| user-service | http://localhost:8081/api/users |
| provider-service | http://localhost:8082/api/providers |
| invoice-service | http://localhost:8083/api/invoices |
| invoice-service (discounts) | http://localhost:8083/api/discounts |
| invoice-service (invoice-discounts) | http://localhost:8083/api/invoice-discounts |
| calendar-service | http://localhost:8084/api/timeslots |

---

## Common Issues

### `database "bookingdb" does not exist`

**If using Docker:** the container may not be running. Start it:

```bash
docker compose up -d
```

**If you have a local PostgreSQL installed (Homebrew etc.):** it takes priority over Docker on port 5432. The Docker database is unreachable. Create the database in your local Postgres instead:

```bash
psql -h 127.0.0.1 -p 5432 -U <your-mac-username> -d postgres -c "CREATE DATABASE bookingdb;"
```

To check which Postgres is listening on port 5432:

```bash
lsof -iTCP:5432 -sTCP:LISTEN
# If you see a 'postgres' process (not com.docke), it's a local installation
```

### `Port already in use`

Another process is using one of the ports. Find and kill it:

```bash
lsof -i :<port>
kill -9 <PID>
```

### Two services on the same port

Each service must have a unique port in its `application.properties`:

```
booking-service  → server.port=8080
user-service     → server.port=8081
provider-service → server.port=8082
invoice-service  → server.port=8083
calendar-service → server.port=8084
```

### `Connection refused` on startup

Make sure the database container started successfully before running any service:

```bash
docker ps | grep booking-db
```

---

## Team

| Name | Service | GitHub |
|---|---|---|
| Paula Maged Michael | user-service | @PaulaMaged |
| Mahmoud Ayman | user-service | @TheSant0x |
| Ragaa Aly | user-service | @ragaaaly |
| Abdelrahim Abdelazim | invoice-service | @abdoo303 |
| Ali Hossam Eldeen | invoice-service | @Ali-Hosam |
| Ziad Sherif Ibrahim | invoice-service | @zeyadalaaser |
| Abdelrahman Atef Saad | provider-service | @abdlrhman08 |
| Yaseen Ashraf | provider-service | @zoatel |
| Mohamed Youssef | provider-service | @M-aboelsafa |
| Ahmed Mohamed El-Gohary | booking-service | @AhmedEl-Gohary |
| Rofaeil Samuel Fayez | booking-service | @Rofaeil478 |
| Ahmed Kamal | booking-service | @AhmedKamal18 |
| Ahmed Hussien Ali | calendar-service | @ahmedhussien107 |
| Ahmed Yasser Tawfik | calendar-service | @AhmedYTawfik |
