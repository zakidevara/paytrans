-- src/main/resources/schema.sql

-- =====================================================
-- ACID PRINCIPLE DEMONSTRATION
-- =====================================================
-- Atomicity: Transactions ensure all-or-nothing operations
-- Consistency: Constraints enforce valid data states
-- Isolation: PostgreSQL default READ COMMITTED isolation
-- Durability: PostgreSQL WAL (Write-Ahead Logging) ensures persistence
-- =====================================================

-- Main transactions table with ACID-compliant constraints
CREATE TABLE IF NOT EXISTS transactions (
  id SERIAL PRIMARY KEY,
  amount DECIMAL(10, 2) NOT NULL CHECK (amount > 0),           -- Consistency: amount must be positive
  currency VARCHAR(3) NOT NULL CHECK (currency IN ('USD', 'EUR', 'GBP', 'JPY', 'IDR')), -- Consistency: valid currencies only
  status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'ROLLED_BACK')), -- Consistency: valid statuses
  fee DECIMAL(10, 2) NOT NULL DEFAULT 0 CHECK (fee >= 0),      -- Consistency: fee cannot be negative
  net_amount DECIMAL(10, 2) NOT NULL CHECK (net_amount > 0),   -- Consistency: net amount must be positive
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  version INTEGER NOT NULL DEFAULT 0                            -- Isolation: Optimistic locking for concurrency control
);

-- Account balance table for demonstrating atomic transfers
CREATE TABLE IF NOT EXISTS accounts (
  id SERIAL PRIMARY KEY,
  account_number VARCHAR(20) NOT NULL UNIQUE,
  balance DECIMAL(15, 2) NOT NULL DEFAULT 0 CHECK (balance >= 0), -- Consistency: no negative balance
  currency VARCHAR(3) NOT NULL DEFAULT 'USD',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  version INTEGER NOT NULL DEFAULT 0                              -- Isolation: Optimistic locking
);

-- Transaction ledger for audit trail (Durability demonstration)
CREATE TABLE IF NOT EXISTS transaction_ledger (
  id SERIAL PRIMARY KEY,
  transaction_id BIGINT NOT NULL,
  operation VARCHAR(20) NOT NULL,                                  -- CREATE, UPDATE, ROLLBACK
  old_status VARCHAR(20),
  new_status VARCHAR(20) NOT NULL,
  old_amount DECIMAL(10, 2),
  new_amount DECIMAL(10, 2) NOT NULL,
  changed_by VARCHAR(50) NOT NULL DEFAULT 'SYSTEM',
  changed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  reason TEXT
);

-- Index for faster lookups
CREATE INDEX IF NOT EXISTS idx_transactions_status ON transactions(status);
CREATE INDEX IF NOT EXISTS idx_transactions_created_at ON transactions(created_at);
CREATE INDEX IF NOT EXISTS idx_ledger_transaction_id ON transaction_ledger(transaction_id);
CREATE INDEX IF NOT EXISTS idx_accounts_account_number ON accounts(account_number);

-- Insert sample accounts for transfer demonstration
INSERT INTO accounts (account_number, balance, currency) 
VALUES 
  ('ACC-001', 10000.00, 'USD'),
  ('ACC-002', 5000.00, 'USD'),
  ('ACC-003', 7500.00, 'EUR')
ON CONFLICT (account_number) DO NOTHING;