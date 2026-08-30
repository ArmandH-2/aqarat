/* --------------------------------------------------------------------------
   Bring arrears inside the collections window

   The original seed left roughly one instalment in eight unpaid across the
   whole history, so a database seeded months ago accumulates payments nine
   months late. No agency operates that way — arrears are chased, escalated, or
   the contract is terminated — and on a client's own portfolio it read as
   though they had never paid anything.

   This settles every overdue instalment older than the collections window and
   writes the confirming payment row each settled instalment needs, so the
   schedule and the payment ledger stay consistent.

   Idempotent: rerunning it settles nothing further and inserts no duplicates.
   A fresh db/seed.sql produces this state without needing the script.
   -------------------------------------------------------------------------- */

USE Aqarat;
GO

DECLARE @collections_window_days INT = 45;
DECLARE @cutoff DATE = DATEADD(DAY, -@collections_window_days, CAST(SYSUTCDATETIME() AS DATE));

/* 1. Settle anything overdue from before the window.

      An instalment with a payment still awaiting confirmation is left alone.
      Settling it here would mark it paid while a DECLARED payment for the same
      money sat in the agent's queue, and confirming that payment would then add
      the amount a second time. */
UPDATE s
SET amount_paid = s.amount_due,
    status      = 'PAID'
FROM dbo.payment_schedule s
WHERE s.status <> 'PAID'
  AND s.due_date < @cutoff
  AND NOT EXISTS (SELECT 1 FROM dbo.payment p
                  WHERE p.schedule_id = s.id AND p.status = 'DECLARED');

/* 2. Every paid instalment needs the payment that paid it. Only rows with no
      payment at all are inserted, which is what makes a rerun a no-op. */
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
WHERE s.status = 'PAID'
  AND NOT EXISTS (SELECT 1 FROM dbo.payment p WHERE p.schedule_id = s.id);
GO

SELECT
    (SELECT COUNT(*) FROM dbo.payment_schedule WHERE status = 'OVERDUE') AS still_overdue,
    (SELECT COUNT(*) FROM dbo.payment_schedule WHERE status = 'PAID')    AS paid,
    (SELECT DATEDIFF(DAY, MIN(due_date), CAST(SYSUTCDATETIME() AS DATE))
     FROM dbo.payment_schedule WHERE status = 'OVERDUE')                 AS oldest_arrear_days;
GO
