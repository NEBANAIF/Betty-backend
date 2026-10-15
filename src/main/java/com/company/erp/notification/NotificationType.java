package com.company.erp.notification;

/** What kind of activity a notification reports. Stock changes carry their own wording (title/message). */
public enum NotificationType {
    SALE_COMPLETED,
    SALE_VOIDED,
    STOCK_CHANGE
}
