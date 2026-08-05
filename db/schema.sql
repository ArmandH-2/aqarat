/* ==========================================================================
   Aqarat - database schema
   SQL Server 2016 or later.

   Run this first, then seed.sql.
   Re-running is safe: every table is dropped and recreated.

   Note: USER and KEY are reserved words in SQL Server, which is why the
   tables are app_user and the settings column is setting_key.
   ========================================================================== */

IF DB_ID('Aqarat') IS NULL
    CREATE DATABASE Aqarat;
GO

USE Aqarat;
GO

/* --------------------------------------------------------------------------
   Drop in dependency order
   -------------------------------------------------------------------------- */

DROP TABLE IF EXISTS dbo.audit_log;
DROP TABLE IF EXISTS dbo.payment;
DROP TABLE IF EXISTS dbo.payment_schedule;
DROP TABLE IF EXISTS dbo.contract;
DROP TABLE IF EXISTS dbo.reservation;
DROP TABLE IF EXISTS dbo.viewing;
DROP TABLE IF EXISTS dbo.valuation;
DROP TABLE IF EXISTS dbo.property_photo;
DROP TABLE IF EXISTS dbo.property;
DROP TABLE IF EXISTS dbo.property_type;
DROP TABLE IF EXISTS dbo.district;
DROP TABLE IF EXISTS dbo.app_user;
DROP TABLE IF EXISTS dbo.system_setting;
GO

/* --------------------------------------------------------------------------
   Accounts
   -------------------------------------------------------------------------- */

CREATE TABLE dbo.app_user (
    id            INT IDENTITY(1,1) NOT NULL,
    email         VARCHAR(120)  NOT NULL,
    password_hash VARCHAR(100)  NOT NULL,
    full_name     VARCHAR(120)  NOT NULL,
    phone         VARCHAR(30)   NULL,
    role          VARCHAR(10)   NOT NULL,
    status        VARCHAR(10)   NOT NULL CONSTRAINT df_user_status  DEFAULT 'ACTIVE',
    created_at    DATETIME2(0)  NOT NULL CONSTRAINT df_user_created DEFAULT SYSUTCDATETIME(),

    CONSTRAINT pk_app_user        PRIMARY KEY (id),
    CONSTRAINT uq_app_user_email  UNIQUE (email),
    CONSTRAINT ck_app_user_role   CHECK (role   IN ('ADMIN','AGENT','CUSTOMER')),
    CONSTRAINT ck_app_user_status CHECK (status IN ('ACTIVE','INACTIVE'))
);

/* --------------------------------------------------------------------------
   Reference data
   -------------------------------------------------------------------------- */

CREATE TABLE dbo.district (
    id                INT IDENTITY(1,1) NOT NULL,
    name              VARCHAR(80)   NOT NULL,
    governorate       VARCHAR(60)   NOT NULL,
    avg_price_per_sqm DECIMAL(12,2) NOT NULL,

    CONSTRAINT pk_district       PRIMARY KEY (id),
    CONSTRAINT uq_district_name  UNIQUE (name),
    CONSTRAINT ck_district_price CHECK (avg_price_per_sqm > 0)
);

CREATE TABLE dbo.property_type (
    id   INT IDENTITY(1,1) NOT NULL,
    name VARCHAR(40) NOT NULL,

    CONSTRAINT pk_property_type      PRIMARY KEY (id),
    CONSTRAINT uq_property_type_name UNIQUE (name)
);

/* --------------------------------------------------------------------------
   Property

   Holds both the physical facts and the current offer. The two were
   deliberately not split - see docs/DECISIONS.md, decision 10.
   -------------------------------------------------------------------------- */

