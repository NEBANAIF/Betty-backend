package com.company.erp.notification;

/**
 * Where an activity stands in the check-it-is-correct process.
 * PENDING (nobody has looked at it yet) -> ON_HOLD (someone is checking it) -> APPROVED (checked, correct).
 * An activity can also go straight from PENDING to APPROVED. For sales and ordinary stock changes
 * none of these undoes or blocks the activity: it has already happened when the notification appears.
 * The exception is a manual stock change held for approval (see stockchange.StockChangeRequest): its
 * notification is PENDING while the stock has NOT moved yet, APPROVED once it has, and REJECTED when
 * it was discarded.
 */
public enum ReviewStatus {
    PENDING,
    ON_HOLD,
    APPROVED,
    REJECTED
}
