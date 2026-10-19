package com.company.erp.purchase;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ShipmentCalculatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 3, 1);

    private static PurchaseShipmentStage stage(String country, String location, String arrival, String departure,
                                               StageStatus status) {
        PurchaseShipmentStage s = new PurchaseShipmentStage();
        s.setCountry(country);
        s.setLocation(location);
        s.setArrivalDate(arrival == null ? null : LocalDate.parse(arrival));
        s.setDepartureDate(departure == null ? null : LocalDate.parse(departure));
        s.setStatus(status);
        return s;
    }

    private List<PurchaseShipmentStage> chinaDjiboutiEthiopia() {
        return List.of(
                stage("China", "Shanghai Port", null, "2026-01-02", StageStatus.DEPARTED),
                stage("Djibouti", "Djibouti Port", "2026-01-20", "2026-02-05", StageStatus.DEPARTED),
                stage("Ethiopia", "Modjo Dry Port", "2026-02-10", null, StageStatus.ARRIVED),
                stage("Ethiopia", "Main Warehouse", null, null, StageStatus.PENDING));
    }

    @Test
    void worksOutStatusLocationDaysAtEachStopAndTotalTransit() {
        var s = ShipmentCalculator.summarise(PurchaseOrderStatus.APPROVED, LocalDate.parse("2026-02-20"),
                chinaDjiboutiEthiopia(), TODAY, null);

        assertThat(s.status()).isEqualTo(ShipmentStatus.IN_TRANSIT);
        assertThat(s.currentCountry()).isEqualTo("Ethiopia");
        assertThat(s.currentLocation()).isEqualTo("At Modjo Dry Port");
        assertThat(s.stageMetrics().get(0).daysAtStage()).isNull();      // no arrival recorded at the origin
        assertThat(s.stageMetrics().get(1).daysAtStage()).isEqualTo(16); // 20 Jan -> 5 Feb
        assertThat(s.stageMetrics().get(1).ongoing()).isFalse();
        assertThat(s.stageMetrics().get(2).daysAtStage()).isEqualTo(19); // 10 Feb -> today
        assertThat(s.stageMetrics().get(2).ongoing()).isTrue();
        assertThat(s.stageMetrics().get(3).daysAtStage()).isNull();      // not reached
        assertThat(s.totalTransitDays()).isEqualTo(58);                  // 2 Jan -> today
        assertThat(s.delayDays()).isEqualTo(9);                          // expected 20 Feb
        assertThat(s.delayed()).isTrue();
    }

    @Test
    void routeTextsListEveryStop() {
        assertThat(ShipmentCalculator.route(chinaDjiboutiEthiopia()))
                .isEqualTo("Shanghai Port \u2192 Djibouti Port \u2192 Modjo Dry Port \u2192 Main Warehouse");
        assertThat(ShipmentCalculator.routeWithDates(chinaDjiboutiEthiopia())).contains("dep 2026-01-02");
    }

    @Test
    void arrivingAtTheDestinationIsArrivedButDaysKeepCountingUntilTheStockIsReceived() {
        var route = List.of(
                stage("China", null, null, "2026-01-02", StageStatus.DEPARTED),
                stage("Ethiopia", "Main Warehouse", "2026-02-10", null, StageStatus.ARRIVED));

        var waiting = ShipmentCalculator.summarise(PurchaseOrderStatus.APPROVED, LocalDate.parse("2026-02-15"), route, TODAY, null);
        assertThat(waiting.status()).isEqualTo(ShipmentStatus.ARRIVED);
        assertThat(waiting.actualArrivalDate()).isEqualTo(LocalDate.parse("2026-02-10"));
        assertThat(waiting.totalTransitDays()).isEqualTo(39);
        assertThat(waiting.delayDays()).isZero();
        assertThat(waiting.stageMetrics().get(1).ongoing()).isTrue();

        var received = ShipmentCalculator.summarise(PurchaseOrderStatus.RECEIVED, LocalDate.parse("2026-02-15"), route,
                TODAY, LocalDate.parse("2026-02-12"));
        assertThat(received.stageMetrics().get(1).daysAtStage()).isEqualTo(2);
        assertThat(received.stageMetrics().get(1).ongoing()).isFalse();
    }

    @Test
    void arrivingAfterTheExpectedDateCountsTheDaysLate() {
        var route = List.of(
                stage("China", null, null, "2026-01-02", StageStatus.DEPARTED),
                stage("Ethiopia", "Main Warehouse", "2026-02-10", null, StageStatus.ARRIVED));
        var s = ShipmentCalculator.summarise(PurchaseOrderStatus.APPROVED, LocalDate.parse("2026-02-01"), route, TODAY, null);
        assertThat(s.delayDays()).isEqualTo(9);
    }

    @Test
    void anUnshippedRoutePastItsExpectedDateIsDelayedButCancelledOrReceivedOrdersNeverAre() {
        var plan = List.of(stage("China", null, null, null, StageStatus.PENDING),
                stage("Ethiopia", null, null, null, StageStatus.PENDING));
        var expected = LocalDate.parse("2026-02-01");

        var s = ShipmentCalculator.summarise(PurchaseOrderStatus.APPROVED, expected, plan, TODAY, null);
        assertThat(s.status()).isEqualTo(ShipmentStatus.NOT_SHIPPED);
        assertThat(s.currentCountry()).isNull();
        assertThat(s.totalTransitDays()).isNull();
        assertThat(s.delayDays()).isEqualTo(28);

        assertThat(ShipmentCalculator.summarise(PurchaseOrderStatus.CANCELLED, expected, plan, TODAY, null).delayDays()).isZero();
        assertThat(ShipmentCalculator.summarise(PurchaseOrderStatus.RECEIVED, expected, plan, TODAY, null).delayDays()).isZero();
    }

    @Test
    void goodsThatHaveLeftAStopAreInTransitToTheNextOne() {
        var route = List.of(
                stage("China", "Shanghai", null, "2026-02-20", StageStatus.DEPARTED),
                stage("Djibouti", null, null, null, StageStatus.PENDING),
                stage("Ethiopia", null, null, null, StageStatus.PENDING));
        var s = ShipmentCalculator.summarise(PurchaseOrderStatus.APPROVED, LocalDate.parse("2026-04-01"), route, TODAY, null);
        assertThat(s.status()).isEqualTo(ShipmentStatus.IN_TRANSIT);
        assertThat(s.currentLocation()).isEqualTo("In transit: Shanghai \u2192 Djibouti");
        assertThat(s.currentCountry()).isEqualTo("China");
        assertThat(s.totalTransitDays()).isEqualTo(9);
        assertThat(s.delayDays()).isZero();
    }
}
