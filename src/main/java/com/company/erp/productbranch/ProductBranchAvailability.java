package com.company.erp.productbranch;

import com.company.erp.common.audit.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * A row here means "this product belongs at this branch" - presence is the whole fact; there is
 * no boolean to flip. A product with NO row for a branch is invisible there: left out of that
 * branch's product list, POS, purchases, batches, and transfers to or from it (see
 * ProductService#search, which is the one place this is enforced - every one of those screens
 * searches through it).
 *
 * New products and new branches start fully checked: ProductService#create and BranchService's
 * branch creation each seed one row per existing counterpart, so nothing is invisible until
 * someone deliberately unchecks it on the branch's product checklist.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "product_branch_availability", uniqueConstraints = @UniqueConstraint(columnNames = {"product_id", "branch_id"}))
public class ProductBranchAvailability extends BaseEntity {

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "branch_id", nullable = false)
    private UUID branchId;

    public ProductBranchAvailability(UUID productId, UUID branchId) {
        this.productId = productId;
        this.branchId = branchId;
    }
}