CREATE TABLE dbo.property (
    id               INT IDENTITY(1,1) NOT NULL,
    owner_id         INT           NOT NULL,
    agent_id         INT           NULL,
    district_id      INT           NOT NULL,
    property_type_id INT           NOT NULL,
    title            VARCHAR(150)  NOT NULL,
    description      VARCHAR(2000) NULL,
    address_line     VARCHAR(200)  NULL,
    area_sqm         DECIMAL(10,2) NOT NULL,
    bedrooms         INT           NOT NULL CONSTRAINT df_property_bedrooms  DEFAULT 0,
    bathrooms        INT           NOT NULL CONSTRAINT df_property_bathrooms DEFAULT 0,
    floor_number     INT           NULL,
    total_floors     INT           NULL,
    year_built       INT           NULL,
    has_parking      BIT           NOT NULL CONSTRAINT df_property_parking   DEFAULT 0,
    has_elevator     BIT           NOT NULL CONSTRAINT df_property_elevator  DEFAULT 0,
    has_balcony      BIT           NOT NULL CONSTRAINT df_property_balcony   DEFAULT 0,
    is_furnished     BIT           NOT NULL CONSTRAINT df_property_furnished DEFAULT 0,
    deal_type        VARCHAR(10)   NOT NULL,
    asking_price     DECIMAL(14,2) NOT NULL,
    min_term_months  INT           NULL,
    max_term_months  INT           NULL,
    status           VARCHAR(20)   NOT NULL CONSTRAINT df_property_status  DEFAULT 'DRAFT',
    review_note      VARCHAR(500)  NULL,
    submitted_at     DATETIME2(0)  NULL,
    published_at     DATETIME2(0)  NULL,
    closed_at        DATETIME2(0)  NULL,
    created_at       DATETIME2(0)  NOT NULL CONSTRAINT df_property_created DEFAULT SYSUTCDATETIME(),

    CONSTRAINT pk_property          PRIMARY KEY (id),
    CONSTRAINT fk_property_owner    FOREIGN KEY (owner_id)         REFERENCES dbo.app_user(id),
    CONSTRAINT fk_property_agent    FOREIGN KEY (agent_id)         REFERENCES dbo.app_user(id),
    CONSTRAINT fk_property_district FOREIGN KEY (district_id)      REFERENCES dbo.district(id),
    CONSTRAINT fk_property_type     FOREIGN KEY (property_type_id) REFERENCES dbo.property_type(id),

    CONSTRAINT ck_property_area   CHECK (area_sqm > 0),
    CONSTRAINT ck_property_price  CHECK (asking_price > 0),
    CONSTRAINT ck_property_deal   CHECK (deal_type IN ('SALE','RENT')),
    /* WITHDRAWAL_REQUESTED is the owner asking for a published listing to be
       taken down. An agent reviews the request before it becomes WITHDRAWN,
       which is why it needs a status of its own rather than the owner
       withdrawing the listing directly. */
    CONSTRAINT ck_property_status CHECK (status IN
        ('DRAFT','PENDING_REVIEW','NEEDS_INFO','REJECTED','AVAILABLE',
         'RESERVED','UNDER_CONTRACT','CLOSED','WITHDRAWAL_REQUESTED','WITHDRAWN')),

    /* An owner may set a minimum, a maximum, both, or neither - but a
       maximum below the minimum is nonsense, and neither applies to a sale. */
    CONSTRAINT ck_property_terms_order CHECK (
        min_term_months IS NULL OR max_term_months IS NULL
        OR max_term_months >= min_term_months),
    CONSTRAINT ck_property_terms_sale CHECK (
        deal_type = 'RENT'
        OR (min_term_months IS NULL AND max_term_months IS NULL))
);

CREATE TABLE dbo.property_photo (
    id          INT IDENTITY(1,1) NOT NULL,
    property_id INT          NOT NULL,
    file_path   VARCHAR(300) NOT NULL,
    is_primary  BIT          NOT NULL CONSTRAINT df_photo_primary DEFAULT 0,
    sort_order  INT          NOT NULL CONSTRAINT df_photo_sort    DEFAULT 0,

    CONSTRAINT pk_property_photo PRIMARY KEY (id),
    CONSTRAINT fk_photo_property FOREIGN KEY (property_id)
        REFERENCES dbo.property(id) ON DELETE CASCADE
);

/* --------------------------------------------------------------------------
   Valuation

   One row per estimate. History is kept so that an estimate can later be
   compared against what the property actually sold for.
   -------------------------------------------------------------------------- */

CREATE TABLE dbo.valuation (
    id              INT IDENTITY(1,1) NOT NULL,
    property_id     INT           NOT NULL,
    estimated_value DECIMAL(14,2) NOT NULL,
    lower_bound     DECIMAL(14,2) NOT NULL,
    upper_bound     DECIMAL(14,2) NOT NULL,
    price_per_sqm   DECIMAL(12,2) NOT NULL,
    flag            VARCHAR(15)   NOT NULL,
    breakdown       VARCHAR(MAX)  NULL,
    comparables     VARCHAR(MAX)  NULL,
    model_version   VARCHAR(20)   NOT NULL CONSTRAINT df_val_version DEFAULT 'v1',
    created_at      DATETIME2(0)  NOT NULL CONSTRAINT df_val_created DEFAULT SYSUTCDATETIME(),

    CONSTRAINT pk_valuation          PRIMARY KEY (id),
    CONSTRAINT fk_valuation_property FOREIGN KEY (property_id)
        REFERENCES dbo.property(id) ON DELETE CASCADE,
    CONSTRAINT ck_valuation_flag  CHECK (flag IN ('OK','ABOVE_MARKET','IMPLAUSIBLE')),
    CONSTRAINT ck_valuation_range CHECK (lower_bound <= estimated_value
                                     AND estimated_value <= upper_bound)
);

