package com.company.erp.pos.dto;

import com.company.erp.expense.ExpenseCategory;
import com.company.erp.payment.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * A cost that belongs to this one sale (loading labour, delivery, packaging...), entered at checkout.
 * It is the shop's cost, not something the customer is charged: it never changes the sale total or amount
 * paid. paymentMethod defaults to CASH (paid out of the till).
 */
public record CheckoutExpenseRequest(
        @NotNull ExpenseCategory category,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal amount,
        @NotBlank @Size(max = 400) String description,
        PaymentMethod paymentMethod
) {
}
