package com.company.erp.notification.dto;

import com.company.erp.notification.Notification;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        String type,
        String title,
        String message,
        UUID branchId,
        String branchName,
        String actorName,
        String referenceType,
        UUID referenceId,
        Instant createdAt,
        /** PENDING / ON_HOLD / APPROVED */
        String reviewStatus,
        String reviewedByName,
        Instant reviewedAt,
        /** Whether the signed-in user has read it. */
        boolean read
) {
    public static NotificationResponse from(Notification n, boolean read) {
        return new NotificationResponse(
                n.getId(), n.getType().name(), n.getTitle(), n.getMessage(),
                n.getBranch().getId(), n.getBranch().getName(),
                n.getActor() != null ? n.getActor().getName() : null,
                n.getReferenceType(), n.getReferenceId(), n.getCreatedAt(),
                n.getReviewStatus().name(),
                n.getReviewedBy() != null ? n.getReviewedBy().getName() : null,
                n.getReviewedAt(), read);
    }
}
