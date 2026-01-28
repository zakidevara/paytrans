#!/bin/bash

# Simple test for idempotency

echo "Test 1: Creating transaction..."
RESPONSE1=$(curl -s -X POST http://localhost:8080/api/v1/transactions \
  -H "Content-Type: application/json" \
  -d '{"idempotencyKey": "simple-test-001", "amount": 100.50, "currency": "USD"}')

echo "$RESPONSE1" 
echo ""

sleep 1

echo "Test 2: Duplicate request (same key)..."
RESPONSE2=$(curl -s -X POST http://localhost:8080/api/v1/transactions \
  -H "Content-Type: application/json" \
  -d '{"idempotencyKey": "simple-test-001", "amount": 100.50, "currency": "USD"}')

echo "$RESPONSE2"
echo ""

echo "Checking Redis cache..."
docker exec -it paytrans-redis redis-cli GET 'idempotency:simple-test-001'
