# PayTrans - High Performance Transit Payment Service

![Java](https://img.shields.io/badge/Java-21-orange?style=for-the-badge&logo=java)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.2-green?style=for-the-badge&logo=springboot)
![Gradle](https://img.shields.io/badge/Gradle-8.5-02303A?style=for-the-badge&logo=gradle)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15-blue?style=for-the-badge&logo=postgresql)
![Redis](https://img.shields.io/badge/Redis-7-red?style=for-the-badge&logo=redis)

A reactive, non-blocking payment processing microservice designed for high-throughput systems, demonstrating modern **Event-Driven**, **Observable**, and **ACID-Compliant** architecture.

## Key Features

* **Reactive Core (WebFlux + R2DBC):** Uses a non-blocking event loop to handle thousands of concurrent connections with minimal resource usage.
* **Redis-Based Idempotency:** Prevents duplicate payment processing using distributed idempotency keys with 24-hour caching and processing locks.
* **ACID Transaction Guarantees:** Full demonstration of Atomicity, Consistency, Isolation, and Durability principles with optimistic locking and audit trails.
* **Resilience (Rate Limiting):** Implements **Resilience4j** to reject excess traffic immediately (fail-fast), protecting the database from being overwhelmed during spikes.
* **Observability First (OpenTelemetry):** Distributed tracing is baked in. Custom spans track business logic latency, visualized via **Jaeger**.
* **Event-Driven Architecture:** Publishes transaction events to Kafka for downstream processing and auditing.
* **Precision Arithmetic:** Uses `BigDecimal` for all monetary calculations to ensure zero floating-point errors.
* **Cloud-Native Build:** Dockerized infrastructure (Postgres, Jaeger, Kafka, Redis) managed via Compose.

## Tech Stack

| Component | Technology |
|-----------|------------|
| **Language** | Java 21 |
| **Framework** | Spring Boot 4.0.2 (WebFlux) |
| **Database** | PostgreSQL 15 (Reactive R2DBC) |
| **Cache** | Redis 7 (Reactive) |
| **Message Broker** | Apache Kafka |
| **Observability** | OpenTelemetry (OTLP) + Jaeger |
| **Resilience** | Resilience4j |
| **Build Tool** | Gradle 8.5 |

---

## Project Structure

```text
src/main/java/com/devara/paytrans
├── config/                  # Global Configuration
│   ├── OpenTelemetryConfig.java    # Manual Tracer Setup
│   ├── RedisConfig.java            # Redis Reactive Template
│   ├── KafkaConfig.java            # Kafka Producer/Consumer
│   └── JacksonSerializer.java      # JSON Serialization
├── payment/
│   └── transaction/         # FEATURE: Transaction Processing
│       ├── TransactionController.java   # Rate Limiter & Idempotency
│       ├── TransactionService.java      # Business Logic & Tracing
│       ├── IdempotencyService.java      # Redis-based Idempotency
│       ├── AcidTransactionService.java  # ACID Principles Demo
│       ├── AcidDemoController.java      # ACID REST Endpoints
│       ├── TransactionRepository.java   # R2DBC Interface
│       ├── AccountRepository.java       # Account R2DBC Interface
│       ├── TransactionLedgerRepository.java  # Audit Trail Repository
│       ├── Transaction.java             # Domain Entity (@Version)
│       ├── Account.java                 # Account Entity (Transfers)
│       ├── TransactionLedger.java       # Audit Trail Entity
│       ├── TransactionEvent.java        # Kafka Event
│       └── TransactionListener.java     # Kafka Consumer
└── PayTransApplication.java
```

## Getting Started

### Prerequisites
* Java 21 installed.
* Docker & Docker Compose running.

### 1. Start Infrastructure
We use Docker Compose to spin up the Database an
- Kafka: Port 9092
- Redis: Port 6379d the Tracing UI.

```bash
docker-compose up -d
```
- Postgres: Port 5432
- Jaeger UI: Port 16686 (http://localhost:16686)


### 1. Process a Transaction (with Idempotency)
Send a payment request with an idempotency key:
```bash
curl -i -X POST http://localhost:8080/api/v1/transactions \
  -H "Content-Type: application/json" \
  -d '{
    "idempotencyKey": "unique-key-12345",
    "amount": 5.50,
    "currency": "SGD"
  }'
```

### 2. Test Idempotency (Duplicate Request)
Send the same request to demonstrate that the duplicate request will not be processed twice.
```bash
curl -i -X POST http://localhost:8080/api/v1/transactions \
  -H "Content-Type: application/json" \
  -d '{
    "idempotencyKey": "unique-key-12345",
    "amount": 5.50,
    "currency": "SGD"
  }'
```

### 3. View the Trace (Observability)
- Go to Jaeger: http://localhost:16686
- Select Service: paytrans-service
- Click Find Traces.
- We can see the full waterfall: HTTP POST → fraud-detection → currency-conversion → calculate-fees → save-transaction → send-notification → publish-kafka-event.

### 4. Stress Test (Rate Limiting)
The system is configured to allow 10 requests per second. To simulate a traffic spike, run this loop in your terminal:

```bash
for i in {1..20}; do 
  curl -o /dev/null -s -w "%{http_code}\n" \
    -X POST http://localhost:8080/api/v1/transactions \
    -H "Content-Type: application/json" \
    -d "{\"idempotencyKey\": \"test-$i\", \"amount\": 10, \"currency\": \"USD\"}"; 
done
```
Expected Result:
- First 10 requests: 201 (Success)
- Remaining requests: 429 (Too Many Requests) - The system successfully shed the load.

---

## Idempotency Deep Dive

This service implements Redis-based idempotency to prevent duplicate payment processing. See [docs/IDEMPOTENCY.md](docs/IDEMPOTENCY.md) for detailed documentation.

**Quick Reference:**
- Clients send unique `idempotencyKey` with each request
- Duplicate requests (same key) return cached results
- Results cached for 24 hours in Redis
- Concurrent duplicates blocked with processing locks

---

## ACID Principles Demonstration

This service provides comprehensive ACID database transaction demonstrations. See [docs/ACID.md](docs/ACID.md) for detailed documentation.

### ACID Endpoints

| Principle | Endpoint | Description |
|-----------|----------|-------------|
| **Atomicity** | `POST /api/v1/acid/atomicity/transfer` | Atomic money transfer between accounts |
| **Atomicity** | `POST /api/v1/acid/atomicity/multistep` | Multi-step operation with rollback |
| **Consistency** | `POST /api/v1/acid/consistency` | Validated transaction with business rules |
| **Isolation** | `PUT /api/v1/acid/isolation/{id}` | Optimistic locking update |
| **Isolation** | `POST /api/v1/acid/isolation/serializable` | SERIALIZABLE isolation level |
| **Durability** | `POST /api/v1/acid/durability` | Transaction with audit trail |
| **Durability** | `GET /api/v1/acid/durability/audit/{id}` | Get complete audit history |

### Quick ACID Test

```bash
# Atomicity - Transfer $100 between accounts
curl -X POST http://localhost:8080/api/v1/acid/atomicity/transfer \
  -H "Content-Type: application/json" \
  -d '{"fromAccount": "ACC001", "toAccount": "ACC002", "amount": 100.00}'

# Consistency - Validated transaction with fee calculation
curl -X POST http://localhost:8080/api/v1/acid/consistency \
  -H "Content-Type: application/json" \
  -d '{"amount": 100.00, "currency": "USD"}'

# Isolation - Update with optimistic locking
curl -X PUT http://localhost:8080/api/v1/acid/isolation/1 \
  -H "Content-Type: application/json" \
  -d '{"newStatus": "REFUNDED"}'

# Durability - Get audit trail
curl http://localhost:8080/api/v1/acid/durability/audit/1
```

---

## Documentation

| Document | Description |
|----------|-------------|
| [docs/IDEMPOTENCY.md](docs/IDEMPOTENCY.md) | Redis-based idempotency implementation |
| [docs/ACID.md](docs/ACID.md) | ACID principles demonstration |
| [docs/TESTING.md](docs/TESTING.md) | Testing examples and scenarios |

---
