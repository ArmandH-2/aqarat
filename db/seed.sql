/* ==========================================================================
   Aqarat - seed data
   Run after schema.sql. Re-running is safe: it clears everything first.

   Generates a synthetic but realistic Lebanese dataset:
     51  districts with market price per m2
     8   property types
     255 accounts (1 admin, 4 agents, 250 customers)
     2000 properties across the full status range
     valuations for everything awaiting review
     viewings, reservations, contracts, schedules and payments

   The data is synthetic. Prices are generated from district-level price per
   m2 with adjustments for type, size, age, floor and features, plus noise.
   This is stated in the README - it is not presented as real market data.

   IMPORTANT: for a RENT listing, asking_price is the MONTHLY rent.
   For a SALE listing it is the full sale price.

   Every account has the password:  Password123!
   ========================================================================== */

USE Aqarat;
GO

/* --------------------------------------------------------------------------
   Clear
   -------------------------------------------------------------------------- */

DELETE FROM dbo.audit_log;
DELETE FROM dbo.payment;
DELETE FROM dbo.payment_schedule;
DELETE FROM dbo.contract;
DELETE FROM dbo.reservation;
DELETE FROM dbo.viewing;
DELETE FROM dbo.valuation;
DELETE FROM dbo.property_photo;
DELETE FROM dbo.property;
DELETE FROM dbo.property_type;
DELETE FROM dbo.district;
DELETE FROM dbo.app_user;
GO

DBCC CHECKIDENT ('dbo.app_user',      RESEED, 0) WITH NO_INFOMSGS;
DBCC CHECKIDENT ('dbo.district',      RESEED, 0) WITH NO_INFOMSGS;
DBCC CHECKIDENT ('dbo.property_type', RESEED, 0) WITH NO_INFOMSGS;
DBCC CHECKIDENT ('dbo.property',      RESEED, 0) WITH NO_INFOMSGS;
DBCC CHECKIDENT ('dbo.valuation',     RESEED, 0) WITH NO_INFOMSGS;
DBCC CHECKIDENT ('dbo.viewing',       RESEED, 0) WITH NO_INFOMSGS;
DBCC CHECKIDENT ('dbo.reservation',   RESEED, 0) WITH NO_INFOMSGS;
DBCC CHECKIDENT ('dbo.contract',      RESEED, 0) WITH NO_INFOMSGS;
GO

/* --------------------------------------------------------------------------
   Districts

   Price per m2 in USD. Indicative figures for a mid-2020s Lebanese market,
   used to generate the dataset and as the estimator's starting point.
   -------------------------------------------------------------------------- */

INSERT INTO dbo.district (name, governorate, avg_price_per_sqm) VALUES
    ('Downtown',          'Beirut',        4200),
    ('Achrafieh',         'Beirut',        3100),
    ('Verdun',            'Beirut',        2900),
    ('Clemenceau',        'Beirut',        2750),
    ('Ras Beirut',        'Beirut',        2700),
    ('Gemmayzeh',         'Beirut',        2500),
    ('Sodeco',            'Beirut',        2450),
    ('Mar Mikhael',       'Beirut',        2400),
    ('Badaro',            'Beirut',        2350),
    ('Hamra',             'Beirut',        2300),
    ('Mazraa',            'Beirut',        1500),
    ('Tarik El Jdideh',   'Beirut',        1300),
    ('Rabieh',            'Mount Lebanon', 2300),
    ('Hazmieh',           'Mount Lebanon', 2100),
    ('Dbayeh',            'Mount Lebanon', 2050),
    ('Kaslik',            'Mount Lebanon', 1950),
    ('Antelias',          'Mount Lebanon', 1850),
    ('Adma',              'Mount Lebanon', 1850),
    ('Mansourieh',        'Mount Lebanon', 1750),
    ('Faraya',            'Mount Lebanon', 1700),
    ('Baabda',            'Mount Lebanon', 1650),
    ('Bsalim',            'Mount Lebanon', 1650),
    ('Beit Mery',         'Mount Lebanon', 1600),
    ('Jdeideh',           'Mount Lebanon', 1600),
    ('Furn El Chebbak',   'Mount Lebanon', 1550),
    ('Zalka',             'Mount Lebanon', 1550),
    ('Jounieh',           'Mount Lebanon', 1500),
    ('Broummana',         'Mount Lebanon', 1500),
    ('Dekwaneh',          'Mount Lebanon', 1450),
    ('Ghazir',            'Mount Lebanon', 1350),
    ('Chiyah',            'Mount Lebanon', 1250),
    ('Aley',              'Mount Lebanon', 1200),
    ('Damour',            'Mount Lebanon', 1150),
    ('Bhamdoun',          'Mount Lebanon', 1100),
    ('Beiteddine',        'Mount Lebanon',  950),
    ('Jbeil',             'North',         1750),
    ('Batroun',           'North',         1650),
    ('Ehden',             'North',          900),
    ('Tripoli',           'North',          800),
    ('Koura',             'North',          780),
    ('Chekka',            'North',          720),
    ('Zgharta',           'North',          700),
    ('Saida',             'South',          950),
    ('Tyre',              'South',          880),
    ('Jezzine',           'South',          800),
    ('Nabatieh',          'Nabatieh',       720),
    ('Marjeyoun',         'Nabatieh',       600),
    ('Zahle',             'Bekaa',          820),
    ('Chtaura',           'Bekaa',          700),
    ('Rayak',             'Bekaa',          550),
    ('Baalbek',           'Bekaa',          520);