/* --------------------------------------------------------------------------
   Demand side
   -------------------------------------------------------------------------- */

CREATE TABLE dbo.viewing (
    id           INT IDENTITY(1,1) NOT NULL,
    property_id  INT          NOT NULL,
    client_id    INT          NOT NULL,
    agent_id     INT          NULL,
    scheduled_at DATETIME2(0) NOT NULL,
    status       VARCHAR(12)  NOT NULL CONSTRAINT df_viewing_status  DEFAULT 'REQUESTED',
    outcome_note VARCHAR(500) NULL,
    created_at   DATETIME2(0) NOT NULL CONSTRAINT df_viewing_created DEFAULT SYSUTCDATETIME(),

    CONSTRAINT pk_viewing          PRIMARY KEY (id),
    CONSTRAINT fk_viewing_property FOREIGN KEY (property_id) REFERENCES dbo.property(id),
    CONSTRAINT fk_viewing_client   FOREIGN KEY (client_id)   REFERENCES dbo.app_user(id),
    CONSTRAINT fk_viewing_agent    FOREIGN KEY (agent_id)    REFERENCES dbo.app_user(id),
    CONSTRAINT ck_viewing_status   CHECK (status IN
        ('REQUESTED','CONFIRMED','COMPLETED','CANCELLED','NO_SHOW'))
);

CREATE TABLE dbo.reservation (
    id             INT IDENTITY(1,1) NOT NULL,
    property_id    INT           NOT NULL,
    client_id      INT           NOT NULL,
    agent_id       INT           NULL,
    deposit_amount DECIMAL(14,2) NOT NULL,
    reserved_at    DATETIME2(0)  NOT NULL CONSTRAINT df_res_reserved DEFAULT SYSUTCDATETIME(),
    expires_at     DATETIME2(0)  NOT NULL,
    status         VARCHAR(12)   NOT NULL CONSTRAINT df_res_status   DEFAULT 'ACTIVE',

    CONSTRAINT pk_reservation  PRIMARY KEY (id),
    CONSTRAINT fk_res_property FOREIGN KEY (property_id) REFERENCES dbo.property(id),
    CONSTRAINT fk_res_client   FOREIGN KEY (client_id)   REFERENCES dbo.app_user(id),
    CONSTRAINT fk_res_agent    FOREIGN KEY (agent_id)    REFERENCES dbo.app_user(id),
    CONSTRAINT ck_res_status   CHECK (status IN ('ACTIVE','CONVERTED','LAPSED','CANCELLED')),
    CONSTRAINT ck_res_deposit  CHECK (deposit_amount > 0),
    CONSTRAINT ck_res_expiry   CHECK (expires_at > reserved_at)
);

/* --------------------------------------------------------------------------
   Contract and money
   -------------------------------------------------------------------------- */

