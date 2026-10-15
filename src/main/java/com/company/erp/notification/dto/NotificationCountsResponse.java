package com.company.erp.notification.dto;

/** unread: not read by the signed-in user. toReview: still pending or on hold (for everyone). */
public record NotificationCountsResponse(long unread, long toReview) {
}