INSERT INTO dbo.property_type (name) VALUES
    ('Apartment'), ('Duplex'), ('Villa'), ('Chalet'),
    ('Office'), ('Shop'), ('Warehouse'), ('Land');
GO

/* --------------------------------------------------------------------------
   Accounts

   Password for every account is  Password123!
   The hash below is BCrypt cost 10 and is the same for all seeded users.
   Change it before anything resembling real use.
   -------------------------------------------------------------------------- */

DECLARE @pw VARCHAR(100) = '$2a$10$aF1a0.fPsAMK6N3ZOMN1xudXTzPhJwkWRZX7l0erZ/P8trBa8JtpO';

SET IDENTITY_INSERT dbo.app_user ON;

INSERT INTO dbo.app_user (id, email, password_hash, full_name, phone, role) VALUES
    (1, 'admin@aqarat.local',  @pw, 'System Administrator', '+9611000001', 'ADMIN'),
    (2, 'rami@aqarat.local',   @pw, 'Rami Khoury',          '+9613000002', 'AGENT'),
    (3, 'nour@aqarat.local',   @pw, 'Nour Haddad',          '+9613000003', 'AGENT'),
    (4, 'charbel@aqarat.local',@pw, 'Charbel Sfeir',        '+9613000004', 'AGENT'),
    (5, 'layal@aqarat.local',  @pw, 'Layal Mansour',        '+9613000005', 'AGENT');

SET IDENTITY_INSERT dbo.app_user OFF;
GO

/* 250 customers. Names are assembled from two small lists so they read as
   people rather than "Customer 137". */

IF OBJECT_ID('tempdb..#fn') IS NOT NULL DROP TABLE #fn;
IF OBJECT_ID('tempdb..#ln') IS NOT NULL DROP TABLE #ln;

CREATE TABLE #fn (rn INT IDENTITY(1,1), name VARCHAR(30));
CREATE TABLE #ln (rn INT IDENTITY(1,1), name VARCHAR(30));

INSERT INTO #fn (name) VALUES
    ('Ali'),('Hassan'),('Karim'),('Rami'),('Ziad'),('Nadim'),('Elie'),('Georges'),
    ('Tony'),('Marwan'),('Fadi'),('Sami'),('Bassam'),('Wissam'),('Charbel'),('Joseph'),
    ('Nabil'),('Walid'),('Omar'),('Youssef'),('Rana'),('Maya'),('Lina'),('Nour'),
    ('Hala'),('Rima'),('Carla'),('Joelle'),('Dima'),('Zeina'),('Sara'),('Layal'),
    ('Nadine'),('Yara'),('Christelle'),('Mirna'),('Hiba'),('Rita'),('Tala'),('Farah');

