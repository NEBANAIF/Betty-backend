package com.company.erp.purchase;

import com.company.erp.branch.Branch;
import com.company.erp.common.audit.BaseEntity;
import com.company.erp.supplier.Supplier;
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
import org.hibernate.annotations.BatchSize;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Purchasing workflow (spec section 17):
 * Supplier -> PurchaseOrder -> Receive Purchase -> automatic ProductBatch
 * creation -> increased branch stock.
 *
 * `total` is the ORDERED total (sum of quantity * unitCost across items),
 * fixed at creation - it does not change as receiving happens, since it
 * represents what was ordered/agreed with the supplier, not what's been
 * physically received so far (that's tracked per-item via receivedQuantity
 * and surfaced in PurchaseOrderResponse).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "purchase_orders")
public class PurchaseOrder extends BaseEntity {

    /** Human-readable, e.g. PO-20260115-0007. Generated at creation, never reused. */
    @Column(name = "order_number", nullable = false, unique = true)
    private String orderNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @Column(name = "order_date", nullable = false)
    private LocalDate orderDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PurchaseOrderStatus status;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal total;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private User approvedBy;

    @OneToMany(mappedBy = "purchaseOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<PurchaseOrderItem> items = new ArrayList<>();

    // ---- Global (imported) purchases. A LOCAL order leaves all of these empty. -------------------------------

    @Enumerated(EnumType.STRING)
    @Column(name = "purchase_type", nullable = false, length = 10)
    private PurchaseType purchaseType = PurchaseType.LOCAL;

    @Column(name = "origin_country", length = 100)
    private String originCountry;

    @Column(name = "destination_country", length = 100)
    private String destinationCountry;

    @Enumerated(EnumType.STRING)
    @Column(name = "shipping_method", length = 20)
    private ShippingMethod shippingMethod;

    /** Currency the supplier is paid in. Item unit costs on a global order are in this currency. */
    @Column(length = 10)
    private String currency;

    /** Local-currency value of one unit of {@link #currency}; stock received is costed at unitCost x this. */
    @Column(name = "exchange_rate", nullable = false, precision = 18, scale = 6)
    private BigDecimal exchangeRate = BigDecimal.ONE;

    @Column(name = "shipping_cost", precision = 14, scale = 2)
    private BigDecimal shippingCost;

    @Column(name = "expected_arrival_date")
    private LocalDate expectedArrivalDate;

    // Worked out from the route every time it is saved (refreshShipmentSummary); stored so lists can filter on them.
    @Column(name = "actual_arrival_date")
    private LocalDate actualArrivalDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "shipment_status", length = 20)
    private ShipmentStatus shipmentStatus;

    @Column(name = "current_location", length = 250)
    private String currentLocation;

    @Column(name = "current_country", length = 100)
    private String currentCountry;

    @Column(name = "arrival_year")
    private Integer arrivalYear;

    @Column(name = "arrival_month")
    private Integer arrivalMonth;

    @OneToMany(mappedBy = "purchaseOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("stageOrder ASC")
    @BatchSize(size = 50)
    private List<PurchaseShipmentStage> stages = new ArrayList<>();

    @OneToMany(mappedBy = "purchaseOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    @BatchSize(size = 50)
    private List<PurchaseReceipt> receipts = new ArrayList<>();

    public boolean isGlobal() {
        return purchaseType == PurchaseType.GLOBAL;
    }

    public void addStage(PurchaseShipmentStage stage) {
        stage.setPurchaseOrder(this);
        stages.add(stage);
    }

    public void addReceipt(PurchaseReceipt receipt) {
        receipt.setPurchaseOrder(this);
        receipts.add(receipt);
    }

    /** Date of the most recent delivery booked in, once the order is fully received (otherwise null). */
    public LocalDate fullyReceivedOn() {
        if (status != PurchaseOrderStatus.RECEIVED) {
            return null;
        }
        return receipts.stream().map(PurchaseReceipt::getReceivedDate).max(LocalDate::compareTo).orElse(null);
    }

    /**
     * Recomputes the stored shipment summary (status, current location, actual arrival, arrival month/year) from
     * the route. Call it whenever the route or the expected date changes. Never touches stock.
     */
    public void refreshShipmentSummary(LocalDate today) {
        if (!isGlobal()) {
            shipmentStatus = null;
            currentLocation = null;
            currentCountry = null;
            actualArrivalDate = null;
            arrivalYear = null;
            arrivalMonth = null;
            return;
        }
        ShipmentCalculator.Summary summary =
                ShipmentCalculator.summarise(status, expectedArrivalDate, stages, today, fullyReceivedOn());
        shipmentStatus = summary.status();
        currentLocation = summary.currentLocation();
        currentCountry = summary.currentCountry();
        actualArrivalDate = summary.actualArrivalDate();
        LocalDate arrival = actualArrivalDate != null ? actualArrivalDate : expectedArrivalDate;
        arrivalYear = arrival != null ? arrival.getYear() : null;
        arrivalMonth = arrival != null ? arrival.getMonthValue() : null;
    }

    public void addItem(PurchaseOrderItem item) {
        item.setPurchaseOrder(this);
        items.add(item);
    }

    public boolean isFullyReceived() {
        return items.stream().allMatch(i -> i.getReceivedQuantity() >= i.getQuantity());
    }

    public boolean isPartiallyReceived() {
        return items.stream().anyMatch(i -> i.getReceivedQuantity() > 0);
    }
}
