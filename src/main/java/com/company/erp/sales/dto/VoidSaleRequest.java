package com.company.erp.sales.dto;

import jakarta.validation.constraints.NotBlank;

/** A reason is required - it's the only thing distinguishing an audit-trail void from an unexplained one. */
public record VoidSaleRequest(@NotBlank String reason) {
}
