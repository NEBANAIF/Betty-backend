-- Editing a batch's number, cost or expiry is a held stock change too (EDIT_BATCH): nothing
-- changes on the batch until someone approves it. The proposed values live on the request.
ALTER TABLE stock_change_requests DROP CONSTRAINT IF EXISTS stock_change_requests_change_type_check;
ALTER TABLE stock_change_requests ADD CONSTRAINT stock_change_requests_change_type_check
    CHECK (change_type IN ('ADD_BATCH', 'REMOVE_STOCK', 'EDIT_BATCH'));
ALTER TABLE stock_change_requests ADD COLUMN new_batch_number VARCHAR(100);
