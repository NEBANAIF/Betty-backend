package com.company.erp.purchase.dto;

import com.company.erp.purchase.StageStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** One stop on the route. {@code status} defaults to PENDING (not reached yet). */
public record ShipmentStageRequest(
        @NotBlank @Size(max = 100) String country,
        @Size(max = 150) String location,
        LocalDate arrivalDate,
        LocalDate departureDate,
        StageStatus status,
        @Size(max = 300) String notes
) {
}
