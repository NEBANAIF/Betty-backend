package com.company.erp.settings;

import com.company.erp.common.exception.BusinessRuleViolationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ThemeSettingsValidatorTest {

    @Test
    void blankOrEmptyMeansTheBuiltInLook() {
        assertThat(ThemeSettingsValidator.normalise(null)).isNull();
        assertThat(ThemeSettingsValidator.normalise("  ")).isNull();
        assertThat(ThemeSettingsValidator.normalise("{}")).isNull();
        assertThat(ThemeSettingsValidator.normalise("{\"sidebarBg\":\"\"}")).isNull();
    }

    @Test
    void validColoursFontsAndScaleAreKept() {
        String json = ThemeSettingsValidator.normalise(
                "{\"sidebarBg\":\"#1A2B3C\",\"bodyFont\":\"Poppins\",\"fontScale\":1.1}");
        assertThat(json).contains("\"sidebarBg\":\"#1a2b3c\"").contains("\"bodyFont\":\"Poppins\"").contains("1.1");
    }

    @Test
    void anythingThatCouldBreakOutOfCssIsRejected() {
        assertThatThrownBy(() -> ThemeSettingsValidator.normalise("{\"pageBg\":\"red;}body{display:none\"}"))
                .isInstanceOf(BusinessRuleViolationException.class);
        assertThatThrownBy(() -> ThemeSettingsValidator.normalise("{\"bodyFont\":\"Comic; x\"}"))
                .isInstanceOf(BusinessRuleViolationException.class);
        assertThatThrownBy(() -> ThemeSettingsValidator.normalise("{\"fontScale\":3}"))
                .isInstanceOf(BusinessRuleViolationException.class);
        assertThatThrownBy(() -> ThemeSettingsValidator.normalise("{\"surprise\":\"#ffffff\"}"))
                .isInstanceOf(BusinessRuleViolationException.class);
        assertThatThrownBy(() -> ThemeSettingsValidator.normalise("not json"))
                .isInstanceOf(BusinessRuleViolationException.class);
    }
}