INSERT INTO #ln (name) VALUES
    ('Khoury'),('Haddad'),('Nassar'),('Saad'),('Karam'),('Aoun'),('Chalhoub'),('Rizk'),
    ('Sfeir'),('Gemayel'),('Mansour'),('Fares'),('Hobeika'),('Zeidan'),('Daher'),
    ('Ghanem'),('Tabet'),('Younes'),('Sleiman'),('Chidiac'),('Maalouf'),('Bitar'),
    ('Abou Jaoude'),('Bou Assi'),('Salameh');

DECLARE @pw2 VARCHAR(100) = '$2a$10$aF1a0.fPsAMK6N3ZOMN1xudXTzPhJwkWRZX7l0erZ/P8trBa8JtpO';

;WITH n AS (
    SELECT TOP (250) ROW_NUMBER() OVER (ORDER BY (SELECT NULL)) AS i
    FROM sys.all_objects
)
INSERT INTO dbo.app_user (email, password_hash, full_name, phone, role)
SELECT
    'user' + CAST(n.i AS VARCHAR(10)) + '@example.com',
    @pw2,
    f.name + ' ' + l.name,
    '+9613' + RIGHT('000000' + CAST(100000 + n.i AS VARCHAR(10)), 6),
    'CUSTOMER'
FROM n
JOIN #fn f ON f.rn = ((n.i * 7)  % 40) + 1
JOIN #ln l ON l.rn = ((n.i * 11) % 25) + 1;
GO

/* --------------------------------------------------------------------------
   Properties

   Built in three passes so that each random draw is made exactly once:
     #r    raw random numbers
     #gen  derived values, including the market value the price is built from
     insert
   -------------------------------------------------------------------------- */

IF OBJECT_ID('tempdb..#r')   IS NOT NULL DROP TABLE #r;
IF OBJECT_ID('tempdb..#gen') IS NOT NULL DROP TABLE #gen;
IF OBJECT_ID('tempdb..#dn')  IS NOT NULL DROP TABLE #dn;

SELECT id, name, avg_price_per_sqm,
       ROW_NUMBER() OVER (ORDER BY id) AS rn
INTO #dn
FROM dbo.district;

