/*
    Demonstration accounts on the company domain.

    The launch film and the live defence both put an email address on screen.
    A fake domain or a personal address undermines the thing the film is
    arguing, so the three roles are shown on syntropyhq.co instead.

    Run after seed.sql, which does not create these:

        sqlcmd -S "localhost\SQLEXPRESS" -E -C -I -d Aqarat -i db/demo-accounts.sql

    Idempotent: re-running updates the rows rather than failing on the unique
    index over email.

    The hash is BCrypt of the same password every seeded account uses, so the
    demonstration needs exactly one password memorised. It is a throwaway
    credential against a local database with no network listener; it is not a
    secret and it does not unlock anything beyond this machine.
*/

DECLARE @hash VARCHAR(100) = '$2a$10$aF1a0.fPsAMK6N3ZOMN1xudXTzPhJwkWRZX7l0erZ/P8trBa8JtpO';

MERGE app_user AS target
USING (VALUES
    ('admin@syntropyhq.co',    'Syntropy Administrator', '+9611000010', 'ADMIN'),
    ('agent@syntropyhq.co',    'Layla Mansour',          '+9613000011', 'AGENT'),
    ('customer@syntropyhq.co', 'Karim Nassar',           '+9613000012', 'CUSTOMER')
) AS source (email, full_name, phone, role)
ON target.email = source.email
WHEN MATCHED THEN
    UPDATE SET
        full_name     = source.full_name,
        phone         = source.phone,
        role          = source.role,
        status        = 'ACTIVE',
        password_hash = @hash
WHEN NOT MATCHED THEN
    INSERT (email, password_hash, full_name, phone, role, status, created_at)
    VALUES (source.email, @hash, source.full_name, source.phone, source.role, 'ACTIVE', SYSUTCDATETIME());

SELECT email, full_name, role, status
FROM app_user
WHERE email LIKE '%@syntropyhq.co'
ORDER BY role;

/*
    A portfolio worth filming.

    A brand-new account owns nothing, so the Portfolio panel renders four zero
    counters and an empty-state placeholder. That is correct behaviour and a
    terrible thing to put in a launch film — it reads as an unfinished product
    rather than an empty account.

    So the demonstration customer is given a spread across the statuses the
    panel counts, drawn from central Beirut districts at prices that read as
    real. Selection is by criteria and ordered by id rather than by hardcoded
    ids, so a reseed produces the same portfolio again.

    This curates which rows are visible. It does not change what the software
    does with them.
*/

DECLARE @customer INT = (SELECT id FROM app_user WHERE email = 'customer@syntropyhq.co');

DECLARE @filmable TABLE (id INT PRIMARY KEY);

INSERT INTO @filmable (id)
SELECT id FROM (
    SELECT p.id,
           ROW_NUMBER() OVER (PARTITION BY p.status ORDER BY p.id) AS rank_in_status,
           p.status
    FROM property p
    JOIN district d ON d.id = p.district_id
    WHERE d.name IN ('Achrafieh', 'Ras Beirut', 'Hamra', 'Verdun',
                     'Gemmayzeh', 'Mar Mikhael', 'Badaro', 'Sodeco')
      AND p.asking_price BETWEEN 120000 AND 900000
      AND p.status IN ('AVAILABLE', 'PENDING_REVIEW', 'NEEDS_INFO', 'UNDER_CONTRACT')
) ranked
WHERE (status = 'AVAILABLE'       AND rank_in_status <= 4)
   OR (status = 'PENDING_REVIEW'  AND rank_in_status <= 2)
   OR (status = 'NEEDS_INFO'      AND rank_in_status <= 1)
   OR (status = 'UNDER_CONTRACT'  AND rank_in_status <= 1);

UPDATE property SET owner_id = @customer WHERE id IN (SELECT id FROM @filmable);

SELECT p.status, COUNT(*) AS owned
FROM property p
WHERE p.owner_id = @customer
GROUP BY p.status
ORDER BY p.status;
