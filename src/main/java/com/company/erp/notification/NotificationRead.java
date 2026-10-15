package com.company.erp.notification;

import com.company.erp.common.audit.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** "This user has read this notification." Unique per (notification, user). */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "notification_reads")
public class NotificationRead extends BaseEntity {

    @Column(nullable = false)
    private UUID notificationId;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private Instant readAt;

    public NotificationRead(UUID notificationId, UUID userId) {
        this.notificationId = notificationId;
        this.userId = userId;
        this.readAt = Instant.now();
    }
}
