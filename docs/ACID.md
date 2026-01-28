# ACID Principles Demonstration

This service demonstrates all four ACID database transaction principles using PostgreSQL and Spring WebFlux with R2DBC.

## What is ACID?

ACID is a set of properties that guarantee reliable processing of database transactions:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           ACID PRINCIPLES                                    │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  A - ATOMICITY      All operations succeed or all fail together             │
│                     "All or nothing" - no partial updates                    │
│                                                                              │
│  C - CONSISTENCY    Data always moves from one valid state to another       │
│                     Business rules and constraints are always enforced       │
│                                                                              │
│  I - ISOLATION      Concurrent transactions don't interfere                 │
│                     Each transaction sees a consistent view of data          │
│                                                                              │
│  D - DURABILITY     Once committed, data survives system failures           │
│                     Written to disk (WAL) before commit returns              │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

## API Endpoints

### 1. Atomicity - Money Transfer

**Endpoint:** `POST /api/v1/acid/atomicity/transfer`

Transfers money between two accounts atomically. Either both the debit AND credit succeed, or neither happens.

```bash
# First, create test accounts (via SQL or API)
# Then test the transfer:

curl -X POST http://localhost:8080/api/v1/acid/atomicity/transfer \
  -H "Content-Type: application/json" \
  -d '{
    "fromAccount": "ACC001",
    "toAccount": "ACC002",
    "amount": 100.00
  }'
```

**Success Response:**
```json
{
  "success": true,
  "message": "Transfer completed atomically",
  "transactionId": 1,
  "amount": 100.00,
  "principle": "ATOMICITY - All operations succeeded together"
}
```

**Failure Response (Insufficient Funds):**
```json
{
  "success": false,
  "error": "Insufficient funds. Available: 50.00, Required: 100.00",
  "principle": "ATOMICITY - Transfer rolled back due to insufficient funds"
}
```

### 2. Atomicity - Multi-Step with Failure Simulation

**Endpoint:** `POST /api/v1/acid/atomicity/multistep`

Demonstrates rollback when an intermediate step fails.

```bash
# Success case - all steps complete
curl -X POST http://localhost:8080/api/v1/acid/atomicity/multistep \
  -H "Content-Type: application/json" \
  -d '{
    "amount": 50.00,
    "simulateFailure": false
  }'

# Failure case - step 3 fails, ALL changes rolled back
curl -X POST http://localhost:8080/api/v1/acid/atomicity/multistep \
  -H "Content-Type: application/json" \
  -d '{
    "amount": 50.00,
    "simulateFailure": true
  }'
```

### 3. Consistency - Validated Transaction

**Endpoint:** `POST /api/v1/acid/consistency`

Creates a transaction with full business rule validation:
- Amount must be positive
- Currency must be valid (USD, EUR, GBP, JPY, IDR)
- Fee is calculated (2.5% + $0.30)
- Net amount must be positive after fees

```bash
# Valid transaction
curl -X POST http://localhost:8080/api/v1/acid/consistency \
  -H "Content-Type: application/json" \
  -d '{
    "amount": 100.00,
    "currency": "USD"
  }'

# Invalid currency - rejected
curl -X POST http://localhost:8080/api/v1/acid/consistency \
  -H "Content-Type: application/json" \
  -d '{
    "amount": 100.00,
    "currency": "INVALID"
  }'

# Amount too small (net would be negative after fees)
curl -X POST http://localhost:8080/api/v1/acid/consistency \
  -H "Content-Type: application/json" \
  -d '{
    "amount": 0.25,
    "currency": "USD"
  }'
```

**Success Response:**
```json
{
  "success": true,
  "transactionId": 5,
  "amount": 100.00,
  "fee": 2.80,
  "netAmount": 97.20,
  "currency": "USD",
  "principle": "CONSISTENCY - All business rules validated before commit"
}
```

### 4. Isolation - Optimistic Locking Update

**Endpoint:** `PUT /api/v1/acid/isolation/{id}`

Updates a transaction using optimistic locking to detect concurrent modifications.

