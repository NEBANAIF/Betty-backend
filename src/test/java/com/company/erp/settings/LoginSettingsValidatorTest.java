package com.company.erp.settings;

import com.company.erp.common.exception.BusinessRuleViolationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginSettingsValidatorTest {

    @Test
    void blankOrEmptyMeansTheBuiltInPage() {
        assertThat(LoginSettingsValidator.normalise(null)).isNull();
        assertThat(LoginSettingsValidator.normalise("  ")).isNull();
        assertThat(LoginSettingsValidator.normalise("{}")).isNull();
        assertThat(LoginSettingsValidator.normalise("{\"title\":\"  \"}")).isNull();
    }

    @Test
    void validTextColoursAndChoicesAreKept() {
        String json = LoginSettingsValidator.normalise(
                "{\"title\":\" Welcome \",\"panelColor\":\"#1A2B3C\",\"layout\":\"right\",\"pattern\":\"dots\","
                        + "\"bullets\":[\"Fast\",\"  \",\"Simple\"]}");
        assertThat(json).contains("\"title\":\"Welcome\"").contains("\"panelColor\":\"#1a2b3c\"")
                .contains("\"layout\":\"right\"").contains("\"pattern\":\"dots\"")
                .contains("\"bullets\":[\"Fast\",\"Simple\"]");
    }

    @Test
    void anEmptyBulletListIsKeptSoTheListCanBeHidden() {
        assertThat(LoginSettingsValidator.normalise("{\"bullets\":[]}")).isEqualTo("{\"bullets\":[]}");
    }

    @Test
    void headlineKeepsLineBreaksButOtherTextDoesNot() {
        String json = LoginSettingsValidator.normalise("{\"headline\":\"One\\nTwo\",\"title\":\"A\\nB\"}");
        assertThat(json).contains("\"headline\":\"One\\nTwo\"").contains("\"title\":\"A B\"");
    }

    @Test
    void badInputIsRejected() {
        assertThatThrownBy(() -> LoginSettingsValidator.normalise("{\"panelColor\":\"red;}body{display:none\"}"))
                .isInstanceOf(BusinessRuleViolationException.class);
        assertThatThrownBy(() -> LoginSettingsValidator.normalise("{\"layout\":\"upside-down\"}"))
                .isInstanceOf(BusinessRuleViolationException.class);
        assertThatThrownBy(() -> LoginSettingsValidator.normalise("{\"title\":\"" + "x".repeat(61) + "\"}"))
                .isInstanceOf(BusinessRuleViolationException.class);
        assertThatThrownBy(() -> LoginSettingsValidator.normalise("{\"bullets\":[\"a\",\"b\",\"c\",\"d\",\"e\"]}"))
                .isInstanceOf(BusinessRuleViolationException.class);
        assertThatThrownBy(() -> LoginSettingsValidator.normalise("{\"surprise\":\"x\"}"))
                .isInstanceOf(BusinessRuleViolationException.class);
        assertThatThrownBy(() -> LoginSettingsValidator.normalise("not json"))
                .isInstanceOf(BusinessRuleViolationException.class);
    }
}
