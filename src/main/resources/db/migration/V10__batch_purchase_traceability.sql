-- Batch-to-purchase traceability, added after Phase 11 review. Supplier
-- Returns (Phase 9) originally removed stock via a generic FIFO sweep
-- across every batch of a product at a branch, because no link existed
-- from a batch back to the purchase-order line that created it. That's a
-- real correctness gap: if two suppliers ship the same product to the
-- same branch, a return against Supplier B's purchase order could
-- silently draw stock from (and credit the returned value against)
-- Supplier A's batch instead. This column closes that gap - see
-- ProductBatch.sourcePurchaseOrderItemId and
-- StockMutationService.issueFromPurchaseOrderItem.
ALTER TABLE product_batches
    ADD COLUMN source_purchase_order_item_id UUID REFERENCES purchase_order_items (id);

CREATE INDEX idx_batches_source_po_item ON product_batches (source_purchase_order_item_id);