DECLARE @districts INT = (SELECT COUNT(*) FROM #dn);

;WITH n AS (
    SELECT TOP (2000) ROW_NUMBER() OVER (ORDER BY (SELECT NULL)) AS i
    FROM sys.all_objects a CROSS JOIN sys.all_objects b
)
SELECT
    i,
    ABS(CHECKSUM(NEWID())) % @districts AS r_district,
    ABS(CHECKSUM(NEWID())) % 100        AS r_type,
    ABS(CHECKSUM(NEWID())) % 100        AS r_deal,
    ABS(CHECKSUM(NEWID())) % 100        AS r_status,
    ABS(CHECKSUM(NEWID())) % 1000       AS r_size,
    ABS(CHECKSUM(NEWID())) % 101        AS r_noise,
    ABS(CHECKSUM(NEWID())) % 40         AS r_age,
    ABS(CHECKSUM(NEWID())) % 12         AS r_floor,
    ABS(CHECKSUM(NEWID())) % 16         AS r_feat,
    ABS(CHECKSUM(NEWID())) % 900        AS r_days,
    ABS(CHECKSUM(NEWID())) % 250        AS r_owner,
    ABS(CHECKSUM(NEWID())) % 4          AS r_agent,
    ABS(CHECKSUM(NEWID())) % 100        AS r_inflate,
    ABS(CHECKSUM(NEWID())) % 100        AS r_term
INTO #r
FROM n;

SELECT
    r.i,
    d.id                                        AS district_id,
    d.avg_price_per_sqm                         AS base_ppsm,
    d.name                                      AS district_name,

    CASE
        WHEN r.r_type < 58 THEN 'Apartment'
        WHEN r.r_type < 68 THEN 'Duplex'
        WHEN r.r_type < 76 THEN 'Villa'
        WHEN r.r_type < 82 THEN 'Chalet'
        WHEN r.r_type < 88 THEN 'Office'
        WHEN r.r_type < 94 THEN 'Shop'
        WHEN r.r_type < 97 THEN 'Warehouse'
        ELSE 'Land'
    END                                         AS type_name,

    CASE WHEN r.r_deal < 65 THEN 'SALE' ELSE 'RENT' END AS deal_type,

    CASE
        WHEN r.r_status < 55 THEN 'AVAILABLE'
        WHEN r.r_status < 75 THEN 'CLOSED'
        WHEN r.r_status < 85 THEN 'PENDING_REVIEW'
        WHEN r.r_status < 90 THEN 'UNDER_CONTRACT'
        WHEN r.r_status < 94 THEN 'RESERVED'
        WHEN r.r_status < 97 THEN 'NEEDS_INFO'
        WHEN r.r_status < 99 THEN 'REJECTED'
        ELSE 'WITHDRAWN'
    END                                         AS status,

    2026 - r.r_age                              AS year_built,
    r.r_floor                                   AS floor_number,
    r.r_floor + 2 + (r.r_size % 6)              AS total_floors,

    CASE WHEN r.r_feat & 1 = 1 THEN 1 ELSE 0 END AS has_parking,
    CASE WHEN r.r_feat & 2 = 2 THEN 1 ELSE 0 END AS has_elevator,
    CASE WHEN r.r_feat & 4 = 4 THEN 1 ELSE 0 END AS has_balcony,
    CASE WHEN r.r_feat & 8 = 8 THEN 1 ELSE 0 END AS is_furnished,

    6 + r.r_owner                               AS owner_id,
    2 + r.r_agent                               AS agent_id,
    r.r_days                                    AS days_ago,
    r.r_noise, r.r_inflate, r.r_term, r.r_size, r.r_age
INTO #gen
FROM #r r
JOIN #dn d ON d.rn = r.r_district + 1;

/* Area depends on what the thing is.

   Each ALTER is followed by GO. SQL Server compiles a whole batch before
   running any of it, so a column added and then used inside one batch fails
   to resolve. Temp tables survive GO, so this is safe. */

ALTER TABLE #gen ADD area_sqm DECIMAL(10,2);
GO

UPDATE #gen SET area_sqm =
    CASE type_name
        WHEN 'Apartment' THEN  70 + (r_size % 231)
        WHEN 'Duplex'    THEN 180 + (r_size % 221)
        WHEN 'Villa'     THEN 250 + (r_size % 451)
        WHEN 'Chalet'    THEN  60 + (r_size % 141)
        WHEN 'Office'    THEN  40 + (r_size % 261)
        WHEN 'Shop'      THEN  25 + (r_size % 176)
        WHEN 'Warehouse' THEN 150 + (r_size % 851)
        ELSE                  300 + (r_size % 2701)
    END;

ALTER TABLE #gen ADD bedrooms INT, bathrooms INT;
GO

UPDATE #gen SET
    bedrooms = CASE
        WHEN type_name IN ('Apartment','Duplex','Villa','Chalet')
            THEN CASE WHEN area_sqm / 50 < 1 THEN 1
                      WHEN area_sqm / 50 > 7 THEN 7
                      ELSE CAST(area_sqm / 50 AS INT) END
        ELSE 0 END;
UPDATE #gen SET
    bathrooms = CASE WHEN bedrooms = 0 THEN 1 ELSE 1 + (bedrooms / 3) END;

/* Market value.

   price per m2 = district base
                x type factor
                x age factor      (older is cheaper)
                x floor factor    (higher is slightly dearer)
                x size factor     (bigger units cost less per m2)
                x feature factor
                x noise           (plus or minus 12%)              */

ALTER TABLE #gen ADD market_value DECIMAL(14,2);
GO

UPDATE #gen SET market_value = ROUND(
      area_sqm
    * base_ppsm
    * CASE type_name
        WHEN 'Apartment' THEN 1.00 WHEN 'Duplex'    THEN 1.10
        WHEN 'Villa'     THEN 1.25 WHEN 'Chalet'    THEN 0.90
        WHEN 'Office'    THEN 0.95 WHEN 'Shop'      THEN 1.20
        WHEN 'Warehouse' THEN 0.45 ELSE 0.30 END
    * (1.0 - (r_age * 0.006))
    * (1.0 + (floor_number * 0.006))
    * (1.0 - (area_sqm / 1000.0 * 0.10))
    * (1.0 + has_parking * 0.03 + has_elevator * 0.02
           + has_balcony * 0.01 + is_furnished * 0.05)
    * (1.0 + ((r_noise - 50) * 0.0024))
  , -2);

