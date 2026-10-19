package com.company.erp.purchase;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * All the arithmetic of a global purchase's journey in one place, with no database or Spring in it so it is easy
 * to test: shipment status, where the goods are now, days spent at each stop, total transit days and delay.
 *
 * Nothing here ever changes stock. Reaching the destination only changes the shipment; stock moves when someone
 * confirms the quantity counted at the warehouse.
 */
public final class ShipmentCalculator {

    private ShipmentCalculator() {
    }

    /** Days at one stop; {@code ongoing} means the goods are still there, so the count runs up to today. */
    public record StageMetrics(Integer daysAtStage, boolean ongoing) {
    }

    public record Summary(ShipmentStatus status, String currentLocation, String currentCountry,
                          LocalDate actualArrivalDate, Integer totalTransitDays, int delayDays,
                          List<StageMetrics> stageMetrics) {
        public boolean delayed() {
            return delayDays > 0;
        }
    }

    /**
     * @param orderStatus    the purchase order's own status (a cancelled order is never "delayed")
     * @param expectedArrival the agreed arrival date at the destination (may be null)
     * @param stages         the route, in order
     * @param today          the date to count up to for things still in progress
     * @param fullyReceivedOn the date the last delivery was booked in, if the order is fully received (else null)
     */
    public static Summary summarise(PurchaseOrderStatus orderStatus, LocalDate expectedArrival,
                                    List<PurchaseShipmentStage> stages, LocalDate today, LocalDate fullyReceivedOn) {
        int n = stages.size();

        int lastReached = -1;
        boolean shipped = false;
        for (int i = 0; i < n; i++) {
            StageStatus s = stages.get(i).getStatus();
            if (s.isReached()) {
                lastReached = i;
                if (s == StageStatus.DEPARTED || i > 0) {
                    shipped = true;
                }
            }
        }

        ShipmentStatus status;
        if (n > 0 && lastReached == n - 1 && stages.get(n - 1).getArrivalDate() != null) {
            status = ShipmentStatus.ARRIVED;
        } else if (shipped) {
            status = ShipmentStatus.IN_TRANSIT;
        } else {
            status = ShipmentStatus.NOT_SHIPPED;
        }

        LocalDate actualArrival = status == ShipmentStatus.ARRIVED ? stages.get(n - 1).getArrivalDate() : null;

        String currentLocation = null;
        String currentCountry = null;
        if (lastReached >= 0) {
            PurchaseShipmentStage current = stages.get(lastReached);
            currentCountry = current.getCountry();
            if (current.getStatus() == StageStatus.DEPARTED && lastReached + 1 < n) {
                currentLocation = "In transit: " + label(current) + " \u2192 " + label(stages.get(lastReached + 1));
            } else if (status == ShipmentStatus.ARRIVED) {
                currentLocation = "Arrived: " + label(current);
            } else {
                currentLocation = "At " + label(current);
            }
        }

        List<StageMetrics> metrics = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            PurchaseShipmentStage s = stages.get(i);
            if (!s.getStatus().isReached() || s.getArrivalDate() == null) {
                metrics.add(new StageMetrics(null, false));
                continue;
            }
            LocalDate end;
            boolean ongoing;
            if (s.getDepartureDate() != null) {
                end = s.getDepartureDate();
                ongoing = false;
            } else if (i == n - 1 && fullyReceivedOn != null) {
                end = fullyReceivedOn;
                ongoing = false;
            } else {
                end = today;
                ongoing = true;
            }
            metrics.add(new StageMetrics((int) Math.max(0, ChronoUnit.DAYS.between(s.getArrivalDate(), end)), ongoing));
        }

        LocalDate start = null;
        for (PurchaseShipmentStage s : stages) {
            if (s.getStatus().isReached() && s.getDepartureDate() != null) {
                start = s.getDepartureDate();
                break;
            }
        }
        Integer totalTransitDays = null;
        if (start != null) {
            LocalDate end = actualArrival != null ? actualArrival : today;
            totalTransitDays = (int) Math.max(0, ChronoUnit.DAYS.between(start, end));
        }

        int delay = 0;
        if (expectedArrival != null && orderStatus != PurchaseOrderStatus.CANCELLED) {
            if (status == ShipmentStatus.ARRIVED) {
                delay = (int) Math.max(0, ChronoUnit.DAYS.between(expectedArrival, actualArrival));
            } else if (orderStatus != PurchaseOrderStatus.RECEIVED) {
                delay = (int) Math.max(0, ChronoUnit.DAYS.between(expectedArrival, today));
            }
        }

        return new Summary(status, currentLocation, currentCountry, actualArrival, totalTransitDays, delay,
                List.copyOf(metrics));
    }

    /** "Djibouti Port" if a place was given, otherwise the country. */
    public static String label(PurchaseShipmentStage s) {
        return s.getLocation() == null || s.getLocation().isBlank() ? s.getCountry() : s.getLocation();
    }

    /** "China \u2192 Djibouti \u2192 Ethiopia \u2192 Main Warehouse" */
    public static String route(List<PurchaseShipmentStage> stages) {
        List<String> names = new ArrayList<>();
        for (PurchaseShipmentStage s : stages) {
            names.add(label(s));
        }
        return String.join(" \u2192 ", names);
    }

    /** The route with the recorded dates: "China (dep 2026-01-02) \u2192 Djibouti (arr 2026-01-12, dep 2026-01-15) ...". */
    public static String routeWithDates(List<PurchaseShipmentStage> stages) {
        List<String> parts = new ArrayList<>();
        for (PurchaseShipmentStage s : stages) {
            List<String> dates = new ArrayList<>();
            if (s.getArrivalDate() != null) {
                dates.add("arr " + s.getArrivalDate());
            }
            if (s.getDepartureDate() != null) {
                dates.add("dep " + s.getDepartureDate());
            }
            String status = s.getStatus() == StageStatus.PENDING ? "planned" : s.getStatus().name().toLowerCase();
            parts.add(label(s) + " (" + (dates.isEmpty() ? status : String.join(", ", dates) + ", " + status) + ")");
        }
        return String.join(" \u2192 ", parts);
    }
}
