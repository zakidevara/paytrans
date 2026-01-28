# PayTrans - High Performance Transit Payment Service

![Java](https://img.shields.io/badge/Java-21-orange?style=for-the-badge&logo=java)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.2-green?style=for-the-badge&logo=springboot)
![Gradle](https://img.shields.io/badge/Gradle-8.5-02303A?style=for-the-badge&logo=gradle)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15-blue?style=for-the-badge&logo=postgresql)

A reactive, non-blocking payment processing microservice designed for high-throughput system, demonstrating a modern **Event-Driven** and **Observable** architecture.

## Key Features

* **Reactive Core (WebFlux + R2DBC):** Uses a non-blocking event loop to handle thousands of concurrent connections with minimal resource usage.
* **Resilience (Rate Limiting):** Implements **Resilience4j** to reject excess traffic immediately (fail-fast), protecting the database from being overwhelmed during spikes.
* **Observability First (OpenTelemetry):** Distributed tracing is baked in. Custom spans track business logic latency, visualized via **Jaeger**.
* **Precision Arithmetic:** Uses `BigDecimal` for all monetary calculations to ensure zero floating-point errors.
* **Cloud-Native Build:** Dockerized infrastructure (Postgres, Jaeger) managed via Compose.

## Tech Stack

| Component | Technology |
|-----------|------------|
| **Language** | Java 21 |
| **Framework** | Spring Boot 4.0.2 (WebFlux) |
| **Database** | PostgreSQL 15 (Reactive R2DBC) |
| **Observability** | OpenTelemetry (OTLP) + Jaeger |
| **Resilience** | Resilience4j |
| **Build Tool** | Gradle 8.5 |

---

## Project Structure

```text
src/main/java/com/devara/paytrans
├── config/                  # Global Configuration
│   └── OpenTelemetryConfig.java  # Manual Tracer Setup
├── payment/
│   └── transaction/         # FEATURE: Transaction Processing
│       ├── TransactionController.java  # Rate Limiter & Input Validation
│       ├── TransactionService.java     # Business Logic & Tracing
│       ├── TransactionRepository.java  # R2DBC Interface
│       └── Transaction.java            # Domain Entity
└── PayTransApplication.java
```

## Getting Started

### Prerequisites
* Java 21 installed.
* Docker & Docker Compose running.

### 1. Start Infrastructure
We use Docker Compose to spin up the Database and the Tracing UI.

```bash
docker-compose up -d
```
- Postgres: Port 5432
- Jaeger UI: Port 16686 (http://localhost:16686)

### 2. Start the Spring Boot service using the Gradle wrapper:
```bash
./gradlew bootRun
```
- The application will start on Port 8080.
- Database tables will be auto-created via schema.sql on startup.

## Testing the API
### 1. Process a Transaction (Happy Path)
Send a standard payment request:
```bash
curl -i -X POST http://localhost:8080/api/v1/transactions \
  -H "Content-Type: application/json" \
  -d '{"amount": 5.50, "currency": "SGD"}'
```

### 2. View the Trace (Observability)
- Go to Jaeger: http://localhost:16686
- Select Service: paytrans-service
- Click Find Traces.
- We can see the full waterfall: HTTP POST → calculate_fees (Custom Span) → INSERT transactions.

### 3. Stress Test (Rate Limiting)
The system is configured to allow 10 requests per second. To simulate a traffic spike, run this loop in your terminal:

```bash
for i in {1..20}; do curl -o /dev/null -s -w "%{http_code}\n" -X POST http://localhost:8080/api/v1/transactions -H "Content-Type: application/json" -d '{"amount": 10, "currency": "USD"}'; done
```
Expected Result:
- First 10 requests: 201 (Success)
- Remaining requests: 429 (Too Many Requests) - The system successfully shed the load.
