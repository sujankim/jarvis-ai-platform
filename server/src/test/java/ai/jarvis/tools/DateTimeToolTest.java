package ai.jarvis.tools;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DateTimeTool Tests")
class DateTimeToolTest {

    private DateTimeTool tool;

    @BeforeEach
    void setUp() {
        tool = new DateTimeTool();
    }

    @Test
    @DisplayName("getCurrentDateTime returns non-empty string")
    void shouldReturnCurrentDateTime() {
        String result = tool.getCurrentDateTime();

        assertThat(result).isNotBlank();
        // Should contain year 2026
        assertThat(result).containsAnyOf(
                "2026", "2027");
    }

    @Test
    @DisplayName("getCurrentDate returns date without time")
    void shouldReturnCurrentDate() {
        String result = tool.getCurrentDate();

        assertThat(result).isNotBlank();
        // Should NOT contain colon (time component)
        assertThat(result).doesNotContain(":");
    }

    @Test
    @DisplayName("getCurrentTimeInZone works for valid timezone")
    void shouldReturnTimeForValidZone() {
        String result = tool
                .getCurrentTimeInZone("Asia/Tokyo");

        assertThat(result)
                .isNotBlank()
                .doesNotContain("Invalid");
    }

    @Test
    @DisplayName("getCurrentTimeInZone handles Kathmandu")
    void shouldHandleKathmandu() {
        String result = tool
                .getCurrentTimeInZone(
                        "Asia/Kathmandu");

        assertThat(result)
                .isNotBlank()
                .doesNotContain("Invalid");
    }

    @Test
    @DisplayName("getCurrentTimeInZone returns error for invalid zone")
    void shouldReturnErrorForInvalidZone() {
        String result = tool
                .getCurrentTimeInZone(
                        "Invalid/Timezone");

        assertThat(result)
                .contains("Invalid timezone");
    }

    @Test
    @DisplayName("listTimezonesForRegion returns Asia zones")
    void shouldListAsiaTimezones() {
        String result = tool
                .listTimezonesForRegion("Asia");

        assertThat(result)
                .contains("Asia/Tokyo")
                .contains("Asia/Kathmandu")
                .contains("Asia/Kolkata");
    }

    @Test
    @DisplayName("listTimezonesForRegion handles unknown region")
    void shouldHandleUnknownRegion() {
        String result = tool
                .listTimezonesForRegion("Unknown");

        assertThat(result)
                .contains("No timezones found");
    }
}