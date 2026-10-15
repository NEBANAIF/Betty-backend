package com.company.erp.product;

import com.company.erp.common.audit.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


/**
 * Product is GLOBAL - one row per product across the entire company. It
 * deliberately has NO branchId, NO stock, NO branchStock field. Stock is
 * always derived from Inventory (aggregate) / ProductBatch (source of
 * truth), added in Phase 4.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "products", uniqueConstraints = {
        @UniqueConstraint(name = "uk_product_sku", columnNames = "sku")
})
public class Product extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String sku;

    private String description;

    @Column(nullable = false)
    private String unit;

    @Column(name = "reorder_level", nullable = false)
    private Integer reorderLevel = 0;

    @Column(nullable = false)
    private boolean active = true;
}
