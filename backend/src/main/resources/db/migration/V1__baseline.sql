-- CCMS baseline schema.
-- Every business table carries contractor_id (the tenant). Platform tables
-- (contractor, app_user) are not tenant-scoped.

CREATE TABLE contractor (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    name          VARCHAR(150) NOT NULL,
    owner_name    VARCHAR(150),
    phone         VARCHAR(30),
    email         VARCHAR(150),
    address       VARCHAR(500),
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',   -- ACTIVE | BLOCKED
    plan          VARCHAR(30)  NOT NULL DEFAULT 'STANDARD', -- SaaS subscription plan (future billing)
    blocked_reason VARCHAR(500),
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE app_user (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    username       VARCHAR(80)  NOT NULL UNIQUE,
    password_hash  VARCHAR(100) NOT NULL,
    full_name      VARCHAR(150),
    role           VARCHAR(20)  NOT NULL,                  -- ADMIN | CONTRACTOR
    contractor_id  BIGINT,
    enabled        BOOLEAN      NOT NULL DEFAULT TRUE,
    preferred_lang VARCHAR(5)   NOT NULL DEFAULT 'bn',
    last_login_at  DATETIME(6),
    created_at     DATETIME(6)  NOT NULL,
    updated_at     DATETIME(6)  NOT NULL,
    CONSTRAINT fk_user_contractor FOREIGN KEY (contractor_id) REFERENCES contractor (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE audit_log (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    contractor_id BIGINT,
    user_id       BIGINT,
    action        VARCHAR(60)  NOT NULL,
    entity_type   VARCHAR(60),
    entity_id     BIGINT,
    details       TEXT,
    created_at    DATETIME(6)  NOT NULL,
    INDEX idx_audit_contractor (contractor_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ---------------------------------------------------------------- hierarchy

CREATE TABLE client (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    contractor_id BIGINT       NOT NULL,
    name          VARCHAR(150) NOT NULL,
    code          VARCHAR(40),
    phone         VARCHAR(30),
    email         VARCHAR(150),
    address       VARCHAR(500),
    description   VARCHAR(1000),
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE | ARCHIVED
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    INDEX idx_client_tenant (contractor_id),
    CONSTRAINT fk_client_contractor FOREIGN KEY (contractor_id) REFERENCES contractor (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE site (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    contractor_id BIGINT       NOT NULL,
    client_id     BIGINT       NOT NULL,
    name          VARCHAR(150) NOT NULL,
    code          VARCHAR(40),
    address       VARCHAR(500),
    start_date    DATE,
    description   VARCHAR(1000),
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    INDEX idx_site_tenant (contractor_id, client_id),
    CONSTRAINT fk_site_client FOREIGN KEY (client_id) REFERENCES client (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE building (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    contractor_id BIGINT       NOT NULL,
    site_id       BIGINT       NOT NULL,
    name          VARCHAR(150) NOT NULL,
    code          VARCHAR(40),
    description   VARCHAR(1000),
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    INDEX idx_building_tenant (contractor_id, site_id),
    CONSTRAINT fk_building_site FOREIGN KEY (site_id) REFERENCES site (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE floor (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    contractor_id BIGINT       NOT NULL,
    building_id   BIGINT       NOT NULL,
    name          VARCHAR(150) NOT NULL,
    code          VARCHAR(40),
    level_no      INT,
    description   VARCHAR(1000),
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    INDEX idx_floor_tenant (contractor_id, building_id),
    CONSTRAINT fk_floor_building FOREIGN KEY (building_id) REFERENCES building (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE unit (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    contractor_id BIGINT       NOT NULL,
    floor_id      BIGINT       NOT NULL,
    name          VARCHAR(150) NOT NULL,
    code          VARCHAR(40),
    description   VARCHAR(1000),
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    INDEX idx_unit_tenant (contractor_id, floor_id),
    CONSTRAINT fk_unit_floor FOREIGN KEY (floor_id) REFERENCES floor (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ---------------------------------------------------------------- catalog

CREATE TABLE work_item (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    contractor_id BIGINT       NOT NULL,
    code          VARCHAR(40)  NOT NULL,
    name_bn       VARCHAR(150) NOT NULL,
    name_en       VARCHAR(150) NOT NULL,
    uom           VARCHAR(20)  NOT NULL,                   -- SFT | CFT | RFT | NOS | LS ...
    sort_order    INT          NOT NULL DEFAULT 0,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    UNIQUE KEY uk_work_item_code (contractor_id, code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ---------------------------------------------------------------- labour

CREATE TABLE labour (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    contractor_id BIGINT       NOT NULL,
    name          VARCHAR(150) NOT NULL,
    phone         VARCHAR(30),
    nid           VARCHAR(30),
    address       VARCHAR(500),
    skill_tier    VARCHAR(20)  NOT NULL,                   -- HELPER | MASON | JUNIOR | SENIOR | EXPERT
    joined_on     DATE,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    INDEX idx_labour_tenant (contractor_id, active)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE rate_card (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    contractor_id   BIGINT        NOT NULL,
    skill_tier      VARCHAR(20)   NOT NULL,
    site_id         BIGINT,                                -- NULL = applies to all sites
    daily_rate      DECIMAL(12, 2) NOT NULL,
    ot_hourly_rate  DECIMAL(12, 2) NOT NULL DEFAULT 0,
    skill_allowance DECIMAL(12, 2) NOT NULL DEFAULT 0,     -- per full day
    effective_from  DATE          NOT NULL,
    created_at      DATETIME(6)   NOT NULL,
    updated_at      DATETIME(6)   NOT NULL,
    INDEX idx_rate_lookup (contractor_id, skill_tier, site_id, effective_from),
    CONSTRAINT fk_rate_site FOREIGN KEY (site_id) REFERENCES site (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- One header per worker per day; the day is split across work items/locations.
CREATE TABLE attendance (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    contractor_id BIGINT       NOT NULL,
    labour_id     BIGINT       NOT NULL,
    work_date     DATE         NOT NULL,
    note          VARCHAR(500),
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    UNIQUE KEY uk_attendance_day (contractor_id, labour_id, work_date),
    INDEX idx_attendance_date (contractor_id, work_date),
    CONSTRAINT fk_attendance_labour FOREIGN KEY (labour_id) REFERENCES labour (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE attendance_allocation (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    contractor_id BIGINT        NOT NULL,
    attendance_id BIGINT        NOT NULL,
    site_id       BIGINT        NOT NULL,
    building_id   BIGINT,
    floor_id      BIGINT,
    unit_id       BIGINT,
    work_item_id  BIGINT        NOT NULL,
    day_fraction  DECIMAL(4, 2) NOT NULL,                  -- 0.50 = half day
    ot_hours      DECIMAL(5, 2) NOT NULL DEFAULT 0,        -- entered manually
    created_at    DATETIME(6)   NOT NULL,
    updated_at    DATETIME(6)   NOT NULL,
    INDEX idx_alloc_attendance (attendance_id),
    CONSTRAINT fk_alloc_attendance FOREIGN KEY (attendance_id) REFERENCES attendance (id) ON DELETE CASCADE,
    CONSTRAINT fk_alloc_site FOREIGN KEY (site_id) REFERENCES site (id),
    CONSTRAINT fk_alloc_work_item FOREIGN KEY (work_item_id) REFERENCES work_item (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE labour_payment (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    contractor_id BIGINT         NOT NULL,
    labour_id     BIGINT         NOT NULL,
    pay_date      DATE           NOT NULL,
    amount        DECIMAL(14, 2) NOT NULL,
    type          VARCHAR(20)    NOT NULL,                 -- WAGE | ADVANCE
    note          VARCHAR(500),
    created_at    DATETIME(6)    NOT NULL,
    updated_at    DATETIME(6)    NOT NULL,
    INDEX idx_payment_labour (contractor_id, labour_id, pay_date),
    CONSTRAINT fk_payment_labour FOREIGN KEY (labour_id) REFERENCES labour (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ---------------------------------------------------------------- cost ledger
-- Append-only. Corrections are posted as negative reversal rows, never UPDATEs.

CREATE TABLE cost_entry (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    contractor_id   BIGINT         NOT NULL,
    source_type     VARCHAR(20)    NOT NULL,               -- LABOUR | VENDOR | MATERIAL | OVERHEAD
    source_id       BIGINT,
    entry_date      DATE           NOT NULL,
    site_id         BIGINT         NOT NULL,
    building_id     BIGINT,
    floor_id        BIGINT,
    unit_id         BIGINT,
    work_item_id    BIGINT,
    labour_id       BIGINT,
    days            DECIMAL(6, 2)  NOT NULL DEFAULT 0,
    ot_hours        DECIMAL(6, 2)  NOT NULL DEFAULT 0,
    daily_rate      DECIMAL(12, 2),
    ot_hourly_rate  DECIMAL(12, 2),
    skill_allowance DECIMAL(12, 2),
    amount          DECIMAL(14, 2) NOT NULL,
    reversal_of     BIGINT,
    created_by      BIGINT,
    created_at      DATETIME(6)    NOT NULL,
    INDEX idx_cost_date (contractor_id, entry_date),
    INDEX idx_cost_item (contractor_id, work_item_id, entry_date),
    INDEX idx_cost_site (contractor_id, site_id, building_id, floor_id),
    INDEX idx_cost_labour (contractor_id, labour_id, entry_date),
    INDEX idx_cost_source (contractor_id, source_type, source_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
