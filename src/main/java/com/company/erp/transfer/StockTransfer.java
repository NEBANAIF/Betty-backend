package com.company.erp.transfer;

import com.company.erp.branch.Branch;
import com.company.erp.common.audit.BaseEntity;
import com.company.erp.user.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A branch-to-branch stock move (spec section 19). Status only ever
 * advances PENDING -> APPROVED -> IN_TRANSIT -> RECEIVED, or is cut short
 * by REJECTED (from PENDING/APPROVED, before any stock has moved) or
 * CANCELLED (from PENDING only). Once IN_TRANSIT, the transfer MUST run to
 * RECEIVED - stock has already left the source branch, so there is no
 * "undo" path in this phase (that would require a formal reversal flow,
 * which isn't part of the spec's transfer lifecycle).
 *
 * Stock moves exactly twice, never more: once out of sourceBranch when
 * status becomes IN_TRANSIT, once into destinationBranch when status
 * becomes RECEIVED. See StockTransferService for where those two moments
 * are enforced.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "stock_transfers")
public class StockTransfer extends BaseEntity {

    @Column(name = "transfer_number", nullable = false, unique = true)
    private String transferNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_branch_id", nullable = false)
    private Branch sourceBranch;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "destination_branch_id", nullable = false)
    private Branch destinationBranch;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransferStatus status = TransferStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requested_by", nullable = false)
    private User requestedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private User approvedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "received_by")
    private User receivedBy;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "dispatched_at")
    private Instant dispatchedAt;

    @Column(name = "received_at")
    private Instant receivedAt;

    /** Populated on REJECTED - explains why the source branch declined the request. */
    private String rejectionReason;

    @OneToMany(mappedBy = "transfer", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<StockTransferItem> items = new ArrayList<>();

    public void addItem(StockTransferItem item) {
        item.setTransfer(this);
        items.add(item);
    }
}
