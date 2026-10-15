package com.company.erp.notification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    /** Not yet read by this user, for the given branches, newest first. */
    @Query(value = """
            SELECT n FROM Notification n
            WHERE n.branch.id IN :branchIds
              AND NOT EXISTS (SELECT r.id FROM NotificationRead r WHERE r.notificationId = n.id AND r.userId = :userId)
            ORDER BY n.createdAt DESC
            """,
            countQuery = """
            SELECT COUNT(n) FROM Notification n
            WHERE n.branch.id IN :branchIds
              AND NOT EXISTS (SELECT r.id FROM NotificationRead r WHERE r.notificationId = n.id AND r.userId = :userId)
            """)
    Page<Notification> findUnread(@Param("branchIds") List<UUID> branchIds, @Param("userId") UUID userId,
                                  Pageable pageable);

    /** Every id this user has not read yet - what "mark all as read" works through. */
    @Query("""
            SELECT n.id FROM Notification n
            WHERE n.branch.id IN :branchIds
              AND NOT EXISTS (SELECT r.id FROM NotificationRead r WHERE r.notificationId = n.id AND r.userId = :userId)
            """)
    List<UUID> findUnreadIds(@Param("branchIds") List<UUID> branchIds, @Param("userId") UUID userId);

    @Query("""
            SELECT COUNT(n) FROM Notification n
            WHERE n.branch.id IN :branchIds
              AND NOT EXISTS (SELECT r.id FROM NotificationRead r WHERE r.notificationId = n.id AND r.userId = :userId)
            """)
    long countUnread(@Param("branchIds") List<UUID> branchIds, @Param("userId") UUID userId);

    /** Still waiting for a decision (pending or on hold), whether read or not, newest first. */
    @Query(value = """
            SELECT n FROM Notification n
            WHERE n.branch.id IN :branchIds AND n.reviewStatus IN (
                com.company.erp.notification.ReviewStatus.PENDING, com.company.erp.notification.ReviewStatus.ON_HOLD)
            ORDER BY n.createdAt DESC
            """,
            countQuery = """
            SELECT COUNT(n) FROM Notification n
            WHERE n.branch.id IN :branchIds AND n.reviewStatus IN (
                com.company.erp.notification.ReviewStatus.PENDING, com.company.erp.notification.ReviewStatus.ON_HOLD)
            """)
    Page<Notification> findToReview(@Param("branchIds") List<UUID> branchIds, Pageable pageable);

    @Query("""
            SELECT COUNT(n) FROM Notification n
            WHERE n.branch.id IN :branchIds AND n.reviewStatus IN (
                com.company.erp.notification.ReviewStatus.PENDING, com.company.erp.notification.ReviewStatus.ON_HOLD)
            """)
    long countToReview(@Param("branchIds") List<UUID> branchIds);

    List<Notification> findByReferenceTypeAndReferenceId(String referenceType, UUID referenceId);
}