```bash
curl -X PUT http://localhost:8080/api/v1/acid/isolation/1 \
  -H "Content-Type: application/json" \
  -d '{
    "newStatus": "REFUNDED"
  }'
```

**Success Response:**
```json
{
  "success": true,
  "transactionId": 1,
  "newStatus": "REFUNDED",
  "version": 2,
  "principle": "ISOLATION - Optimistic locking prevented concurrent modification"
}
```

**Conflict Response (concurrent modification detected):**
```json
{
  "success": false,
  "error": "Transaction was modified by another process. Please retry.",
  "principle": "ISOLATION - Concurrent modification detected, please retry"
}
```

### 5. Isolation - Serializable Transaction

**Endpoint:** `POST /api/v1/acid/isolation/serializable`

Creates a transaction with SERIALIZABLE isolation level (highest isolation).

```bash
curl -X POST http://localhost:8080/api/v1/acid/isolation/serializable \
  -H "Content-Type: application/json" \
  -d '{
    "amount": 500.00,
    "currency": "EUR"
  }'
```

### 6. Durability - Transaction with Audit Trail

**Endpoint:** `POST /api/v1/acid/durability`

Creates a transaction with a complete audit trail for recovery.

```bash
curl -X POST http://localhost:8080/api/v1/acid/durability \
  -H "Content-Type: application/json" \
  -d '{
    "amount": 250.00,
    "currency": "USD",
    "reason": "Monthly subscription payment"
  }'
```

**Response:**
```json
{
  "success": true,
  "transactionId": 10,
  "amount": 250.00,
  "principle": "DURABILITY - Transaction and audit trail persisted to WAL",
  "note": "Data will survive system crashes"
}
```

### 7. Durability - Get Audit Trail

**Endpoint:** `GET /api/v1/acid/durability/audit/{id}`

Retrieves the complete audit history for a transaction.

```bash
curl http://localhost:8080/api/v1/acid/durability/audit/10
```

**Response:**
```json
{
  "transactionId": 10,
  "auditTrail": "AUDIT TRAIL FOR TRANSACTION 10\n==================================================\n[2024-01-15T10:30:00Z] CREATE: N/A -> COMPLETED (Amount: 250.00) - Monthly subscription payment\n",
  "principle": "DURABILITY - Complete history preserved for recovery"
}
```

## Database Schema for ACID

The following schema supports ACID operations:

```sql
-- Main transaction table with CHECK constraints (Consistency)
CREATE TABLE IF NOT EXISTS transactions (
    id SERIAL PRIMARY KEY,
    amount DECIMAL(15,2) NOT NULL CHECK (amount > 0),
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL,
    fee DECIMAL(15,2) DEFAULT 0,
    net_amount DECIMAL(15,2),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    version INTEGER DEFAULT 0  -- Optimistic locking (Isolation)
);

-- Account table for atomic transfers (Atomicity)
CREATE TABLE IF NOT EXISTS accounts (
    id SERIAL PRIMARY KEY,
    account_number VARCHAR(50) NOT NULL UNIQUE,
    balance DECIMAL(15,2) NOT NULL CHECK (balance >= 0),
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    version INTEGER DEFAULT 0
);

-- Immutable audit trail (Durability)
CREATE TABLE IF NOT EXISTS transaction_ledger (
    id SERIAL PRIMARY KEY,
    transaction_id BIGINT NOT NULL,
    operation VARCHAR(20) NOT NULL,
    old_status VARCHAR(20),
    new_status VARCHAR(20),
    old_amount DECIMAL(15,2),
    new_amount DECIMAL(15,2),
    changed_by VARCHAR(100) NOT NULL,
    changed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reason TEXT,
    FOREIGN KEY (transaction_id) REFERENCES transactions(id)
);
```

## Visual Representation

