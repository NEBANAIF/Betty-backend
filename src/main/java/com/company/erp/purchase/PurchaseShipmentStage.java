package com.company.erp.purchase;

import com.company.erp.common.audit.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/** One stop on a global purchase's route, e.g. "Djibouti Port, Djibouti". Stops are kept in route order. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "purchase_shipment_stages")
public class PurchaseShipmentStage extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_order_id", nullable = false)
    private PurchaseOrder purchaseOrder;

    /** 1 = where the goods start; the highest number is the final destination. */
    @Column(name = "stage_order", nullable = false)
    private Integer stageOrder;

    @Column(nullable = false, length = 100)
    private String country;

    /** Port, city or warehouse inside that country (optional). */
    @Column(length = 150)
    private String location;

    @Column(name = "arrival_date")
    private LocalDate arrivalDate;

    @Column(name = "departure_date")
    private LocalDate departureDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StageStatus status = StageStatus.PENDING;

    @Column(length = 300)
    private String notes;

    /** "Djibouti Port, Djibouti" - or just the country when no place was given. */
    public String getDisplayName() {
        return location == null || location.isBlank() ? country : location + ", " + country;
    }
}
