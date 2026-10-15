package com.company.erp.notification;

import com.company.erp.common.dto.PageResponse;
import com.company.erp.notification.dto.NotificationCountsResponse;
import com.company.erp.notification.dto.NotificationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * The Notifications page and the bell. Seeing the feed needs ACTIVITY_VIEW; putting an activity on
 * hold or approving it needs ACTIVITY_REVIEW. Which activities appear is limited to the branches
 * the caller may see (see NotificationService).
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    @PreAuthorize("hasAuthority('ACTIVITY_VIEW')")
    public PageResponse<NotificationResponse> list(
            @RequestParam(defaultValue = "UNREAD") NotificationTab tab,
            Pageable pageable) {
        return notificationService.list(tab, pageable);
    }

    /** What the bell shows: unread count, and how many activities still await review. */
    @GetMapping("/counts")
    @PreAuthorize("hasAuthority('ACTIVITY_VIEW')")
    public NotificationCountsResponse counts() {
        return notificationService.counts();
    }

    @PostMapping("/{id}/read")
    @PreAuthorize("hasAuthority('ACTIVITY_VIEW')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@PathVariable UUID id) {
        notificationService.markRead(id);
    }

    @PostMapping("/read-all")
    @PreAuthorize("hasAuthority('ACTIVITY_VIEW')")
    public Map<String, Integer> markAllRead() {
        return Map.of("marked", notificationService.markAllRead());
    }

    @PostMapping("/{id}/hold")
    @PreAuthorize("hasAuthority('ACTIVITY_REVIEW')")
    public NotificationResponse hold(@PathVariable UUID id) {
        return notificationService.hold(id);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('ACTIVITY_REVIEW')")
    public NotificationResponse approve(@PathVariable UUID id) {
        return notificationService.approve(id);
    }
}