/* A minority of owners ask well above the market. This is what gives the
   review queue real ABOVE_MARKET and IMPLAUSIBLE cases to demonstrate. */

ALTER TABLE #gen ADD asking_price DECIMAL(14,2);
GO

UPDATE #gen SET asking_price =
    CASE
        WHEN deal_type = 'RENT'
            THEN ROUND(market_value * 0.0055
                 * CASE WHEN r_inflate >= 92 THEN 1.55
                        WHEN r_inflate >= 84 THEN 1.25
                        ELSE 1.0 END, -1)
        ELSE ROUND(market_value
                 * CASE WHEN r_inflate >= 96 THEN 2.10
                        WHEN r_inflate >= 88 THEN 1.35
                        ELSE 1.0 END, -2)
    END;

/* Keep everything above the CHECK constraints. */
UPDATE #gen SET asking_price = 100  WHERE asking_price < 100;
UPDATE #gen SET market_value = 1000 WHERE market_value < 1000;

SET IDENTITY_INSERT dbo.property ON;

INSERT INTO dbo.property
    (id, owner_id, agent_id, district_id, property_type_id, title, description,
     address_line, area_sqm, bedrooms, bathrooms, floor_number, total_floors,
     year_built, has_parking, has_elevator, has_balcony, is_furnished,
     deal_type, asking_price, min_term_months, max_term_months,
     status, review_note, submitted_at, published_at, closed_at, created_at)
SELECT
    g.i,
    g.owner_id,
    CASE WHEN g.status IN ('PENDING_REVIEW','WITHDRAWN') THEN NULL ELSE g.agent_id END,
    g.district_id,
    pt.id,
    g.type_name + ' in ' + g.district_name,
    CAST(g.bedrooms AS VARCHAR(3)) + ' bedroom ' + LOWER(g.type_name)
        + ' of ' + CAST(CAST(g.area_sqm AS INT) AS VARCHAR(10)) + ' m2 in '
        + g.district_name + '. Built in ' + CAST(g.year_built AS VARCHAR(4)) + '.',
    'Street ' + CAST((g.i % 40) + 1 AS VARCHAR(3))
        + ', Building ' + CAST((g.i % 17) + 1 AS VARCHAR(3)),
    g.area_sqm, g.bedrooms, g.bathrooms, g.floor_number, g.total_floors,
    g.year_built, g.has_parking, g.has_elevator, g.has_balcony, g.is_furnished,
    g.deal_type,
    g.asking_price,
    CASE WHEN g.deal_type = 'RENT' AND g.r_term < 70 THEN 3 + (g.r_term % 4) END,
    CASE WHEN g.deal_type = 'RENT' AND g.r_term < 45 THEN 12 + (g.r_term % 25) END,
    g.status,
    CASE g.status
        WHEN 'NEEDS_INFO' THEN 'Please upload the title deed and at least three photos.'
        WHEN 'REJECTED'   THEN 'Asking price is far outside anything comparable in this district.'
    END,
    /* submitted five days before it was published, so the ordering is always
       submitted -> published -> closed, whatever days_ago happens to be */
    DATEADD(DAY, -(g.days_ago + 5), SYSUTCDATETIME()),
    CASE WHEN g.status NOT IN ('PENDING_REVIEW','NEEDS_INFO','REJECTED','WITHDRAWN')
         THEN DATEADD(DAY, -g.days_ago, SYSUTCDATETIME()) END,
    CASE WHEN g.status = 'CLOSED'
         THEN DATEADD(DAY, -(g.days_ago / 4), SYSUTCDATETIME()) END,
    DATEADD(DAY, -(g.days_ago + 5), SYSUTCDATETIME())
FROM #gen g
JOIN dbo.property_type pt ON pt.name = g.type_name;

SET IDENTITY_INSERT dbo.property OFF;
DBCC CHECKIDENT ('dbo.property', RESEED) WITH NO_INFOMSGS;
GO

/* Four photos per property, matched to its type so a villa does not open with
   an apartment thumbnail. The files ship with the app under uploads/images;
   the path stored in the row is relative to that root.

   Photo 1 is the primary and is what a listing card shows. The rest exist so
   the property detail gallery has something to be a gallery of. */