CREATE TABLE dbo.contract (
    id                INT IDENTITY(1,1) NOT NULL,
    property_id       INT           NOT NULL,
    owner_id          INT           NOT NULL,
    client_id         INT           NOT NULL,
    agent_id          INT           NOT NULL,
    contract_type     VARCHAR(10)   NOT NULL,
    total_amount      DECIMAL(14,2) NOT NULL,
    monthly_rent      DECIMAL(14,2) NULL,
    start_date        DATE          NOT NULL,
    end_date          DATE          NULL,
    term_months       INT           NULL,
    payment_frequency VARCHAR(12)   NOT NULL,
    installment_count INT           NOT NULL CONSTRAINT df_contract_inst    DEFAULT 1,
    commission_rate   DECIMAL(5,2)  NOT NULL,
    commission_amount DECIMAL(14,2) NULL,
    status            VARCHAR(12)   NOT NULL CONSTRAINT df_contract_status  DEFAULT 'DRAFT',
    created_at        DATETIME2(0)  NOT NULL CONSTRAINT df_contract_created DEFAULT SYSUTCDATETIME(),
    activated_at      DATETIME2(0)  NULL,
    closed_at         DATETIME2(0)  NULL,

    CONSTRAINT pk_contract          PRIMARY KEY (id),
    CONSTRAINT fk_contract_property FOREIGN KEY (property_id) REFERENCES dbo.property(id),
    CONSTRAINT fk_contract_owner    FOREIGN KEY (owner_id)    REFERENCES dbo.app_user(id),
    CONSTRAINT fk_contract_client   FOREIGN KEY (client_id)   REFERENCES dbo.app_user(id),
    CONSTRAINT fk_contract_agent    FOREIGN KEY (agent_id)    REFERENCES dbo.app_user(id),

    CONSTRAINT ck_contract_type   CHECK (contract_type IN ('SALE','LEASE')),
    CONSTRAINT ck_contract_status CHECK (status IN
        ('DRAFT','ACTIVE','COMPLETED','TERMINATED','EXPIRED')),
    CONSTRAINT ck_contract_freq   CHECK (payment_frequency IN
        ('ONE_OFF','MONTHLY','QUARTERLY','ANNUAL','INSTALLMENT')),
    CONSTRAINT ck_contract_amount CHECK (total_amount > 0),
    CONSTRAINT ck_contract_dates  CHECK (end_date IS NULL OR end_date > start_date),

    /* A lease has to say how much per month, for how long, and until when.
       A sale has none of those. */
    CONSTRAINT ck_contract_lease CHECK (
        contract_type = 'SALE'
        OR (monthly_rent IS NOT NULL AND end_date IS NOT NULL AND term_months IS NOT NULL))
);

CREATE TABLE dbo.payment_schedule (
    id             INT IDENTITY(1,1) NOT NULL,
    contract_id    INT           NOT NULL,
    installment_no INT           NOT NULL,
    due_date       DATE          NOT NULL,
    amount_due     DECIMAL(14,2) NOT NULL,
    amount_paid    DECIMAL(14,2) NOT NULL CONSTRAINT df_sched_paid   DEFAULT 0,
    status         VARCHAR(15)   NOT NULL CONSTRAINT df_sched_status DEFAULT 'PENDING',

    CONSTRAINT pk_payment_schedule  PRIMARY KEY (id),
    CONSTRAINT fk_sched_contract    FOREIGN KEY (contract_id)
        REFERENCES dbo.contract(id) ON DELETE CASCADE,
    CONSTRAINT uq_sched_installment UNIQUE (contract_id, installment_no),
    CONSTRAINT ck_sched_status CHECK (status IN
        ('PENDING','PARTIALLY_PAID','PAID','OVERDUE')),
    CONSTRAINT ck_sched_amount CHECK (amount_due > 0 AND amount_paid >= 0)
);

CREATE TABLE dbo.payment (
    id             INT IDENTITY(1,1) NOT NULL,
    schedule_id    INT           NULL,
    reservation_id INT           NULL,
    amount         DECIMAL(14,2) NOT NULL,
    paid_at        DATETIME2(0)  NOT NULL,
    method         VARCHAR(15)   NOT NULL,
    reference      VARCHAR(80)   NULL,
    proof_path     VARCHAR(300)  NULL,
    declared_by    INT           NOT NULL,
    confirmed_by   INT           NULL,
    status         VARCHAR(10)   NOT NULL CONSTRAINT df_payment_status  DEFAULT 'DECLARED',
    created_at     DATETIME2(0)  NOT NULL CONSTRAINT df_payment_created DEFAULT SYSUTCDATETIME(),

    CONSTRAINT pk_payment             PRIMARY KEY (id),
    CONSTRAINT fk_payment_schedule    FOREIGN KEY (schedule_id)    REFERENCES dbo.payment_schedule(id),
    CONSTRAINT fk_payment_reservation FOREIGN KEY (reservation_id) REFERENCES dbo.reservation(id),
    CONSTRAINT fk_payment_declared    FOREIGN KEY (declared_by)    REFERENCES dbo.app_user(id),
    CONSTRAINT fk_payment_confirmed   FOREIGN KEY (confirmed_by)   REFERENCES dbo.app_user(id),

    CONSTRAINT ck_payment_status CHECK (status IN ('DECLARED','CONFIRMED','REJECTED')),
    CONSTRAINT ck_payment_method CHECK (method IN ('CASH','BANK_TRANSFER','CHEQUE')),
    CONSTRAINT ck_payment_amount CHECK (amount > 0),

    /* A payment settles either an installment or a reservation deposit.
       Never both, never neither. */
    CONSTRAINT ck_payment_target CHECK (
        (schedule_id IS NULL     AND reservation_id IS NOT NULL)
     OR (schedule_id IS NOT NULL AND reservation_id IS NULL))
);

