package com.company.erp.pos.dto;

import com.company.erp.payment.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The full POS checkout payload (spec section 11).
 *
 * - branchId is only ever a target, checked against the authenticated
 *   user's actual branch access inside PosCheckoutService - never trusted
 *   as authorization on its own.
 * - discountAmount is a flat amount subtracted from the item subtotal.
 * - amountPaid may be less than the (discounted) total, in which case
 *   customerId becomes required and the remainder is tracked as credit - a
 *   Loan is created for it (Phase 7); amountPaid may also be 0 for a
 *   fully-on-credit sale.
 * - bankId is the registered bank a BANK payment went into (chosen from the POS dropdown);
 *   accountReference is then just the transfer/receipt reference the cashier typed. bankName
 *   is only for older clients that type a bank name instead of choosing one.
 * - loanDueDate only matters when a loan gets created (remainingAmount > 0);
 *   if omitted, the loan defaults to a 30-day due date. Ignored entirely
 *   when the sale is paid in full.
 */
public record CheckoutRequest(
        @NotNull UUID branchId,
        UUID customerId,
        @NotEmpty @Valid List<CheckoutItemRequest> items,
        @DecimalMin(value = "0.0", inclusive = true) BigDecimal discountAmount,
        @NotNull PaymentMethod paymentMethod,
        @NotNull @DecimalMin(value = "0.0", inclusive = true) BigDecimal amountPaid,
        String bankName,
        String accountReference,
        LocalDate loanDueDate,
        UUID bankId
) {
}
