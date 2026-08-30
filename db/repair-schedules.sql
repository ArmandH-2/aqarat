/* --------------------------------------------------------------------------
   Make every instalment agree with the payments behind it

   One invariant: a schedule's amount_paid is the sum of the CONFIRMED payments
   against it. Anything else is a figure with nothing behind it.

   Two earlier scripts broke it in two different ways, and both are closed now:

   - rebase-arrears.sql settled overdue instalments without checking whether one
     already carried a payment awaiting confirmation, which left instalments
     marked paid with no confirmed payment at all and a client's declaration
     hanging off them. An agent confirming that declaration is refused, because
     applying it would count the money twice - so the confirmation queue filled
     with rows that could never be actioned.

   - the same script also produced instalments at exactly twice their amount due,
     where a confirmation did land on top of the fabricated settlement.

   Restating amount_paid from the payment rows repairs both, because both are the
   same fault: a number that no payment supports. The status follows from the
   restated figure and the due date.

   Idempotent: only rows that disagree are touched, so a rerun changes nothing.
   -------------------------------------------------------------------------- */

USE Aqarat;
GO

DECLARE @today DATE = CAST(SYSUTCDATETIME() AS DATE);

WITH confirmed AS (
    SELECT s.id,
           s.amount_due,
           s.amount_paid,
           s.due_date,
           ISNULL((SELECT SUM(p.amount)
                     FROM dbo.payment p
                    WHERE p.schedule_id = s.id
                      AND p.status = 'CONFIRMED'), 0) AS actually_paid
      FROM dbo.payment_schedule s
)
UPDATE s
SET amount_paid = c.actually_paid,
    /* The same rule the service applies, plus the one it cannot: an instalment
       that is unpaid and past its due date is overdue, not merely pending. */
    status = CASE
                WHEN c.actually_paid >= c.amount_due THEN 'PAID'
                WHEN c.due_date < @today            THEN 'OVERDUE'
                WHEN c.actually_paid > 0            THEN 'PARTIALLY_PAID'
                ELSE 'PENDING'
             END
FROM dbo.payment_schedule s
JOIN confirmed c ON c.id = s.id
WHERE s.amount_paid <> c.actually_paid;
GO

SELECT
    (SELECT COUNT(*) FROM dbo.payment_schedule s
      WHERE s.amount_paid <> ISNULL((SELECT SUM(p.amount) FROM dbo.payment p
                                      WHERE p.schedule_id = s.id
                                        AND p.status = 'CONFIRMED'), 0))    AS still_disagreeing,
    (SELECT COUNT(*) FROM dbo.payment p
      JOIN dbo.payment_schedule s ON s.id = p.schedule_id
     WHERE p.status = 'DECLARED'
       AND s.amount_paid >= s.amount_due)                                   AS unconfirmable_declarations;
GO
