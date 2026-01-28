#!/bin/bash

# Idempotency Demonstration Script
# This script demonstrates Redis-based idempotency in the payment transaction service

echo "======================================"
echo "Payment Transaction Idempotency Demo"
echo "======================================"
echo ""

# Base URL
BASE_URL="http://localhost:8080/api/v1/transactions"

# Generate a unique idempotency key
IDEMPOTENCY_KEY="demo-$(uuidgen)"

echo "Using Idempotency Key: $IDEMPOTENCY_KEY"
echo ""

# Test 1: First Request (should create new transaction)
echo "📝 Test 1: First Request - Creating Transaction"
echo "--------------------------------------"
RESPONSE_1=$(curl -s -w "\nHTTP_STATUS:%{http_code}" -X POST "$BASE_URL" \
  -H "Content-Type: application/json" \
  -d "{
    \"idempotencyKey\": \"$IDEMPOTENCY_KEY\",
    \"amount\": 100.50,
    \"currency\": \"USD\"
  }")

HTTP_STATUS_1=$(echo "$RESPONSE_1" | grep "HTTP_STATUS:" | cut -d: -f2)
BODY_1=$(echo "$RESPONSE_1" | sed '$d')

echo "Status Code: $HTTP_STATUS_1"
echo "Response:"
echo "$BODY_1" | jq '.'
echo ""

# Extract transaction ID for comparison
TRANSACTION_ID=$(echo "$BODY_1" | jq -r '.id // empty')
echo "Transaction ID: $TRANSACTION_ID"
echo ""

# Wait a moment
sleep 1

# Test 2: Duplicate Request (should return cached result)
echo "📝 Test 2: Duplicate Request - Same Idempotency Key"
echo "--------------------------------------"
RESPONSE_2=$(curl -s -w "\nHTTP_STATUS:%{http_code}" -X POST "$BASE_URL" \
  -H "Content-Type: application/json" \
  -d "{
    \"idempotencyKey\": \"$IDEMPOTENCY_KEY\",
    \"amount\": 100.50,
    \"currency\": \"USD\"
  }")

HTTP_STATUS_2=$(echo "$RESPONSE_2" | grep "HTTP_STATUS:" | cut -d: -f2)
BODY_2=$(echo "$RESPONSE_2" | sed '$d')

echo "Status Code: $HTTP_STATUS_2"
echo "Response:"
echo "$BODY_2" | jq '.'
echo ""

TRANSACTION_ID_2=$(echo "$BODY_2" | jq -r '.id // empty')

# Verify idempotency
if [ "$TRANSACTION_ID" == "$TRANSACTION_ID_2" ]; then
    echo "✅ SUCCESS: Same transaction returned (ID: $TRANSACTION_ID)"
    echo "   Idempotency is working correctly!"
else
    echo "❌ FAILURE: Different transaction IDs"
    echo "   First: $TRANSACTION_ID"
    echo "   Second: $TRANSACTION_ID_2"
fi
echo ""

# Test 3: Different Idempotency Key (should create new transaction)
echo "📝 Test 3: New Idempotency Key - Creating New Transaction"
echo "--------------------------------------"
NEW_IDEMPOTENCY_KEY="demo-$(uuidgen)"
echo "New Idempotency Key: $NEW_IDEMPOTENCY_KEY"

RESPONSE_3=$(curl -s -w "\nHTTP_STATUS:%{http_code}" -X POST "$BASE_URL" \
  -H "Content-Type: application/json" \
  -d "{
    \"idempotencyKey\": \"$NEW_IDEMPOTENCY_KEY\",
    \"amount\": 200.00,
    \"currency\": \"EUR\"
  }")

HTTP_STATUS_3=$(echo "$RESPONSE_3" | grep "HTTP_STATUS:" | cut -d: -f2)
BODY_3=$(echo "$RESPONSE_3" | sed '$d')

echo "Status Code: $HTTP_STATUS_3"
echo "Response:"
echo "$BODY_3" | jq '.'
echo ""

TRANSACTION_ID_3=$(echo "$BODY_3" | jq -r '.id // empty')

if [ "$TRANSACTION_ID" != "$TRANSACTION_ID_3" ]; then
    echo "✅ SUCCESS: New transaction created (ID: $TRANSACTION_ID_3)"
    echo "   Different from first transaction (ID: $TRANSACTION_ID)"
else
    echo "❌ FAILURE: Same transaction ID returned for different idempotency key"
fi
echo ""

# Test 4: Concurrent Requests (demonstrate processing lock)
echo "📝 Test 4: Concurrent Requests - Testing Processing Lock"
echo "--------------------------------------"
CONCURRENT_KEY="concurrent-$(uuidgen)"
echo "Concurrent Idempotency Key: $CONCURRENT_KEY"
echo "Sending 3 requests simultaneously..."
echo ""

# Send 3 requests in parallel
(curl -s -w "\nHTTP_STATUS:%{http_code}" -X POST "$BASE_URL" \
  -H "Content-Type: application/json" \
  -d "{\"idempotencyKey\": \"$CONCURRENT_KEY\", \"amount\": 50.00, \"currency\": \"GBP\"}" > /tmp/response_a.txt) &

(curl -s -w "\nHTTP_STATUS:%{http_code}" -X POST "$BASE_URL" \
  -H "Content-Type: application/json" \
  -d "{\"idempotencyKey\": \"$CONCURRENT_KEY\", \"amount\": 50.00, \"currency\": \"GBP\"}" > /tmp/response_b.txt) &

(curl -s -w "\nHTTP_STATUS:%{http_code}" -X POST "$BASE_URL" \
  -H "Content-Type: application/json" \
  -d "{\"idempotencyKey\": \"$CONCURRENT_KEY\", \"amount\": 50.00, \"currency\": \"GBP\"}" > /tmp/response_c.txt) &

# Wait for all requests to complete
wait

echo "Response A:"
cat /tmp/response_a.txt | grep "HTTP_STATUS:" | cut -d: -f2
echo ""

echo "Response B:"
cat /tmp/response_b.txt | grep "HTTP_STATUS:" | cut -d: -f2
echo ""

echo "Response C:"
cat /tmp/response_c.txt | grep "HTTP_STATUS:" | cut -d: -f2
echo ""

echo "Expected: One 201 (Created) and two 409 (Conflict) status codes"
echo "(The order may vary depending on race conditions)"
echo ""

# Cleanup
rm -f /tmp/response_a.txt /tmp/response_b.txt /tmp/response_c.txt

echo "======================================"
echo "Demo Complete!"
echo "======================================"
echo ""
echo "Summary:"
echo "- Test 1: Created new transaction"
echo "- Test 2: Returned cached transaction (same idempotency key)"
echo "- Test 3: Created different transaction (new idempotency key)"
echo "- Test 4: Prevented concurrent duplicate processing"
echo ""
echo "Check Redis to see stored keys:"
echo "  docker exec -it paytrans-redis redis-cli KEYS 'idempotency:*'"
echo ""
echo "Inspect a specific key:"
echo "  docker exec -it paytrans-redis redis-cli GET 'idempotency:$IDEMPOTENCY_KEY'"
