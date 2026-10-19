package com.company.erp.purchase;

import com.company.erp.common.exception.BusinessRuleViolationException;
import com.company.erp.purchase.dto.ShipmentStageRequest;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RouteValidatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 3, 1);

    private static ShipmentStageRequest stop(String country, String arrival, String departure, StageStatus status) {
        return new ShipmentStageRequest(country, null, arrival == null ? null : LocalDate.parse(arrival),
                departure == null ? null : LocalDate.parse(departure), status, null);
    }

    private static void rejects(List<ShipmentStageRequest> route, String messagePart) {
        assertThatThrownBy(() -> RouteValidator.build(route, "China", "Ethiopia", TODAY))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining(messagePart);
    }

    @Test
    void buildsTheStopsInOrderAndIgnoresCaseOfTheEndCountries() {
        var stages = RouteValidator.build(List.of(
                stop("China", null, "2026-01-02", StageStatus.DEPARTED),
                stop("Djibouti", "2026-01-20", "2026-02-05", StageStatus.DEPARTED),
                stop("ethiopia", null, null, null)), "china", "ETHIOPIA", TODAY);
        assertThat(stages).extracting(PurchaseShipmentStage::getStageOrder).containsExactly(1, 2, 3);
        assertThat(stages.get(2).getStatus()).isEqualTo(StageStatus.PENDING);
    }

    @Test
    void routeNeedsTwoStopsFromOriginToDestination() {
        rejects(List.of(stop("China", null, null, null)), "at least two");
        rejects(List.of(stop("India", null, null, null), stop("Ethiopia", null, null, null)), "start in the origin");
        rejects(List.of(stop("China", null, null, null), stop("Kenya", null, null, null)), "end in the destination");
    }

    @Test
    void datesMustMakeSense() {
        rejects(List.of(stop("China", null, "2026-01-02", StageStatus.DEPARTED),
                stop("Djibouti", "2026-01-10", "2026-01-05", StageStatus.DEPARTED),
                stop("Ethiopia", null, null, null)), "departure date is before");
        rejects(List.of(stop("China", null, "2026-01-10", StageStatus.DEPARTED),
                stop("Ethiopia", "2026-01-05", null, StageStatus.ARRIVED)), "before the goods left");
        rejects(List.of(stop("China", null, "2026-05-02", StageStatus.DEPARTED),
                stop("Ethiopia", null, null, null)), "future");
    }

    @Test
    void goodsCannotSkipAStop() {
        rejects(List.of(stop("China", null, null, StageStatus.ARRIVED),
                stop("Ethiopia", "2026-01-05", null, StageStatus.ARRIVED)), "cannot be here before");
        rejects(List.of(stop("China", null, null, StageStatus.PENDING),
                stop("Djibouti", "2026-01-05", null, StageStatus.ARRIVED),
                stop("Ethiopia", null, null, null)), "still pending");
    }

    @Test
    void reachedStopsNeedTheirDates() {
        rejects(List.of(stop("China", null, "2026-01-02", StageStatus.DEPARTED),
                stop("Ethiopia", null, null, StageStatus.ARRIVED)), "enter the arrival date");
        rejects(List.of(stop("China", null, null, StageStatus.DEPARTED),
                stop("Ethiopia", null, null, null)), "enter the departure date");
        rejects(List.of(stop("China", null, "2026-01-02", StageStatus.DEPARTED),
                stop("Ethiopia", "2026-01-20", "2026-01-25", StageStatus.DEPARTED)), "cannot be marked as departed");
    }

    @Test
    void plannedDatesOnStopsNotYetReachedAreAllowed() {
        var stages = RouteValidator.build(List.of(
                stop("China", null, "2026-01-02", StageStatus.DEPARTED),
                stop("Ethiopia", "2026-06-01", null, StageStatus.PENDING)), "China", "Ethiopia", TODAY);
        assertThat(stages).hasSize(2);
    }
}
