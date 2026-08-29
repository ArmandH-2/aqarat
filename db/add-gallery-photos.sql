/* --------------------------------------------------------------------------
   Gallery top-up

   The original seed gave every property a single primary photograph, which is
   all a list card needs. The property detail panel shows a gallery, so each
   property also gets photos 2 to 4 of its type.

   Idempotent: only inserts the rows that are missing, so it is safe to run
   against a database that has already been topped up. A fresh
   db/seed.sql produces the same four rows without needing this script.
   -------------------------------------------------------------------------- */

USE Aqarat;
GO

INSERT INTO dbo.property_photo (property_id, file_path, is_primary, sort_order)
/* sort_order 0 already holds <type>-1.jpg, so file index is sort_order + 1. */
SELECT p.id,
       'images/seed/' + LOWER(REPLACE(pt.name, ' ', '-'))
           + '-' + CAST(n.sort_order + 1 AS VARCHAR(2)) + '.jpg',
       0,
       n.sort_order
FROM dbo.property p
JOIN dbo.property_type pt ON pt.id = p.property_type_id
CROSS JOIN (VALUES (1), (2), (3)) AS n(sort_order)
WHERE NOT EXISTS (
    SELECT 1
    FROM dbo.property_photo existing
    WHERE existing.property_id = p.id
      AND existing.sort_order = n.sort_order
);
GO

SELECT COUNT(*) AS photo_rows, COUNT(DISTINCT property_id) AS properties
FROM dbo.property_photo;
GO