/* --------------------------------------------------------------------------
   Audit
   -------------------------------------------------------------------------- */

CREATE TABLE dbo.audit_log (
    id          BIGINT IDENTITY(1,1) NOT NULL,
    user_id     INT          NULL,
    entity_type VARCHAR(40)  NOT NULL,
    entity_id   INT          NULL,
    action      VARCHAR(30)  NOT NULL,
    old_value   VARCHAR(MAX) NULL,
    new_value   VARCHAR(MAX) NULL,
    created_at  DATETIME2(0) NOT NULL CONSTRAINT df_audit_created DEFAULT SYSUTCDATETIME(),

    CONSTRAINT pk_audit_log  PRIMARY KEY (id),
    CONSTRAINT fk_audit_user FOREIGN KEY (user_id) REFERENCES dbo.app_user(id)
);

CREATE TABLE dbo.system_setting (
    setting_key VARCHAR(60)  NOT NULL,
    value       VARCHAR(200) NOT NULL,
    description VARCHAR(300) NULL,

    CONSTRAINT pk_system_setting PRIMARY KEY (setting_key)
);
GO

/* --------------------------------------------------------------------------
   Integrity rules enforced by the database

   These two are filtered unique indexes. The service layer checks the same
   rules to produce readable error messages, but these are the backstop and
   they cannot be bypassed.
   -------------------------------------------------------------------------- */

/* One active reservation per property. */
CREATE UNIQUE INDEX ux_reservation_active
    ON dbo.reservation(property_id)
    WHERE status = 'ACTIVE';

/* One agent cannot have two confirmed viewings at the same moment. */
CREATE UNIQUE INDEX ux_viewing_agent_slot
    ON dbo.viewing(agent_id, scheduled_at)
    WHERE status = 'CONFIRMED';
GO

/* --------------------------------------------------------------------------
   Performance indexes
   -------------------------------------------------------------------------- */

CREATE INDEX ix_property_status     ON dbo.property(status);
CREATE INDEX ix_property_search     ON dbo.property(district_id, property_type_id, deal_type);
CREATE INDEX ix_property_owner      ON dbo.property(owner_id);
CREATE INDEX ix_property_agent      ON dbo.property(agent_id);
CREATE INDEX ix_valuation_property  ON dbo.valuation(property_id, created_at DESC);
CREATE INDEX ix_viewing_client      ON dbo.viewing(client_id);
CREATE INDEX ix_reservation_client  ON dbo.reservation(client_id);
CREATE INDEX ix_contract_client     ON dbo.contract(client_id);
CREATE INDEX ix_contract_agent      ON dbo.contract(agent_id);
CREATE INDEX ix_contract_property   ON dbo.contract(property_id);
CREATE INDEX ix_schedule_due        ON dbo.payment_schedule(due_date, status);
CREATE INDEX ix_payment_schedule    ON dbo.payment(schedule_id);
CREATE INDEX ix_audit_entity        ON dbo.audit_log(entity_type, entity_id);
GO

/* --------------------------------------------------------------------------
   Settings

   Nothing that a business person might want to change belongs in Java source.
   -------------------------------------------------------------------------- */

INSERT INTO dbo.system_setting (setting_key, value, description) VALUES
    ('commission_rate_percent',           '2.50',
        'Agency commission, applied to sale price or to total lease value'),
    ('reservation_days',                  '14',
        'How long a reservation holds a property before it lapses'),
    ('payment_grace_days',                '5',
        'Days after the due date before an installment counts as overdue'),
    ('above_market_threshold_percent',    '20',
        'Distance above the estimate range that flags a listing ABOVE_MARKET'),
    ('implausible_threshold_percent',     '60',
        'Distance from the estimate, either direction, that flags IMPLAUSIBLE'),
    ('comparable_area_tolerance_percent', '30',
        'Area window used when selecting comparable properties'),
    ('comparable_min_count',              '5',
        'Minimum comparables required before the estimate is considered reliable');
GO

PRINT 'Aqarat schema created. Run seed.sql next.';
GO
