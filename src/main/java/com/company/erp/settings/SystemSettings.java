package com.company.erp.settings;

import com.company.erp.common.audit.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * App-wide toggles that aren't tied to any one branch or role - as opposed
 * to Permission (per-role) or Branch-scoped data. Deliberately a single row:
 * there is exactly one of these, created on first use (see
 * SystemSettingsService#get), not a key/value table, because right now
 * there's exactly one setting. If a second one shows up, this can grow
 * columns rather than needing a redesign.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "system_settings")
public class SystemSettings extends BaseEntity {

    /**
     * When true, the cost-price field on the "Initial Stock" section of the
     * New Product form is locked (shown disabled) - someone recording
     * products who shouldn't be setting/seeing cost still can enter a
     * quantity, but the batch opens at cost 0 until a user with STOCK_ADJUST
     * corrects it via Inventory. Enforced only in the frontend UI; the
     * adjust-stock endpoint itself is unaffected; server-side enforcement
     * would mean also blocking normal stock adjustments made with a
     * cost, which is a bigger behavior change than this switch is meant for.
     */
    @Column(nullable = false)
    private boolean lockCostPrice = false;

    /**
     * When true, a product with zero available stock at the branch being sold from is left out of
     * the POS product search - a cashier can't ring up something that isn't there. Deliberately
     * scoped to POS only: Purchases, Batches and Transfers must still be able to find an
     * out-of-stock product, since finding it is the whole point of restocking it.
     */
    @Column(nullable = false)
    private boolean hideOutOfStockAtPos = false;

    /** Display name of the app (login screen, sidebar, browser tab). Set from Settings. */
    public static final String DEFAULT_APP_NAME = "ERP Suite";

    @Column(name = "app_name", nullable = false, length = 60)
    private String appName = DEFAULT_APP_NAME;

    /**
     * Look and feel chosen in Settings > Appearance (colours, fonts), stored as a small JSON object.
     * Null means "the built-in look". Only validated values are ever stored - see ThemeSettingsValidator.
     */
    @Column(name = "theme_json", length = 4000)
    private String themeJson;

    /**
     * Wording, layout and colours of the sign-in page (Settings > Login page), as a small JSON object.
     * Null means "the built-in sign-in page". Only validated values are stored - see LoginSettingsValidator.
     */
    @Column(name = "login_json", length = 4000)
    private String loginJson;
}
