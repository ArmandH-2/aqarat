/* --------------------------------------------------------------------------
   Repair instalments counted twice

   An earlier version of rebase-arrears.sql settled overdue instalments without
   checking whether one already had a payment awaiting confirmation. That left a
   DECLARED payment sitting against an instalment that was already marked paid,
   and confirming it added the same money a second time — so amount_paid ended
   up at exactly twice amount_due and a client's receipt reported a negative
   balance remaining.

   Both causes are now closed: the script skips those instalments, and
   PaymentService refuses to apply a payment to an instalment already settled in
   full. This repairs the rows written before either was in place.

   Idempotent: it only touches rows whose amount_paid disagrees with the
   payments actually confirmed against them, so a rerun changes nothing.
   -------------------------------------------------------------------------- */

USE Aqarat;
GO

/* Restate amount_paid as what was actually confirmed, which is the only figure
   that can be traced to a payment row. */
UPDATE s
SET amount_paid = confirmed.total
FROM dbo.payment_schedule s
CROSS APPLY (
    SELECT ISNULL(SUM(p.amount), 0) AS total
    FROM dbo.payment p
    WHERE p.schedule_id = s.id
      AND p.status = 'CONFIRMED'
) AS confirmed
WHERE s.amount_paid > s.amount_due
  AND s.amount_paid <> confirmed.total;
GO

SELECT
    (SELECT COUNT(*) FROM dbo.payment_schedule WHERE amount_paid > amount_due) AS still_overpaid;
GO