INSERT INTO dbo.property_photo (property_id, file_path, is_primary, sort_order)
SELECT p.id,
    'images/seed/' + LOWER(REPLACE(pt.name, ' ', '-'))
        + '-' + CAST(n.sort_order + 1 AS VARCHAR(2)) + '.jpg',
    CASE WHEN n.sort_order = 0 THEN 1 ELSE 0 END,
    n.sort_order
FROM dbo.property p
JOIN dbo.property_type pt ON pt.id = p.property_type_id
CROSS JOIN (VALUES (0), (1), (2), (3)) AS n(sort_order);
GO

/* --------------------------------------------------------------------------
   Review threads
   -------------------------------------------------------------------------- */

/* The agent's review note is mirrored as the first message of the discussion,
   so a customer opening My Properties sees the thread without having to
   provoke one first. */

INSERT INTO dbo.property_message (property_id, author_id, message, created_at)
SELECT p.id, p.agent_id, p.review_note, p.submitted_at
FROM dbo.property p
WHERE p.status = 'NEEDS_INFO'
  AND p.agent_id IS NOT NULL
  AND p.review_note IS NOT NULL;
GO

/* --------------------------------------------------------------------------
   Valuations

   Generated for everything an agent might look at. The estimate is built from
   the market value the property was generated from, with a little noise, so
   the flag on an inflated asking price comes out correctly.
   -------------------------------------------------------------------------- */

INSERT INTO dbo.valuation
    (property_id, estimated_value, lower_bound, upper_bound, price_per_sqm,
     flag, breakdown, comparables, model_version, created_at)
SELECT
    p.id,
    est.v,
    /* rounded to whole dollars, not hundreds: a monthly rent of 82 would
       round to bounds of 100 and 100 and break ck_valuation_range */
    ROUND(est.v * 0.91, 0),
    ROUND(est.v * 1.09, 0),
    ROUND(est.v / p.area_sqm, 2),
    CASE
        WHEN p.deal_type = 'RENT' THEN
            CASE WHEN p.asking_price > est.v * 1.60 THEN 'IMPLAUSIBLE'
                 WHEN p.asking_price > est.v * 1.09 THEN 'ABOVE_MARKET'
                 ELSE 'OK' END
        ELSE
            CASE WHEN p.asking_price > est.v * 1.60 THEN 'IMPLAUSIBLE'
                 WHEN p.asking_price < est.v * 0.40 THEN 'IMPLAUSIBLE'
                 WHEN p.asking_price > est.v * 1.09 THEN 'ABOVE_MARKET'
                 ELSE 'OK' END
    END,
    'district=' + CAST(ROUND(est.v * 0.72, 0) AS VARCHAR(20))
        + ';size=' + CAST(ROUND(est.v * 0.14, 0) AS VARCHAR(20))
        + ';age=-' + CAST(ROUND(est.v * 0.04, 0) AS VARCHAR(20))
        + ';features=' + CAST(ROUND(est.v * 0.03, 0) AS VARCHAR(20)),
    'seeded',
    'v1',
    p.submitted_at
FROM dbo.property p
CROSS APPLY (
    SELECT CASE WHEN p.deal_type = 'RENT'
                THEN ROUND(g.market_value * 0.0055, -1)
                ELSE g.market_value END AS v
    FROM #gen g WHERE g.i = p.id
) est
WHERE p.status IN ('PENDING_REVIEW','NEEDS_INFO','REJECTED','AVAILABLE','RESERVED');
GO

/* --------------------------------------------------------------------------
   Viewings
   -------------------------------------------------------------------------- */

;WITH n AS (
    SELECT TOP (400) ROW_NUMBER() OVER (ORDER BY (SELECT NULL)) AS i
    FROM sys.all_objects
)
INSERT INTO dbo.viewing (property_id, client_id, agent_id, scheduled_at, status, outcome_note)
SELECT
    p.id,
    6 + (ABS(CHECKSUM(NEWID())) % 250),
    p.agent_id,
    DATEADD(HOUR, 9 + (n.i % 8), CAST(DATEADD(DAY, (n.i % 40) - 20, CAST(SYSUTCDATETIME() AS DATE)) AS DATETIME2(0))),
    CASE WHEN n.i % 10 < 4 THEN 'COMPLETED'
         WHEN n.i % 10 < 7 THEN 'REQUESTED'
         WHEN n.i % 10 < 9 THEN 'CANCELLED'
         ELSE 'NO_SHOW' END,
    CASE WHEN n.i % 10 < 4 THEN 'Client attended. Interested, considering an offer.' END
