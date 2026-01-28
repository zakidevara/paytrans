# Redis-Based Idempotency Implementation

This document explains the Redis-based idempotency implementation for preventing duplicate payment transactions.

## Overview

Idempotency ensures that duplicate requests (with the same idempotency key) produce the same result without side effects. This is critical for payment systems where network failures or retries could cause duplicate charges.

## Architecture

```
Client Request (with idempotency key)
         ↓
   Controller
         ↓
IdempotencyService ← → Redis Cache
         ↓
TransactionService
         ↓
   Database
```

## Key Components

### 1. IdempotencyService
Located: `src/main/java/com/devara/paytrans/payment/transaction/IdempotencyService.java`

**Responsibilities:**
- Check Redis for existing results
- Prevent concurrent duplicate processing using locks
- Cache successful transaction results
- Return cached results for duplicate requests

**Flow:**
1. Check if result exists in Redis → return cached result
2. If no result, try to acquire processing lock
3. If lock acquired → execute operation and cache result
4. If lock not acquired → return 409 Conflict (duplicate processing)

### 2. RedisConfig
Located: `src/main/java/com/devara/paytrans/config/RedisConfig.java`

**Provides:**
- `ReactiveRedisTemplate<String, String>` for Redis operations
- `ObjectMapper` for JSON serialization/deserialization

### 3. TransactionController (Updated)
Located: `src/main/java/com/devara/paytrans/payment/transaction/TransactionController.java`

**Changes:**
- Added `idempotencyKey` field to `TransactionRequest`
- Wraps payment processing with `IdempotencyService.executeIdempotent()`
- Returns 409 Conflict for concurrent duplicate requests

## Redis Keys

### Result Key
- **Pattern:** `idempotency:{idempotency-key}`
- **Purpose:** Stores the cached transaction result (JSON)
- **TTL:** 24 hours

### Processing Lock Key
- **Pattern:** `idempotency:{idempotency-key}:processing`
- **Purpose:** Prevents concurrent processing of duplicate requests
- **TTL:** 5 minutes

## Request/Response Examples

### First Request (Success)
```bash
curl -X POST http://localhost:8080/api/v1/transactions \
  -H "Content-Type: application/json" \
  -d '{
    "idempotencyKey": "unique-key-123",
    "amount": 100.50,
    "currency": "USD"
  }'
```

**Response:** HTTP 201 Created
```json
{
  "id": 1,
  "amount": 100.50,
  "currency": "USD",
  "status": "COMPLETED",
  "createdAt": "2026-01-28T10:30:00Z"
}
```

### Duplicate Request (Cached Result)
```bash
# Same idempotency key
curl -X POST http://localhost:8080/api/v1/transactions \
  -H "Content-Type: application/json" \
  -d '{
    "idempotencyKey": "unique-key-123",
    "amount": 100.50,
    "currency": "USD"
  }'
```

**Response:** HTTP 201 Created (same transaction)
```json
{
  "id": 1,
  "amount": 100.50,
  "currency": "USD",
  "status": "COMPLETED",
  "createdAt": "2026-01-28T10:30:00Z"
}
```

### Concurrent Duplicate Request (Processing Lock)
```bash
# Sent while first request is still processing
curl -X POST http://localhost:8080/api/v1/transactions \
  -H "Content-Type: application/json" \
  -d '{
    "idempotencyKey": "unique-key-123",
    "amount": 100.50,
    "currency": "USD"
  }'
```

**Response:** HTTP 409 Conflict
```json
{
  "timestamp": "2026-01-28T10:30:05Z",
  "status": 409,
  "error": "Conflict",
  "message": "A request with the same idempotency key is currently being processed. Please try again later.",
  "path": "/api/v1/transactions"
}
```

## Configuration

### application.yml
```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
      timeout: 2000ms
```

### docker-compose.yml
```yaml
redis:
  image: redis:7-alpine
  container_name: paytrans-redis
  ports:
    - "6379:6379"
```

## Testing

### Manual Testing
Use the provided demo script:
```bash
chmod +x demo-idempotency.sh
./demo-idempotency.sh
```

### Inspecting Redis
```bash
# List all idempotency keys
docker exec -it paytrans-redis redis-cli KEYS 'idempotency:*'

# Get a specific result
docker exec -it paytrans-redis redis-cli GET 'idempotency:unique-key-123'

# Check processing lock
docker exec -it paytrans-redis redis-cli GET 'idempotency:unique-key-123:processing'

# Check TTL
docker exec -it paytrans-redis redis-cli TTL 'idempotency:unique-key-123'
```

## Best Practices

### Client Implementation
1. **Generate unique idempotency keys** (UUID recommended)
2. **Store the key** before making the request
3. **Retry with the same key** on network failures
4. **Handle 409 Conflict** by waiting and retrying

### Key Generation Example (Client-Side)
```javascript
// JavaScript/TypeScript
import { v4 as uuidv4 } from 'uuid';

const idempotencyKey = uuidv4();
localStorage.setItem('pending-transaction-key', idempotencyKey);

try {
  const response = await fetch('/api/v1/transactions', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      idempotencyKey,
      amount: 100.50,
      currency: 'USD'
    })
  });
  
  if (response.ok) {
    localStorage.removeItem('pending-transaction-key');
  } else if (response.status === 409) {
    // Wait and retry with same key
    await new Promise(resolve => setTimeout(resolve, 2000));
    // retry...
  }
} catch (error) {
  // Network error - retry with same key
  const savedKey = localStorage.getItem('pending-transaction-key');
  // retry with savedKey...
}
```

## Advantages

1. **Prevents Duplicate Charges:** Same request won't create multiple transactions
2. **Fast Response:** Cached results return immediately
3. **Concurrent Safety:** Processing locks prevent race conditions
4. **Automatic Cleanup:** TTL ensures old keys are removed
5. **Distributed System Support:** Works across multiple service instances

## Limitations

1. **Requires Redis:** Additional infrastructure dependency
2. **Memory Usage:** Stores transaction results in Redis (24h TTL)
3. **Same Parameters Required:** Different amounts with same key still return original result
4. **Network Dependency:** Redis availability affects idempotency

## Monitoring

Key metrics to monitor:
- Cache hit rate (idempotency keys found)
- 409 Conflict rate (concurrent duplicates)
- Redis memory usage
- Average response time (cached vs. fresh)

## Troubleshooting

### Issue: Duplicate transactions created
**Cause:** Idempotency key not provided or different keys used
**Solution:** Ensure clients always send idempotency keys

### Issue: 409 Conflict errors
**Cause:** Concurrent requests with same key
**Solution:** Client should wait and retry (result will be cached)

### Issue: Redis connection errors
**Cause:** Redis not running or network issues
**Solution:** Check Redis container and network connectivity
```bash
docker ps | grep redis
docker exec -it paytrans-redis redis-cli ping
```

## Running the System

1. **Start all services:**
   ```bash
   docker-compose up -d
   ```

2. **Build and run the application:**
   ```bash
   ./gradlew bootRun
   ```

3. **Run the demo:**
   ```bash
   ./demo-idempotency.sh
   ```

4. **Check logs:**
   ```bash
   # Application logs will show:
   # - "Checking idempotency for key: ..."
   # - "Found cached result for key: ..." (for duplicates)
   # - "Acquired processing lock for key: ..." (for new requests)
   ```
