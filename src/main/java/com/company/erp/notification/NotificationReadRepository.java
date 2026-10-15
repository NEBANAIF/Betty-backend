package com.company.erp.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface NotificationReadRepository extends JpaRepository<NotificationRead, UUID> {

    boolean existsByNotificationIdAndUserId(UUID notificationId, UUID userId);

    /** Of the given notifications, the ids this user has already read. */
    @Query("SELECT r.notificationId FROM NotificationRead r WHERE r.userId = :userId AND r.notificationId IN :ids")
    List<UUID> findReadIds(@Param("userId") UUID userId, @Param("ids") Collection<UUID> ids);
}