FROM n
CROSS APPLY (
    SELECT TOP 1 id, agent_id FROM dbo.property
    WHERE status IN ('AVAILABLE','RESERVED') AND agent_id IS NOT NULL
    ORDER BY NEWID()
) p;
GO

/* --------------------------------------------------------------------------
   Reservations

   Exactly one ACTIVE reservation per RESERVED property - the filtered unique
   index ux_reservation_active would reject anything else.
   -------------------------------------------------------------------------- */

INSERT INTO dbo.reservation
    (property_id, client_id, agent_id, deposit_amount, reserved_at, expires_at, status)
SELECT
    p.id,
    6 + (ABS(CHECKSUM(NEWID())) % 250),
    p.agent_id,
    /* one month up front for a rental, 3% for a sale, never below 100 */
    CASE WHEN p.deal_type = 'RENT'                   THEN p.asking_price
         WHEN ROUND(p.asking_price * 0.03, 0) < 100  THEN 100
         ELSE ROUND(p.asking_price * 0.03, 0) END,
    DATEADD(DAY, -6, SYSUTCDATETIME()),
    DATEADD(DAY,  8, SYSUTCDATETIME()),
    'ACTIVE'
FROM dbo.property p
WHERE p.status = 'RESERVED' AND p.agent_id IS NOT NULL;
GO

/* --------------------------------------------------------------------------
   Contracts

   One per property that is under contract or closed.
   -------------------------------------------------------------------------- */

DECLARE @rate DECIMAL(5,2) =
    (SELECT CAST(value AS DECIMAL(5,2)) FROM dbo.system_setting
     WHERE setting_key = 'commission_rate_percent');

INSERT INTO dbo.contract
    (property_id, owner_id, client_id, agent_id, contract_type, total_amount,
     monthly_rent, start_date, end_date, term_months, payment_frequency,
     installment_count, commission_rate, commission_amount, status,
     created_at, activated_at, closed_at)
SELECT
    p.id,
    p.owner_id,
    6 + (ABS(CHECKSUM(NEWID())) % 250),
    p.agent_id,
    CASE WHEN p.deal_type = 'SALE' THEN 'SALE' ELSE 'LEASE' END,
    CASE WHEN p.deal_type = 'SALE' THEN p.asking_price
         ELSE p.asking_price * t.term END,
    CASE WHEN p.deal_type = 'RENT' THEN p.asking_price END,
    CAST(p.published_at AS DATE),
    CASE WHEN p.deal_type = 'RENT'
         THEN DATEADD(MONTH, t.term, CAST(p.published_at AS DATE)) END,
    CASE WHEN p.deal_type = 'RENT' THEN t.term END,
    CASE WHEN p.deal_type = 'SALE' THEN 'INSTALLMENT' ELSE 'MONTHLY' END,
    CASE WHEN p.deal_type = 'SALE' THEN 1 + (p.id % 6) ELSE t.term END,
    @rate,
    ROUND(CASE WHEN p.deal_type = 'SALE' THEN p.asking_price
               ELSE p.asking_price * t.term END * @rate / 100.0, 2),
    CASE WHEN p.status = 'CLOSED' THEN 'COMPLETED' ELSE 'ACTIVE' END,
    p.published_at,
    p.published_at,
    p.closed_at
FROM dbo.property p
CROSS APPLY (SELECT 12 + ((p.id % 3) * 12) AS term) t
WHERE p.status IN ('UNDER_CONTRACT','CLOSED') AND p.agent_id IS NOT NULL;
GO

/* --------------------------------------------------------------------------
   Payment schedules

   A lease gets one row per month. A sale gets its installments spaced
   two months apart.
   -------------------------------------------------------------------------- */

