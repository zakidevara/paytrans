# Redis Idempotency Flow Diagrams

## Flow 1: First Request (Cache Miss)

```
Client                 Controller              IdempotencyService         Redis                 TransactionService       Database
  |                         |                           |                    |                           |                    |
  |  POST /transactions     |                           |                    |                           |                    |
  |  idempotencyKey: ABC    |                           |                    |                           |                    |
  |------------------------>|                           |                    |                           |                    |
  |                         |                           |                    |                           |                    |
  |                         | executeIdempotent(ABC)    |                    |                           |                    |
  |                         |-------------------------->|                    |                           |                    |
  |                         |                           |                    |                           |                    |
  |                         |                           | GET "idempotency:ABC"                           |                    |
  |                         |                           |------------------->|                           |                    |
  |                         |                           |                    |                           |                    |
  |                         |                           |    (null - miss)   |                           |                    |
  |                         |                           |<-------------------|                           |                    |
  |                         |                           |                    |                           |                    |
  |                         |                           | SETNX "idempotency:ABC:processing" = "true" (5m TTL)             |
  |                         |                           |------------------->|                           |                    |
  |                         |                           |                    |                           |                    |
  |                         |                           |    (true - acquired lock)                       |                    |
  |                         |                           |<-------------------|                           |                    |
  |                         |                           |                    |                           |                    |
  |                         |                           | processPayment()   |                           |                    |
  |                         |                           |---------------------------------------------->|                    |
  |                         |                           |                    |                           |                    |
  |                         |                           |                    |                           | INSERT transaction |
  |                         |                           |                    |                           |------------------->|
  |                         |                           |                    |                           |                    |
  |                         |                           |                    |                           |   Transaction(id=1)|
  |                         |                           |                    |                           |<-------------------|
  |                         |                           |                    |                           |                    |
  |                         |                           |   Transaction(id=1)|                           |                    |
  |                         |                           |<----------------------------------------------|                    |
  |                         |                           |                    |                           |                    |
  |                         |                           | SET "idempotency:ABC" = "{id:1,...}" (24h TTL)|                    |
  |                         |                           |------------------->|                           |                    |
  |                         |                           |                    |                           |                    |
  |                         |                           |    (OK)            |                           |                    |
  |                         |                           |<-------------------|                           |                    |
  |                         |                           |                    |                           |                    |
  |                         |                           | DEL "idempotency:ABC:processing"              |                    |
  |                         |                           |------------------->|                           |                    |
  |                         |                           |                    |                           |                    |
  |                         |   Transaction(id=1)       |                    |                           |                    |
  |                         |<--------------------------|                    |                           |                    |
  |                         |                           |                    |                           |                    |
  |    201 Created          |                           |                    |                           |                    |
  |    Transaction(id=1)    |                           |                    |                           |                    |
  |<------------------------|                           |                    |                           |                    |
```

---

## Flow 2: Duplicate Request (Cache Hit)

```
Client                 Controller              IdempotencyService         Redis                 TransactionService       Database
  |                         |                           |                    |                           |                    |
  |  POST /transactions     |                           |                    |                           |                    |
  |  idempotencyKey: ABC    |                           |                    |                           |                    |
  |------------------------>|                           |                    |                           |                    |
  |                         |                           |                    |                           |                    |
  |                         | executeIdempotent(ABC)    |                    |                           |                    |
  |                         |-------------------------->|                    |                           |                    |
  |                         |                           |                    |                           |                    |
  |                         |                           | GET "idempotency:ABC"                           |                    |
  |                         |                           |------------------->|                           |                    |
  |                         |                           |                    |                           |                    |
  |                         |                           | "{id:1,...}" (HIT!)|                           |                    |
  |                         |                           |<-------------------|                           |                    |
  |                         |                           |                    |                           |                    |
  |                         |                           | [deserialize JSON] |                           |                    |
  |                         |                           |                    |                           |                    |
  |                         |   Transaction(id=1)       |                    |                           |                    |
  |                         |<--------------------------|                    |                           |                    |
  |                         |                           |                    |                           |                    |
  |    201 Created          |                           |                    |                           |                    |
  |    Transaction(id=1)    |                           |                    |       NO DATABASE CALL     |                    |
  |    (CACHED)             |                           |                    |       NO SERVICE CALL      |                    |
  |<------------------------|                           |                    |                           |                    |
```

