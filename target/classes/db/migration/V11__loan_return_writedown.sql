-- LoanService.reduceForReturn (added to fix customer returns against
-- credit sales not touching the associated Loan) can legitimately drive
-- a loan's original_amount all the way to 0: if a customer returns 100%
-- of a sale they never paid a cent of, the debt that should have existed
-- shrinks to nothing right along with it. original_amount > 0 was correct
-- for loan CREATION (LoanService.createFromSale already separately
-- requires remainingAmount > 0 before ever creating one - see that
-- method), but it's too strict for a loan's ongoing lifecycle once
-- returns can write it down. remaining_amount was already >= 0 for
-- exactly this reason; original_amount should match.
ALTER TABLE loans DROP CONSTRAINT loans_original_amount_check;
ALTER TABLE loans ADD CONSTRAINT loans_original_amount_check CHECK (original_amount >= 0);
