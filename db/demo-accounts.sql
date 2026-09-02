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
