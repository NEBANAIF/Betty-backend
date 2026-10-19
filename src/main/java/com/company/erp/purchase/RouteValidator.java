package com.company.erp.purchase;

import com.company.erp.common.exception.BusinessRuleViolationException;
import com.company.erp.purchase.dto.ShipmentStageRequest;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns the route a user entered into stops, rejecting anything that cannot be true: a route shorter than two
 * stops, one that does not run from the origin to the destination, dates out of order, goods at a stop before they
 * left the previous one, or "reached" stops with dates in the future. No database or Spring in here, so it is
 * quick to test.
 */
public final class RouteValidator {

    private RouteValidator() {
    }

    public static List<PurchaseShipmentStage> build(List<ShipmentStageRequest> requests, String origin,
                                                     String destination, LocalDate today) {
        if (requests == null || requests.size() < 2) {
            throw new BusinessRuleViolationException(
                    "The route needs at least two stops: where the goods start and where they end up");
        }
        int n = requests.size();
        if (!requests.get(0).country().trim().equalsIgnoreCase(origin)) {
            throw new BusinessRuleViolationException("The route must start in the origin country (" + origin + ")");
        }
        if (!requests.get(n - 1).country().trim().equalsIgnoreCase(destination)) {
            throw new BusinessRuleViolationException("The route must end in the destination country (" + destination + ")");
        }

        List<PurchaseShipmentStage> out = new ArrayList<>();
        boolean seenPending = false;
        PurchaseShipmentStage previous = null;
        for (int i = 0; i < n; i++) {
            ShipmentStageRequest r = requests.get(i);
            StageStatus status = r.status() != null ? r.status() : StageStatus.PENDING;
            String where = "Stop " + (i + 1) + " (" + r.country().trim() + ")";

            if (r.arrivalDate() != null && r.departureDate() != null && r.departureDate().isBefore(r.arrivalDate())) {
                throw new BusinessRuleViolationException(where + ": the departure date is before the arrival date");
            }
            if (status.isReached()) {
                if (seenPending) {
                    throw new BusinessRuleViolationException(
                            where + ": cannot be marked as reached while an earlier stop is still pending");
                }
                if (i > 0 && r.arrivalDate() == null) {
                    throw new BusinessRuleViolationException(where + ": enter the arrival date");
                }
                if (i > 0 && previous.getStatus() != StageStatus.DEPARTED) {
                    throw new BusinessRuleViolationException(where + ": the goods cannot be here before they left "
                            + ShipmentCalculator.label(previous));
                }
                if (status == StageStatus.DEPARTED) {
                    if (r.departureDate() == null) {
                        throw new BusinessRuleViolationException(where + ": enter the departure date");
                    }
                    if (i == n - 1) {
                        throw new BusinessRuleViolationException(
                                where + ": the final stop is the destination - it cannot be marked as departed");
                    }
                } else if (r.departureDate() != null) {
                    throw new BusinessRuleViolationException(
                            where + ": has a departure date, so its status must be Departed");
                }
                if ((r.arrivalDate() != null && r.arrivalDate().isAfter(today))
                        || (r.departureDate() != null && r.departureDate().isAfter(today))) {
                    throw new BusinessRuleViolationException(
                            where + ": dates for a stop the goods have already reached cannot be in the future");
                }
            } else {
                seenPending = true;
            }
            if (previous != null && previous.getDepartureDate() != null && r.arrivalDate() != null
                    && r.arrivalDate().isBefore(previous.getDepartureDate())) {
                throw new BusinessRuleViolationException(
                        where + ": the arrival date is before the goods left the previous stop");
            }

            PurchaseShipmentStage stage = new PurchaseShipmentStage();
            stage.setStageOrder(i + 1);
            stage.setCountry(r.country().trim());
            stage.setLocation(r.location() == null || r.location().isBlank() ? null : r.location().trim());
            stage.setArrivalDate(r.arrivalDate());
            stage.setDepartureDate(r.departureDate());
            stage.setStatus(status);
            stage.setNotes(r.notes() == null || r.notes().isBlank() ? null : r.notes().trim());
            out.add(stage);
            previous = stage;
        }
        return out;
    }
}
