# Idempotency Testing - Quick Reference

## Test 1: Create a Transaction (First Time)
```bash
curl -X POST http://localhost:8080/api/v1/transactions \
  -H "Content-Type: application/json" \
  -d '{
    "idempotencyKey": "test-key-001",
    "amount": 100.50,
    "currency": "USD"
  }' | jq
```

Expected: HTTP 201, creates new transaction


## Test 2: Duplicate Request (Same Key)
```bash
curl -X POST http://localhost:8080/api/v1/transactions \
  -H "Content-Type: application/json" \
  -d '{
    "idempotencyKey": "test-key-001",
    "amount": 100.50,
    "currency": "USD"
  }' | jq
```

Expected: HTTP 201, returns SAME transaction (from cache)


## Test 3: Different Key (New Transaction)
```bash
curl -X POST http://localhost:8080/api/v1/transactions \
  -H "Content-Type: application/json" \
  -d '{
    "idempotencyKey": "test-key-002",
    "amount": 250.00,
    "currency": "EUR"
  }' | jq
```

Expected: HTTP 201, creates DIFFERENT transaction


## Check Redis Cache

### List all idempotency keys
```bash
docker exec -it paytrans-redis redis-cli KEYS 'idempotency:*'
```

### Get specific cached result
```bash
docker exec -it paytrans-redis redis-cli GET 'idempotency:test-key-001'
```

### Check TTL (time to live)
```bash
docker exec -it paytrans-redis redis-cli TTL 'idempotency:test-key-001'
```

### Check if processing lock exists
```bash
docker exec -it paytrans-redis redis-cli GET 'idempotency:test-key-001:processing'
```

### Flush all keys (clear cache)
```bash
docker exec -it paytrans-redis redis-cli FLUSHALL
```


## Concurrent Request Test

Open 3 terminals and run simultaneously:

**Terminal 1:**
```bash
curl -X POST http://localhost:8080/api/v1/transactions \
  -H "Content-Type: application/json" \
  -d '{"idempotencyKey": "concurrent-test", "amount": 50, "currency": "GBP"}'
```

**Terminal 2:**
```bash
curl -X POST http://localhost:8080/api/v1/transactions \
  -H "Content-Type: application/json" \
  -d '{"idempotencyKey": "concurrent-test", "amount": 50, "currency": "GBP"}'
```

**Terminal 3:**
```bash
curl -X POST http://localhost:8080/api/v1/transactions \
  -H "Content-Type: application/json" \
  -d '{"idempotencyKey": "concurrent-test", "amount": 50, "currency": "GBP"}'
```

Expected: One 201 (success), others 409 (conflict - already processing)


## Error Cases

### Missing idempotency key
```bash
curl -X POST http://localhost:8080/api/v1/transactions \
  -H "Content-Type: application/json" \
  -d '{
    "amount": 100.50,
    "currency": "USD"
  }'
```

Expected: HTTP 400 (validation error)


### Invalid amount
```bash
curl -X POST http://localhost:8080/api/v1/transactions \
  -H "Content-Type: application/json" \
  -d '{
    "idempotencyKey": "test-invalid",
    "amount": -50,
    "currency": "USD"
  }'
```

Expected: HTTP 400 (validation error)


## Monitoring

### Watch Redis logs
```bash
docker logs -f paytrans-redis
```

### Watch application logs
```bash
# If running with ./gradlew bootRun, logs appear in terminal
# Look for:
# - "Checking idempotency for key: ..."
# - "Found cached result for key: ..."
# - "Acquired processing lock for key: ..."
```

### Monitor Redis memory
```bash
docker exec -it paytrans-redis redis-cli INFO memory
```
