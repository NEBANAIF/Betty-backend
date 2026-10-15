package com.company.erp.bank;

import com.company.erp.common.audit.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A bank account the business receives payments into. Registered once on the Payments page and
 * then picked from a dropdown at the POS (and when recording a loan repayment), so every bank
 * payment is tied to a real, consistently spelled account.
 * <p>
 * Never deleted - payments refer to it - only deactivated, which removes it from the dropdowns.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "banks")
public class Bank extends BaseEntity {

    /** For example "Commercial Bank of Ethiopia". */
    @Column(nullable = false, length = 120)
    private String name;

    /** Who the account is in the name of (optional). */
    @Column(name = "account_name", length = 120)
    private String accountName;

    @Column(name = "account_number", nullable = false, length = 60)
    private String accountNumber;

    @Column(length = 255)
    private String notes;

    @Column(nullable = false)
    private boolean active = true;
}