### Atomicity Flow
```
┌─────────────────────────────────────────────────────────────────┐
│                    ATOMIC TRANSFER                               │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌─────────────┐        ┌─────────────┐        ┌─────────────┐  │
│  │   START     │───────►│   DEBIT     │───────►│   CREDIT    │  │
│  │ Transaction │        │  Account A  │        │  Account B  │  │
│  └─────────────┘        └─────────────┘        └─────────────┘  │
│        │                       │                      │         │
│        │                       ▼                      ▼         │
│        │               ┌─────────────────────────────────┐      │
│        │               │     SUCCESS? COMMIT ALL          │      │
│        │               └─────────────────────────────────┘      │
│        │                              │                         │
│        │                              ▼                         │
│        │               ┌─────────────────────────────────┐      │
│        └──────────────►│     FAILURE? ROLLBACK ALL        │      │
│                        └─────────────────────────────────┘      │
└─────────────────────────────────────────────────────────────────┘
```

### Isolation Levels
```
┌────────────────┬───────────────┬─────────────────┬───────────────┐
│ Isolation Level│ Dirty Reads   │ Non-Repeatable  │ Phantom Reads │
├────────────────┼───────────────┼─────────────────┼───────────────┤
│ READ_UNCOMMITTED│     YES       │      YES        │     YES       │
│ READ_COMMITTED │     NO        │      YES        │     YES       │
│ REPEATABLE_READ│     NO        │      NO         │     YES       │
│ SERIALIZABLE   │     NO        │      NO         │     NO        │
└────────────────┴───────────────┴─────────────────┴───────────────┘
```

## Testing ACID Properties

### Setup Test Data

```sql
-- Insert test accounts for transfer demonstrations
INSERT INTO accounts (account_number, balance, currency) VALUES 
  ('ACC001', 1000.00, 'USD'),
  ('ACC002', 500.00, 'USD'),
  ('ACC003', 0.00, 'USD');
```

### Test Atomicity

```bash
# Test 1: Successful transfer
curl -X POST http://localhost:8080/api/v1/acid/atomicity/transfer \
  -H "Content-Type: application/json" \
  -d '{"fromAccount": "ACC001", "toAccount": "ACC002", "amount": 100.00}'

# Verify: ACC001 should have 900, ACC002 should have 600

# Test 2: Failed transfer (insufficient funds)
curl -X POST http://localhost:8080/api/v1/acid/atomicity/transfer \
  -H "Content-Type: application/json" \
  -d '{"fromAccount": "ACC003", "toAccount": "ACC001", "amount": 50.00}'

# Verify: No accounts changed (rolled back)
```

### Test Isolation (Concurrent Updates)

Open two terminals and run simultaneously:

**Terminal 1:**
```bash
curl -X PUT http://localhost:8080/api/v1/acid/isolation/1 \
  -H "Content-Type: application/json" \
  -d '{"newStatus": "PROCESSING"}'
```

**Terminal 2:**
```bash
curl -X PUT http://localhost:8080/api/v1/acid/isolation/1 \
  -H "Content-Type: application/json" \
  -d '{"newStatus": "REFUNDED"}'
```

One will succeed, the other will get a conflict error - demonstrating optimistic locking.

## Implementation Details

### Key Files

| File | Purpose |
|------|---------|
| `AcidTransactionService.java` | Core ACID demonstration service |
| `AcidDemoController.java` | REST endpoints for testing |
| `Transaction.java` | Entity with `@Version` for optimistic locking |
| `Account.java` | Account entity for transfer demos |
| `TransactionLedger.java` | Audit trail for durability |
| `schema.sql` | Database schema with constraints |

### Spring Annotations Used

- `@Transactional` - Ensures atomicity
- `@Transactional(isolation = Isolation.SERIALIZABLE)` - Highest isolation
- `@Version` - Optimistic locking for isolation
- Database CHECK constraints - Consistency

## Best Practices

1. **Always use `@Transactional`** for operations that modify multiple tables
2. **Add `@Version` field** to entities that may be updated concurrently
3. **Use CHECK constraints** in the database for data validation
4. **Create audit tables** for critical financial transactions
5. **Handle `OptimisticLockingFailureException`** with retry logic
6. **Choose appropriate isolation level** based on requirements
