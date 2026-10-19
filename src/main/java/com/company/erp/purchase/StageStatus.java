package com.company.erp.purchase;

/**
 * Status of one stop on the route.
 * PENDING - not reached yet (any dates are plans); ARRIVED - the goods are there now;
 * DEPARTED - the goods have left; DELAYED - the goods are there but held up (customs, weather, ...).
 */
public enum StageStatus {
    PENDING,
    ARRIVED,
    DEPARTED,
    DELAYED;

    /** The goods have actually been at this stop. */
    public boolean isReached() {
        return this != PENDING;
    }
}
