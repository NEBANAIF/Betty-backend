-- Until now, paying a loan off left the sale itself untouched, so Sales History kept saying "Partial" and an
-- amount owed after the loan was fully paid. The sale now follows its loan (see LoanService.syncSaleWithLoan);
-- this brings the sales that already exist in line: money collected on the loan counts as paid on the sale,
-- "owed" becomes what the loan still owes, and the sale becomes PAID when nothing is owed.
UPDATE sales s
   SET paid_amount      = s.paid_amount + l.paid_amount,
       remaining_amount = l.remaining_amount,
       payment_status   = CASE
                              WHEN l.remaining_amount = 0 THEN 'PAID'
                              WHEN s.paid_amount + l.paid_amount > 0 THEN 'PARTIAL'
                              ELSE s.payment_status
                          END
  FROM loans l
 WHERE l.sale_id = s.id
   AND (l.paid_amount > 0 OR l.remaining_amount <> s.remaining_amount);