;WITH months AS (
    SELECT TOP (60) ROW_NUMBER() OVER (ORDER BY (SELECT NULL)) AS k
    FROM sys.all_objects
)
INSERT INTO dbo.payment_schedule (contract_id, installment_no, due_date, amount_due)
SELECT
    c.id,
    m.k,
    CASE WHEN c.contract_type = 'LEASE'
         THEN DATEADD(MONTH, m.k - 1, c.start_date)
         ELSE DATEADD(MONTH, (m.k - 1) * 2, c.start_date) END,
    ROUND(c.total_amount / c.installment_count, 2)
FROM dbo.contract c
JOIN months m ON m.k <= c.installment_count;
GO

/* --------------------------------------------------------------------------
   Payments

   Anything already due is paid, except roughly one in eight, which is what
   populates the overdue reports. A handful sit as DECLARED so the agent's
   confirmation queue is not empty.
   -------------------------------------------------------------------------- */

UPDATE s
SET amount_paid = s.amount_due,
    status      = 'PAID'
FROM dbo.payment_schedule s
WHERE s.due_date <= CAST(SYSUTCDATETIME() AS DATE)
  AND s.id % 8 <> 0;

UPDATE s
SET status = 'OVERDUE'
FROM dbo.payment_schedule s
WHERE s.due_date <= CAST(SYSUTCDATETIME() AS DATE)
  AND s.id % 8 = 0;

INSERT INTO dbo.payment
    (schedule_id, amount, paid_at, method, reference, declared_by, confirmed_by, status, created_at)
SELECT
    s.id,
    s.amount_paid,
    CAST(s.due_date AS DATETIME2(0)),
    CASE s.id % 3 WHEN 0 THEN 'BANK_TRANSFER' WHEN 1 THEN 'CASH' ELSE 'CHEQUE' END,
    'REF-' + RIGHT('000000' + CAST(s.id AS VARCHAR(10)), 6),
    c.client_id,
    c.agent_id,
    'CONFIRMED',
    CAST(s.due_date AS DATETIME2(0))
FROM dbo.payment_schedule s
JOIN dbo.contract c ON c.id = s.contract_id
WHERE s.status = 'PAID';

/* A few awaiting an agent's confirmation. */
INSERT INTO dbo.payment
    (schedule_id, amount, paid_at, method, reference, proof_path, declared_by, status)
SELECT TOP (12)
    s.id,
    s.amount_due,
    SYSUTCDATETIME(),
    'BANK_TRANSFER',
    'REF-PENDING-' + CAST(s.id AS VARCHAR(10)),
    'proofs/placeholder.pdf',
    c.client_id,
    'DECLARED'
FROM dbo.payment_schedule s
JOIN dbo.contract c ON c.id = s.contract_id
WHERE s.status = 'OVERDUE'
ORDER BY s.id;

/* Reservation deposits. */
INSERT INTO dbo.payment
    (reservation_id, amount, paid_at, method, reference, declared_by, confirmed_by, status)
SELECT
    r.id, r.deposit_amount, r.reserved_at, 'CASH',
    'DEP-' + RIGHT('000000' + CAST(r.id AS VARCHAR(10)), 6),
    r.client_id, r.agent_id, 'CONFIRMED'
FROM dbo.reservation r;
GO

/* --------------------------------------------------------------------------
   Done
   -------------------------------------------------------------------------- */

DROP TABLE IF EXISTS #r;
DROP TABLE IF EXISTS #gen;
DROP TABLE IF EXISTS #dn;
DROP TABLE IF EXISTS #fn;
DROP TABLE IF EXISTS #ln;
GO

SELECT 'districts'  AS table_name, COUNT(*) AS row_count FROM dbo.district
UNION ALL SELECT 'users',            COUNT(*) FROM dbo.app_user
UNION ALL SELECT 'properties',       COUNT(*) FROM dbo.property
UNION ALL SELECT 'valuations',       COUNT(*) FROM dbo.valuation
UNION ALL SELECT 'viewings',         COUNT(*) FROM dbo.viewing
UNION ALL SELECT 'reservations',     COUNT(*) FROM dbo.reservation
UNION ALL SELECT 'contracts',        COUNT(*) FROM dbo.contract
UNION ALL SELECT 'schedule rows',    COUNT(*) FROM dbo.payment_schedule
UNION ALL SELECT 'payments',         COUNT(*) FROM dbo.payment;

PRINT 'Aqarat seed complete. Sign in as admin@aqarat.local with Password123!';
GO
