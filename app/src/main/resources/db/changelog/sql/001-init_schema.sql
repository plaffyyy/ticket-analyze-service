-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Users table
CREATE TABLE IF NOT EXISTS users (
    id               UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    name             VARCHAR NOT NULL,
    email            VARCHAR NOT NULL UNIQUE,
    password_hash    VARCHAR(255) NOT NULL,
    avatar_url       TEXT,
    currency_default VARCHAR NOT NULL DEFAULT 'RUB',
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_users_email ON users (email);

-- Devices table
CREATE TABLE IF NOT EXISTS devices (
    id         UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    fcm_token  TEXT        NOT NULL,
    platform   VARCHAR(10) NOT NULL CHECK (platform IN ('ios', 'android')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, fcm_token)
);

CREATE INDEX IF NOT EXISTS idx_devices_user_id ON devices (user_id);

-- Groups table
CREATE TABLE IF NOT EXISTS groups (
    id          UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    name        VARCHAR(100) NOT NULL,
    owner_id    UUID        NOT NULL REFERENCES users (id),
    invite_code VARCHAR(20) NOT NULL UNIQUE,
    currency    VARCHAR(3)  NOT NULL DEFAULT 'RUB',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_groups_invite_code ON groups (invite_code);
CREATE INDEX IF NOT EXISTS idx_groups_owner_id ON groups (owner_id);

-- Group members table
CREATE TABLE IF NOT EXISTS group_members (
    group_id  UUID        NOT NULL REFERENCES groups (id) ON DELETE CASCADE,
    user_id   UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role      VARCHAR(10) NOT NULL DEFAULT 'member' CHECK (role IN ('owner', 'member')),
    joined_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (group_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_group_members_group_id ON group_members (group_id);
CREATE INDEX IF NOT EXISTS idx_group_members_user_id ON group_members (user_id);

-- Bills table
CREATE TABLE IF NOT EXISTS bills (
    id           UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    group_id     UUID         NOT NULL REFERENCES groups (id) ON DELETE CASCADE,
    created_by   UUID         NOT NULL REFERENCES users (id),
    title        VARCHAR(255) NOT NULL,
    total        NUMERIC(12, 2) NOT NULL DEFAULT 0,
    currency     VARCHAR(3)   NOT NULL DEFAULT 'RUB',
    receipt_url  TEXT,
    status       VARCHAR(20)  NOT NULL DEFAULT 'open' CHECK (status IN ('open', 'settled', 'processing_ocr')),
    spun_winner  UUID         REFERENCES users (id),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_bills_group_id ON bills (group_id);
CREATE INDEX IF NOT EXISTS idx_bills_created_by ON bills (created_by);
CREATE INDEX IF NOT EXISTS idx_bills_status ON bills (status);

-- Bill items table
CREATE TABLE IF NOT EXISTS bill_items (
    id       UUID           PRIMARY KEY DEFAULT uuid_generate_v4(),
    bill_id  UUID           NOT NULL REFERENCES bills (id) ON DELETE CASCADE,
    name     VARCHAR(255)   NOT NULL,
    price    NUMERIC(12, 2) NOT NULL,
    quantity INT            NOT NULL DEFAULT 1 CHECK (quantity > 0)
);

CREATE INDEX IF NOT EXISTS idx_bill_items_bill_id ON bill_items (bill_id);

-- Bill item splits table
CREATE TABLE IF NOT EXISTS bill_item_splits (
    id           UUID           PRIMARY KEY DEFAULT uuid_generate_v4(),
    item_id      UUID           NOT NULL REFERENCES bill_items (id) ON DELETE CASCADE,
    user_id      UUID           NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    share_amount NUMERIC(12, 2) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_bill_item_splits_item_id ON bill_item_splits (item_id);
CREATE INDEX IF NOT EXISTS idx_bill_item_splits_user_id ON bill_item_splits (user_id);

-- Transactions table
CREATE TABLE IF NOT EXISTS transactions (
    id          UUID           PRIMARY KEY DEFAULT uuid_generate_v4(),
    bill_id     UUID           NOT NULL REFERENCES bills (id) ON DELETE CASCADE,
    debtor_id   UUID           NOT NULL REFERENCES users (id),
    creditor_id UUID           NOT NULL REFERENCES users (id),
    amount      NUMERIC(12, 2) NOT NULL,
    status      VARCHAR(10)    NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'settled')),
    settled_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_transactions_bill_id ON transactions (bill_id);
CREATE INDEX IF NOT EXISTS idx_transactions_debtor_id ON transactions (debtor_id);
CREATE INDEX IF NOT EXISTS idx_transactions_creditor_id ON transactions (creditor_id);
CREATE INDEX IF NOT EXISTS idx_transactions_status ON transactions (status);

-- Notifications table
CREATE TABLE IF NOT EXISTS notifications (
    id           UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id      UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type         VARCHAR(50) NOT NULL,
    title        VARCHAR(255) NOT NULL,
    body         TEXT        NOT NULL,
    payload_json TEXT,
    is_read      BOOLEAN     NOT NULL DEFAULT false,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_notifications_user_id ON notifications (user_id);
CREATE INDEX IF NOT EXISTS idx_notifications_is_read ON notifications (is_read);

-- Refresh tokens table
CREATE TABLE IF NOT EXISTS refresh_tokens (
    id         UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_token_hash ON refresh_tokens (token_hash);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user_id ON refresh_tokens (user_id);
