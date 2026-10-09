-- Income (quotation -> work order -> client bills -> receipts) and
-- expenses (third parties, materials, purchases, rentals, subcontracts, site expenses).

-- Per-contractor running numbers for documents (Q-2026-0001, WO-..., BILL-...).
CREATE TABLE doc_sequence (
    contractor_id BIGINT      NOT NULL,
    doc_type      VARCHAR(20) NOT NULL,
    year_no       INT         NOT NULL,
    last_no       INT         NOT NULL,
    PRIMARY KEY (contractor_id, doc_type, year_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ---------------------------------------------------------------- income

CREATE TABLE quotation (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    contractor_id  BIGINT         NOT NULL,
    quote_no       VARCHAR(40)    NOT NULL,
    quote_date     DATE           NOT NULL,
    valid_until    DATE,
    client_id      BIGINT         NOT NULL,
    site_id        BIGINT,
    title          VARCHAR(200)   NOT NULL,
    status         VARCHAR(20)    NOT NULL DEFAULT 'DRAFT',   -- DRAFT | SENT | ACCEPTED | REJECTED
    subtotal       DECIMAL(16, 2) NOT NULL DEFAULT 0,
    discount       DECIMAL(16, 2) NOT NULL DEFAULT 0,
    total          DECIMAL(16, 2) NOT NULL DEFAULT 0,
    terms          TEXT,
    notes          VARCHAR(1000),
    created_at     DATETIME(6)    NOT NULL,
    updated_at     DATETIME(6)    NOT NULL,
    UNIQUE KEY uk_quote_no (contractor_id, quote_no),
    INDEX idx_quote_client (contractor_id, client_id),
    CONSTRAINT fk_quote_client FOREIGN KEY (client_id) REFERENCES client (id),
    CONSTRAINT fk_quote_site FOREIGN KEY (site_id) REFERENCES site (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE quotation_line (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    contractor_id  BIGINT         NOT NULL,
    quotation_id   BIGINT         NOT NULL,
    line_no        INT            NOT NULL,
    work_item_id   BIGINT,
    description    VARCHAR(500)   NOT NULL,
    uom            VARCHAR(20),
    quantity       DECIMAL(14, 3) NOT NULL,
    rate           DECIMAL(14, 2) NOT NULL,
    amount         DECIMAL(16, 2) NOT NULL,
    created_at     DATETIME(6)    NOT NULL,
    updated_at     DATETIME(6)    NOT NULL,
    INDEX idx_qline_quote (quotation_id),
    CONSTRAINT fk_qline_quote FOREIGN KEY (quotation_id) REFERENCES quotation (id) ON DELETE CASCADE,
    CONSTRAINT fk_qline_item FOREIGN KEY (work_item_id) REFERENCES work_item (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE work_order (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    contractor_id     BIGINT         NOT NULL,
    wo_no             VARCHAR(40)    NOT NULL,
    wo_date           DATE           NOT NULL,
    client_id         BIGINT         NOT NULL,
    site_id           BIGINT         NOT NULL,
    quotation_id      BIGINT,
    title             VARCHAR(200)   NOT NULL,
    client_ref        VARCHAR(100),                             -- client's own WO / LOI number
    start_date        DATE,
    end_date          DATE,
    retention_percent DECIMAL(5, 2)  NOT NULL DEFAULT 0,
    discount          DECIMAL(16, 2) NOT NULL DEFAULT 0,
    contract_value    DECIMAL(16, 2) NOT NULL DEFAULT 0,          -- lines - discount (or lump sum)
    status            VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE | COMPLETED | CANCELLED
    notes             VARCHAR(1000),
    created_at        DATETIME(6)    NOT NULL,
    updated_at        DATETIME(6)    NOT NULL,
    UNIQUE KEY uk_wo_no (contractor_id, wo_no),
    INDEX idx_wo_site (contractor_id, site_id),
    CONSTRAINT fk_wo_client FOREIGN KEY (client_id) REFERENCES client (id),
    CONSTRAINT fk_wo_site FOREIGN KEY (site_id) REFERENCES site (id),
    CONSTRAINT fk_wo_quote FOREIGN KEY (quotation_id) REFERENCES quotation (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE work_order_line (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    contractor_id  BIGINT         NOT NULL,
    work_order_id  BIGINT         NOT NULL,
    line_no        INT            NOT NULL,
    work_item_id   BIGINT,
    description    VARCHAR(500)   NOT NULL,
    uom            VARCHAR(20),
    quantity       DECIMAL(14, 3) NOT NULL,
    rate           DECIMAL(14, 2) NOT NULL,
    amount         DECIMAL(16, 2) NOT NULL,
    created_at     DATETIME(6)    NOT NULL,
    updated_at     DATETIME(6)    NOT NULL,
    INDEX idx_woline_wo (work_order_id),
    CONSTRAINT fk_woline_wo FOREIGN KEY (work_order_id) REFERENCES work_order (id) ON DELETE CASCADE,
    CONSTRAINT fk_woline_item FOREIGN KEY (work_item_id) REFERENCES work_item (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Planned billing schedule: milestones or periodic (weekly / monthly) instalments.
CREATE TABLE billing_milestone (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    contractor_id  BIGINT         NOT NULL,
    work_order_id  BIGINT         NOT NULL,
    seq            INT            NOT NULL,
    title          VARCHAR(200)   NOT NULL,
    due_date       DATE,
    amount         DECIMAL(16, 2) NOT NULL,
    bill_id        BIGINT,                                      -- set once billed
    created_at     DATETIME(6)    NOT NULL,
    updated_at     DATETIME(6)    NOT NULL,
    INDEX idx_milestone_wo (work_order_id),
    CONSTRAINT fk_milestone_wo FOREIGN KEY (work_order_id) REFERENCES work_order (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE client_bill (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    contractor_id    BIGINT         NOT NULL,
    bill_no          VARCHAR(40)    NOT NULL,
    bill_date        DATE           NOT NULL,
    due_date         DATE,
    work_order_id    BIGINT         NOT NULL,
    client_id        BIGINT         NOT NULL,
    site_id          BIGINT         NOT NULL,
    milestone_id     BIGINT,
    period_from      DATE,
    period_to        DATE,
    title            VARCHAR(200)   NOT NULL,
    gross_amount     DECIMAL(16, 2) NOT NULL DEFAULT 0,
    retention_amount DECIMAL(16, 2) NOT NULL DEFAULT 0,
    deduction_amount DECIMAL(16, 2) NOT NULL DEFAULT 0,          -- VAT / AIT / other
    deduction_note   VARCHAR(200),
    net_amount       DECIMAL(16, 2) NOT NULL DEFAULT 0,
    status           VARCHAR(20)    NOT NULL DEFAULT 'DRAFT',    -- DRAFT | SUBMITTED | CANCELLED
    notes            VARCHAR(1000),
    created_at       DATETIME(6)    NOT NULL,
    updated_at       DATETIME(6)    NOT NULL,
    UNIQUE KEY uk_bill_no (contractor_id, bill_no),
    INDEX idx_bill_wo (contractor_id, work_order_id),
    INDEX idx_bill_client (contractor_id, client_id, bill_date),
    CONSTRAINT fk_bill_wo FOREIGN KEY (work_order_id) REFERENCES work_order (id),
    CONSTRAINT fk_bill_client FOREIGN KEY (client_id) REFERENCES client (id),
    CONSTRAINT fk_bill_site FOREIGN KEY (site_id) REFERENCES site (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE client_bill_line (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    contractor_id      BIGINT         NOT NULL,
    bill_id            BIGINT         NOT NULL,
    line_no            INT            NOT NULL,
    work_order_line_id BIGINT,
    work_item_id       BIGINT,
    description        VARCHAR(500)   NOT NULL,
    uom                VARCHAR(20),
    quantity           DECIMAL(14, 3) NOT NULL,
    rate               DECIMAL(14, 2) NOT NULL,
    amount             DECIMAL(16, 2) NOT NULL,
    created_at         DATETIME(6)    NOT NULL,
    updated_at         DATETIME(6)    NOT NULL,
    INDEX idx_bline_bill (bill_id),
    INDEX idx_bline_woline (work_order_line_id),
    CONSTRAINT fk_bline_bill FOREIGN KEY (bill_id) REFERENCES client_bill (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Money received from a client. Without a bill it is an advance against the work order.
CREATE TABLE client_receipt (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    contractor_id  BIGINT         NOT NULL,
    receipt_date   DATE           NOT NULL,
    client_id      BIGINT         NOT NULL,
    work_order_id  BIGINT         NOT NULL,
    bill_id        BIGINT,
    amount         DECIMAL(16, 2) NOT NULL,
    method         VARCHAR(20)    NOT NULL,                     -- CASH | BANK | CHEQUE | MOBILE
    reference      VARCHAR(100),
    note           VARCHAR(500),
    created_at     DATETIME(6)    NOT NULL,
    updated_at     DATETIME(6)    NOT NULL,
    INDEX idx_receipt_wo (contractor_id, work_order_id),
    INDEX idx_receipt_date (contractor_id, receipt_date),
    CONSTRAINT fk_receipt_wo FOREIGN KEY (work_order_id) REFERENCES work_order (id),
    CONSTRAINT fk_receipt_bill FOREIGN KEY (bill_id) REFERENCES client_bill (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ---------------------------------------------------------------- expenses

-- Third parties: material suppliers, sub-contractors, equipment/shuttering owners ...
CREATE TABLE party (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    contractor_id  BIGINT       NOT NULL,
    name           VARCHAR(150) NOT NULL,
    type           VARCHAR(20)  NOT NULL,                       -- SUPPLIER | SUBCONTRACTOR | RENTAL | OTHER
    trade          VARCHAR(150),                                -- e.g. "Electrician", "Rod supplier"
    phone          VARCHAR(30),
    address        VARCHAR(500),
    note           VARCHAR(500),
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     DATETIME(6)  NOT NULL,
    updated_at     DATETIME(6)  NOT NULL,
    INDEX idx_party_tenant (contractor_id, type)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE material (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    contractor_id  BIGINT       NOT NULL,
    code           VARCHAR(40)  NOT NULL,
    name_bn        VARCHAR(150) NOT NULL,
    name_en        VARCHAR(150) NOT NULL,
    uom            VARCHAR(20)  NOT NULL,
    kind           VARCHAR(20)  NOT NULL,                       -- CONSUMABLE | RENTABLE
    sort_order     INT          NOT NULL DEFAULT 0,
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     DATETIME(6)  NOT NULL,
    updated_at     DATETIME(6)  NOT NULL,
    UNIQUE KEY uk_material_code (contractor_id, code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE purchase (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    contractor_id  BIGINT         NOT NULL,
    purchase_date  DATE           NOT NULL,
    party_id       BIGINT,                                      -- NULL = cash from the market
    invoice_no     VARCHAR(60),
    site_id        BIGINT         NOT NULL,
    building_id    BIGINT,
    floor_id       BIGINT,
    unit_id        BIGINT,
    work_item_id   BIGINT,
    total_amount   DECIMAL(16, 2) NOT NULL,
    paid_amount    DECIMAL(16, 2) NOT NULL DEFAULT 0,           -- paid on the spot (party purchases)
    note           VARCHAR(500),
    created_at     DATETIME(6)    NOT NULL,
    updated_at     DATETIME(6)    NOT NULL,
    INDEX idx_purchase_date (contractor_id, purchase_date),
    INDEX idx_purchase_party (contractor_id, party_id),
    CONSTRAINT fk_purchase_party FOREIGN KEY (party_id) REFERENCES party (id),
    CONSTRAINT fk_purchase_site FOREIGN KEY (site_id) REFERENCES site (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE purchase_line (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    contractor_id  BIGINT         NOT NULL,
    purchase_id    BIGINT         NOT NULL,
    material_id    BIGINT,
    description    VARCHAR(300),
    uom            VARCHAR(20),
    quantity       DECIMAL(14, 3) NOT NULL,
    rate           DECIMAL(14, 2) NOT NULL,
    amount         DECIMAL(16, 2) NOT NULL,
    created_at     DATETIME(6)    NOT NULL,
    updated_at     DATETIME(6)    NOT NULL,
    INDEX idx_pline_purchase (purchase_id),
    INDEX idx_pline_material (contractor_id, material_id),
    CONSTRAINT fk_pline_purchase FOREIGN KEY (purchase_id) REFERENCES purchase (id) ON DELETE CASCADE,
    CONSTRAINT fk_pline_material FOREIGN KEY (material_id) REFERENCES material (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Hired / borrowed items at a site (steel shutter, props, bamboo, pins ...), charged per day.
CREATE TABLE rental (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    contractor_id  BIGINT         NOT NULL,
    party_id       BIGINT,
    material_id    BIGINT,
    description    VARCHAR(300),
    uom            VARCHAR(20),
    site_id        BIGINT         NOT NULL,
    building_id    BIGINT,
    floor_id       BIGINT,
    unit_id        BIGINT,
    work_item_id   BIGINT,
    quantity       DECIMAL(14, 3) NOT NULL,                     -- received in total
    quantity_out   DECIMAL(14, 3) NOT NULL,                     -- still at the site
    rate_per_day   DECIMAL(14, 4) NOT NULL,                     -- per unit per day; 0 for free borrowing
    start_date     DATE           NOT NULL,
    charged_until  DATE,                                        -- rent posted to the ledger up to this day
    end_date       DATE,
    status         VARCHAR(20)    NOT NULL DEFAULT 'OUT',       -- OUT | RETURNED
    note           VARCHAR(500),
    created_at     DATETIME(6)    NOT NULL,
    updated_at     DATETIME(6)    NOT NULL,
    INDEX idx_rental_status (contractor_id, status),
    CONSTRAINT fk_rental_party FOREIGN KEY (party_id) REFERENCES party (id),
    CONSTRAINT fk_rental_material FOREIGN KEY (material_id) REFERENCES material (id),
    CONSTRAINT fk_rental_site FOREIGN KEY (site_id) REFERENCES site (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE rental_event (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    contractor_id  BIGINT         NOT NULL,
    rental_id      BIGINT         NOT NULL,
    event_date     DATE           NOT NULL,
    type           VARCHAR(20)    NOT NULL,                     -- CHARGE | RETURN
    quantity       DECIMAL(14, 3) NOT NULL DEFAULT 0,           -- returned quantity
    charge_from    DATE,
    charge_to      DATE,
    charged_qty    DECIMAL(14, 3) NOT NULL DEFAULT 0,
    days           INT            NOT NULL DEFAULT 0,
    amount         DECIMAL(16, 2) NOT NULL DEFAULT 0,
    note           VARCHAR(500),
    created_at     DATETIME(6)    NOT NULL,
    updated_at     DATETIME(6)    NOT NULL,
    INDEX idx_revent_rental (rental_id),
    CONSTRAINT fk_revent_rental FOREIGN KEY (rental_id) REFERENCES rental (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Work given to a third party (e.g. electrical, tiles fitting, piling on contract).
CREATE TABLE subcontract (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    contractor_id   BIGINT         NOT NULL,
    party_id        BIGINT         NOT NULL,
    title           VARCHAR(200)   NOT NULL,
    site_id         BIGINT         NOT NULL,
    building_id     BIGINT,
    floor_id        BIGINT,
    unit_id         BIGINT,
    work_item_id    BIGINT,
    uom             VARCHAR(20),
    quantity        DECIMAL(14, 3),
    rate            DECIMAL(14, 2),
    contract_amount DECIMAL(16, 2) NOT NULL,
    start_date      DATE,
    status          VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE | COMPLETED | CANCELLED
    note            VARCHAR(1000),
    created_at      DATETIME(6)    NOT NULL,
    updated_at      DATETIME(6)    NOT NULL,
    INDEX idx_subcontract_party (contractor_id, party_id),
    CONSTRAINT fk_subcontract_party FOREIGN KEY (party_id) REFERENCES party (id),
    CONSTRAINT fk_subcontract_site FOREIGN KEY (site_id) REFERENCES site (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Work done by the third party, as measured / claimed. Each bill is a cost.
CREATE TABLE subcontract_bill (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    contractor_id  BIGINT         NOT NULL,
    subcontract_id BIGINT         NOT NULL,
    bill_date      DATE           NOT NULL,
    quantity       DECIMAL(14, 3),
    amount         DECIMAL(16, 2) NOT NULL,
    note           VARCHAR(500),
    created_at     DATETIME(6)    NOT NULL,
    updated_at     DATETIME(6)    NOT NULL,
    INDEX idx_sbill_subcontract (subcontract_id),
    CONSTRAINT fk_sbill_subcontract FOREIGN KEY (subcontract_id) REFERENCES subcontract (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Transport, fuel, food, electricity, tools, small cash spends ...
CREATE TABLE site_expense (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    contractor_id  BIGINT         NOT NULL,
    expense_date   DATE           NOT NULL,
    category       VARCHAR(20)    NOT NULL,
    party_id       BIGINT,                                      -- set when bought on credit
    site_id        BIGINT         NOT NULL,
    building_id    BIGINT,
    floor_id       BIGINT,
    unit_id        BIGINT,
    work_item_id   BIGINT,
    amount         DECIMAL(16, 2) NOT NULL,
    description    VARCHAR(500),
    created_at     DATETIME(6)    NOT NULL,
    updated_at     DATETIME(6)    NOT NULL,
    INDEX idx_expense_date (contractor_id, expense_date),
    CONSTRAINT fk_expense_party FOREIGN KEY (party_id) REFERENCES party (id),
    CONSTRAINT fk_expense_site FOREIGN KEY (site_id) REFERENCES site (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE party_payment (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    contractor_id  BIGINT         NOT NULL,
    party_id       BIGINT         NOT NULL,
    pay_date       DATE           NOT NULL,
    amount         DECIMAL(16, 2) NOT NULL,
    method         VARCHAR(20)    NOT NULL,                     -- CASH | BANK | CHEQUE | MOBILE
    reference      VARCHAR(100),
    purchase_id    BIGINT,                                      -- set for "paid on the spot"
    note           VARCHAR(500),
    created_at     DATETIME(6)    NOT NULL,
    updated_at     DATETIME(6)    NOT NULL,
    INDEX idx_ppay_party (contractor_id, party_id, pay_date),
    CONSTRAINT fk_ppay_party FOREIGN KEY (party_id) REFERENCES party (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ---------------------------------------------------------------- ledger

-- Non-labour costs carry the third party, so payables and vendor-wise costs are plain sums.
ALTER TABLE cost_entry
    ADD COLUMN party_id BIGINT AFTER labour_id,
    ADD COLUMN material_id BIGINT AFTER party_id,
    ADD INDEX idx_cost_party (contractor_id, party_id);

-- Standard materials for contractors created before this version.
INSERT INTO material (contractor_id, code, name_bn, name_en, uom, kind, sort_order, active, created_at, updated_at)
SELECT c.id, m.code, m.name_bn, m.name_en, m.uom, m.kind, m.sort_order, TRUE, NOW(6), NOW(6)
FROM contractor c
CROSS JOIN (
    SELECT 'CEMENT' code, 'সিমেন্ট' name_bn, 'Cement' name_en, 'BAG' uom, 'CONSUMABLE' kind, 10 sort_order
    UNION ALL SELECT 'ROD', 'রড', 'Steel rod', 'KG', 'CONSUMABLE', 20
    UNION ALL SELECT 'SAND', 'বালু', 'Sand', 'CFT', 'CONSUMABLE', 30
    UNION ALL SELECT 'BRICK', 'ইট', 'Brick', 'NOS', 'CONSUMABLE', 40
    UNION ALL SELECT 'STONE_CHIPS', 'পাথর / খোয়া', 'Stone chips / khoa', 'CFT', 'CONSUMABLE', 50
    UNION ALL SELECT 'WOOD', 'কাঠ', 'Wood / timber', 'CFT', 'CONSUMABLE', 60
    UNION ALL SELECT 'BAMBOO', 'বাঁশ', 'Bamboo', 'NOS', 'RENTABLE', 70
    UNION ALL SELECT 'STEEL_SHUTTER', 'স্টিল সাটার', 'Steel shutter plate', 'NOS', 'RENTABLE', 80
    UNION ALL SELECT 'PROP', 'জ্যাক / প্রপ', 'Steel prop / jack', 'NOS', 'RENTABLE', 90
    UNION ALL SELECT 'PIN_CLAMP', 'পিন ও ক্ল্যাম্প', 'Pins & clamps', 'NOS', 'RENTABLE', 100
    UNION ALL SELECT 'MIXER', 'মিক্সার মেশিন', 'Concrete mixer', 'NOS', 'RENTABLE', 110
    UNION ALL SELECT 'VIBRATOR', 'ভাইব্রেটর', 'Vibrator', 'NOS', 'RENTABLE', 120
    UNION ALL SELECT 'CABLE', 'বৈদ্যুতিক তার', 'Electric cable', 'RFT', 'CONSUMABLE', 130
    UNION ALL SELECT 'BINDING_WIRE', 'বাইন্ডিং তার', 'Binding wire', 'KG', 'CONSUMABLE', 140
    UNION ALL SELECT 'NAIL', 'পেরেক', 'Nails', 'KG', 'CONSUMABLE', 150
) m;
