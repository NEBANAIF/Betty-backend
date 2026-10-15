-- A registered sale needs no approval. New sale notifications are created already approved;
-- this clears the ones that were still waiting under "To review".
UPDATE notifications
   SET review_status = 'APPROVED',
       reviewed_at   = COALESCE(reviewed_at, now())
 WHERE type = 'SALE_COMPLETED'
   AND review_status IN ('PENDING', 'ON_HOLD');
