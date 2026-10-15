package com.company.erp.returns;

/**
 * Both CustomerReturn and SupplierReturn are processed atomically in one
 * call (validate -> move stock -> record -> done) - spec sections 21/22
 * describe a linear pipeline, not a staged approval workflow like
 * PurchaseOrder or StockTransfer, and no return-specific status enum is
 * listed in the fixed set (doc 1 section 6). COMPLETED is the only status
 * either return type is ever created with.
 * <p>
 * VOID is intentionally reserved, not implemented: reversing a return
 * (e.g. a mistaken entry) would itself require its own validated stock
 * movement, which isn't part of this phase's scope. No code path in this
 * phase ever sets it - it exists so a future "void a return" feature
 * doesn't need another migration.
 */
public enum ReturnStatus {
    COMPLETED,
    VOID
}
