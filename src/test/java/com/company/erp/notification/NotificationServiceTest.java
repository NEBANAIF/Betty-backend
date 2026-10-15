package com.company.erp.notification;

import com.company.erp.branch.Branch;
import com.company.erp.common.audit.BaseEntity;
import com.company.erp.common.exception.BusinessRuleViolationException;
import com.company.erp.common.exception.ResourceNotFoundException;
import com.company.erp.product.Product;
import com.company.erp.security.BranchAccessService;
import com.company.erp.security.UserPrincipal;
import com.company.erp.stockhistory.StockMovementType;
import com.company.erp.user.Role;
import com.company.erp.user.User;
import com.company.erp.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private NotificationReadRepository notificationReadRepository;
    @Mock private UserRepository userRepository;
    @Mock private BranchAccessService branchAccessService;

    private NotificationService service;

    private Branch branch;
    private Branch otherBranch;
    private User reviewer;

    @BeforeEach
    void setUp() {
        service = new NotificationService(notificationRepository, notificationReadRepository,
                userRepository, branchAccessService);

        branch = new Branch();
        branch.setName("Bole Store");
        setId(branch, UUID.randomUUID());
        otherBranch = new Branch();
        otherBranch.setName("Main Warehouse");
        setId(otherBranch, UUID.randomUUID());

        reviewer = new User();
        reviewer.setName("Store Manager");
        reviewer.setRole(Role.SUPER_ADMIN);
        setId(reviewer, UUID.randomUUID());

        lenient().when(branchAccessService.currentUser()).thenReturn(new UserPrincipal(reviewer));
        lenient().when(branchAccessService.resolveReadableBranchIds(BranchAccessService.BranchScope.ALL, null))
                .thenReturn(List.of(branch.getId()));
        lenient().when(userRepository.findById(reviewer.getId())).thenReturn(Optional.of(reviewer));
    }

    @Test
    void aSaleMovementIsNotReportedAsStockBecauseTheSaleItselfIsReported() {
        service.recordStockChange(product(), branch, -3, StockMovementType.SALE, "POS sale X", reviewer);
        service.recordStockChange(product(), branch, 3, StockMovementType.SALE_VOID, "Void of X", reviewer);

        verifyNoInteractions(notificationRepository);
    }

    @Test
    void manualStockChangesAreAnnouncedWhenEnteredNotAgainWhenApplied() {
        service.recordStockChange(product(), branch, 25, StockMovementType.BATCH_ADDED, "x", reviewer);
        service.recordStockChange(product(), branch, 5, StockMovementType.ADJUSTMENT, "x", reviewer);
        service.recordStockChange(product(), branch, -2, StockMovementType.DAMAGED, "x", reviewer);
        service.recordStockChange(product(), branch, -2, StockMovementType.LOST, "x", reviewer);

        verifyNoInteractions(notificationRepository);
    }

    @Test
    void aRegisteredSaleIsBornApprovedAndNeverWaitsForReview() {
        service.record(NotificationType.SALE_COMPLETED, "New sale", "S-1", branch, reviewer, "SALE", UUID.randomUUID());

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(saved.capture());
        assertThat(saved.getValue().getReviewStatus()).isEqualTo(ReviewStatus.APPROVED);
    }

    @Test
    void aVoidedSaleStillWaitsForReview() {
        service.record(NotificationType.SALE_VOIDED, "Sale voided", "S-1", branch, reviewer, "SALE", UUID.randomUUID());

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(saved.capture());
        assertThat(saved.getValue().getReviewStatus()).isEqualTo(ReviewStatus.PENDING);
    }

    @Test
    void aHeldStockChangeIsNotApprovedOrHeldThroughTheFeed() {
        Notification n = pending(branch);
        n.setReferenceType(NotificationService.STOCK_CHANGE_REQUEST);
        when(notificationRepository.findById(n.getId())).thenReturn(Optional.of(n));

        assertThatThrownBy(() -> service.approve(n.getId())).isInstanceOf(BusinessRuleViolationException.class);
        assertThatThrownBy(() -> service.hold(n.getId())).isInstanceOf(BusinessRuleViolationException.class);
        assertThat(n.getReviewStatus()).isEqualTo(ReviewStatus.PENDING);
    }

    @Test
    void resolvingAStockChangeRequestMirrorsItOnItsNotification() {
        UUID requestId = UUID.randomUUID();
        Notification n = pending(branch);
        n.setReferenceType(NotificationService.STOCK_CHANGE_REQUEST);
        n.setReferenceId(requestId);
        when(notificationRepository.findByReferenceTypeAndReferenceId(NotificationService.STOCK_CHANGE_REQUEST, requestId))
                .thenReturn(List.of(n));

        service.resolveStockChangeRequest(requestId, ReviewStatus.REJECTED, reviewer);

        assertThat(n.getReviewStatus()).isEqualTo(ReviewStatus.REJECTED);
        assertThat(n.getReviewedBy()).isSameAs(reviewer);
    }

    @Test
    void aStockChangeBecomesAPendingNotificationWithAReadableMessage() {
        service.recordStockChange(product(), branch, 25, StockMovementType.PURCHASE, "PO-1", reviewer);

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(saved.capture());
        Notification n = saved.getValue();
        assertThat(n.getType()).isEqualTo(NotificationType.STOCK_CHANGE);
        assertThat(n.getTitle()).isEqualTo("Purchase received");
        assertThat(n.getMessage()).isEqualTo("Cable 2.5mm: +25 pcs at Bole Store — PO-1");
        assertThat(n.getBranch()).isSameAs(branch);
        assertThat(n.getActor()).isSameAs(reviewer);
        assertThat(n.getReviewStatus()).isEqualTo(ReviewStatus.PENDING);
    }

    @Test
    void stockGoingOutIsShownWithAMinusSign() {
        service.recordStockChange(product(), branch, -4, StockMovementType.TRANSFER_OUT, null, reviewer);

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(saved.capture());
        assertThat(saved.getValue().getTitle()).isEqualTo("Transfer sent out");
        assertThat(saved.getValue().getMessage()).isEqualTo("Cable 2.5mm: -4 pcs at Bole Store");
    }

    @Test
    void holdingThenApprovingWalksThroughTheStatusesAndMarksItReadForTheReviewer() {
        Notification n = pending(branch);
        when(notificationRepository.findById(n.getId())).thenReturn(Optional.of(n));
        when(notificationReadRepository.existsByNotificationIdAndUserId(n.getId(), reviewer.getId())).thenReturn(false);

        var held = service.hold(n.getId());
        assertThat(held.reviewStatus()).isEqualTo("ON_HOLD");
        assertThat(held.reviewedByName()).isEqualTo("Store Manager");
        assertThat(held.read()).isTrue();

        var approved = service.approve(n.getId());
        assertThat(approved.reviewStatus()).isEqualTo("APPROVED");
        verify(notificationReadRepository, atLeastOnce()).save(any(NotificationRead.class));
    }

    @Test
    void anActivityCanBeApprovedStraightFromPending() {
        Notification n = pending(branch);
        when(notificationRepository.findById(n.getId())).thenReturn(Optional.of(n));

        assertThat(service.approve(n.getId()).reviewStatus()).isEqualTo("APPROVED");
    }

    @Test
    void anApprovedActivityCannotBeHeldOrApprovedAgain() {
        Notification n = pending(branch);
        n.setReviewStatus(ReviewStatus.APPROVED);
        when(notificationRepository.findById(n.getId())).thenReturn(Optional.of(n));

        assertThatThrownBy(() -> service.hold(n.getId())).isInstanceOf(BusinessRuleViolationException.class);
        assertThatThrownBy(() -> service.approve(n.getId())).isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void anActivityAlreadyOnHoldCannotBeHeldTwice() {
        Notification n = pending(branch);
        n.setReviewStatus(ReviewStatus.ON_HOLD);
        when(notificationRepository.findById(n.getId())).thenReturn(Optional.of(n));

        assertThatThrownBy(() -> service.hold(n.getId())).isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void aNotificationOfABranchTheUserCannotSeeIsTreatedAsNotFound() {
        Notification n = pending(otherBranch);
        when(notificationRepository.findById(n.getId())).thenReturn(Optional.of(n));

        assertThatThrownBy(() -> service.approve(n.getId())).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.markRead(n.getId())).isInstanceOf(ResourceNotFoundException.class);
        assertThat(n.getReviewStatus()).isEqualTo(ReviewStatus.PENDING);
    }

    @Test
    void markingAlreadyReadAgainDoesNothing() {
        Notification n = pending(branch);
        when(notificationRepository.findById(n.getId())).thenReturn(Optional.of(n));
        when(notificationReadRepository.existsByNotificationIdAndUserId(n.getId(), reviewer.getId())).thenReturn(true);

        service.markRead(n.getId());

        verify(notificationReadRepository, never()).save(any());
    }

    @Test
    void markAllReadRecordsARowForEveryUnreadNotificationOfTheVisibleBranches() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        when(notificationRepository.findUnreadIds(List.of(branch.getId()), reviewer.getId())).thenReturn(List.of(a, b));

        assertThat(service.markAllRead()).isEqualTo(2);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<NotificationRead>> saved = ArgumentCaptor.forClass(List.class);
        verify(notificationReadRepository).saveAll(saved.capture());
        assertThat(saved.getValue()).extracting(NotificationRead::getNotificationId).containsExactly(a, b);
        assertThat(saved.getValue()).extracting(NotificationRead::getUserId).containsOnly(reviewer.getId());
    }

    private Notification pending(Branch forBranch) {
        Notification n = new Notification();
        n.setType(NotificationType.STOCK_CHANGE);
        n.setTitle("Batch added");
        n.setMessage("Cable 2.5mm: +25 pcs");
        n.setBranch(forBranch);
        n.setActor(reviewer);
        setId(n, UUID.randomUUID());
        return n;
    }

    private Product product() {
        Product p = new Product();
        p.setName("Cable 2.5mm");
        p.setUnit("pcs");
        return p;
    }

    private void setId(BaseEntity entity, UUID id) {
        try {
            var field = BaseEntity.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