**Note:** TransactionService and Database are never called! Result returned from Redis cache.

---

## Flow 3: Concurrent Duplicate Request (Processing Lock)

```
Client A               Client B                Controller A            Controller B            IdempotencyService       Redis
  |                       |                          |                       |                           |                    |
  |  POST (key: XYZ)      |                          |                       |                           |                    |
  |---------------------> |                          |                       |                           |                    |
  |                       |  POST (key: XYZ)         |                       |                           |                    |
  |                       |--------------------------|---------------------> |                           |                    |
  |                       |                          |                       |                           |                    |
  |                       |                          | executeIdempotent(XYZ)|                           |                    |
  |                       |                          |---------------------> |                           |                    |
  |                       |                          |                       | executeIdempotent(XYZ)    |                    |
  |                       |                          |                       |-------------------------->|                    |
  |                       |                          |                       |                           |                    |
  |                       |                          |                       |                           | GET "idempotency:XYZ"
  |                       |                          |                       |                           |------------------->|
  |                       |                          |                       |                           |                    |
  |                       |                          |                       |                           |   (null)           |
  |                       |                          |                       |                           |<-------------------|
  |                       |                          |                       |                           |                    |
  |                       |                          |                       |                           | SETNX "...XYZ:processing" = "true"
  |                       |                          |                       |                           |------------------->|
  |                       |                          |                       |                           |                    |
  |                       |                          |                       |                           |  (true - Client A wins!)
  |                       |                          |                       |                           |<-------------------|
  |                       |                          |                       |                           |                    |
  |                       |                          |                       |                           | GET "idempotency:XYZ"
  |                       |                          |                       |                           |------------------->|
  |                       |                          |                       |                           |                    |
  |                       |                          |                       |                           |   (null)           |
  |                       |                          |                       |                           |<-------------------|
  |                       |                          |                       |                           |                    |
  |                       |                          |                       |                           | SETNX "...XYZ:processing" = "true"
  |                       |                          |                       |                           |------------------->|
  |                       |                          |                       |                           |                    |
  |                       |                          |                       |                           | (false - already locked!)
  |                       |                          |                       |                           |<-------------------|
  |                       |                          |                       |                           |                    |
  |                       |                          | [processing...]       | DuplicateRequestException |                    |
  |                       |                          |                       |<--------------------------|                    |
  |                       |                          |                       |                           |                    |
  |                       |                          |                       | 409 Conflict              |                    |
  |                       | 409 Conflict             |                       |                           |                    |
  |                       |<-------------------------|<----------------------|                           |                    |
  |                       |                          |                       |                           |                    |
  |                       |                          | [completes normally]  |                           |                    |
  |                       |                          | 201 Created           |                           |                    |
  | 201 Created           |                          |                       |                           |                    |
  |<----------------------|                          |                       |                           |                    |
```

**Note:** Client B receives 409 Conflict because Client A is still processing the same idempotency key.

---

## Redis Key Structure

### Result Key
```
Key:   idempotency:{idempotency-key}
Value: {"id":1,"amount":100.50,"currency":"USD","status":"COMPLETED","createdAt":"2026-01-28T10:30:00Z"}
TTL:   86400 seconds (24 hours)
```

### Processing Lock Key
```
Key:   idempotency:{idempotency-key}:processing
Value: "true"
TTL:   300 seconds (5 minutes)
```

---

## State Diagram

```
                                   [Request Received]
                                           |
                                           v
                                  Check Redis Cache
                                           |
                        +------------------+------------------+
                        |                                     |
                   Result Found?                         Result Found?
                    (Cache Hit)                          (Cache Miss)
                        |                                     |
                        v                                     v
              Return Cached Result              Try to Acquire Processing Lock
                   (201 OK)                                   |
                                            +-----------------+-----------------+
                                            |                                   |
                                    Lock Acquired?                        Lock Acquired?
                                       (true)                                (false)
                                            |                                   |
                                            v                                   v
                                  Execute Operation                Return 409 Conflict
                                            |                    (Another request processing)
                                            v
                                   Save to Database
                                            |
                                            v
                                  Cache Result in Redis
                                   (TTL: 24 hours)
                                            |
                                            v
                                 Release Processing Lock
                                            |
                                            v
                                    Return 201 Created
```
