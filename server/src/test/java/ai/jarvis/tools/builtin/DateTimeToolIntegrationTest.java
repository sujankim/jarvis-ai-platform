package ai.jarvis.tools.builtin;

import ai.jarvis.config.TestContainerConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import java.time.ZoneId;
import java.time.ZonedDateTime;


import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link DateTimeTool}.
 *
 * <p>Verifies two things the unit test does not cover:
 * <ol>
 *   <li>The Spring-wired bean produces a fully structured,
 *       correctly formatted date string — not just a non-blank one.</li>
 *   <li>Timezone conversion returns zone-specific output for
 *       representative zones across all major world regions.</li>
 * </ol>
 */
@SpringBootTest(
        properties = {
                "spring.shell.interactive.enabled=false",
                "jarvis.security.jwt.secret="
                        + "integration-test-secret-key-min-32-chars-long",
                "spring.ai.google.genai.api-key="
        }
)
@ImportTestcontainers(TestContainerConfig.class)
@DisplayName("DateTimeTool Integration Tests")
class DateTimeToolIntegrationTest {

    private static final List<String> DAY_NAMES = List.of(
            "Monday", "Tuesday", "Wednesday",
            "Thursday", "Friday", "Saturday", "Sunday"
    );

    private static final List<String> MONTH_NAMES = List.of(
            "January", "February", "March", "April",
            "May", "June", "July", "August",
            "September", "October", "November", "December"
    );

    @Autowired
    private DateTimeTool dateTimeTool;

    // ── 1. Tool returns valid date string ─────────────────────────────────

    @Test
    @DisplayName("getCurrentDateTime returns a fully structured date-time string")
    void shouldReturnValidDateTimeString() {
        String result = dateTimeTool.getCurrentDateTime();

        // Format: "EEEE, MMMM d yyyy, HH:mm:ss z"
        assertThat(result)
                .as("Result must not be blank")
                .isNotBlank();

        assertThat(result)
                .as("Result must contain a day-of-week name")
                .containsAnyOf(DAY_NAMES.toArray(String[]::new));

        assertThat(result)
                .as("Result must contain a month name")
                .containsAnyOf(MONTH_NAMES.toArray(String[]::new));

        assertThat(result)
                .as("Result must contain the current year")
                .contains(String.valueOf(LocalDate.now().getYear()));

        assertThat(result)
                .as("Result must contain a time component (HH:mm:ss)")
                .matches(".*\\d{2}:\\d{2}:\\d{2}.*");

        assertThat(result)
                .as("Result must contain a timezone abbreviation after the time")
                .matches(".*\\d{2}:\\d{2}:\\d{2} [A-Z]+.*");
    }

    @Test
    @DisplayName("getCurrentDate returns date-only string with no time component")
    void shouldReturnDateOnlyString() {
        String result = dateTimeTool.getCurrentDate();

        assertThat(result)
                .as("Result must contain a day-of-week name")
                .containsAnyOf(DAY_NAMES.toArray(String[]::new));

        assertThat(result)
                .as("Result must contain a month name")
                .containsAnyOf(MONTH_NAMES.toArray(String[]::new));

        assertThat(result)
                .as("Result must contain the current year")
                .contains(String.valueOf(LocalDate.now().getYear()));

        assertThat(result)
                .as("Date-only result must not contain a colon (no time component)")
                .doesNotContain(":");
    }

    // ── 2. Timezone conversion works for major zones ──────────────────────

    /**
     * One representative zone per major world region.
     * Columns: IANA zone ID | keyword stable across DST changes.
     *
     * <p>The keyword is a substring of the full display name returned by
     * {@code ZoneId.getDisplayName(TextStyle.FULL, Locale.ENGLISH)}.
     * It is chosen to remain constant regardless of whether the zone
     * is currently observing daylight saving time.
     * <ul>
     *   <li>America/New_York  → "Eastern" (EST and EDT both contain it)</li>
     *   <li>America/Chicago   → "Central"</li>
     *   <li>America/Los_Angeles → "Pacific"</li>
     *   <li>Europe/London     → "Time" (GMT and BST do not share a root;
     *                           "Time" appears in both)</li>
     *   <li>Europe/Paris      → "Central European"</li>
     *   <li>Asia/Kolkata      → "India Standard Time"</li>
     *   <li>Asia/Kathmandu    → "Nepal Time"</li>
     *   <li>Asia/Tokyo        → "Japan Standard Time"</li>
     *   <li>Australia/Sydney  → "Australian Eastern"</li>
     *   <li>Pacific/Auckland  → "New Zealand"</li>
     *   <li>Africa/Cairo      → "Eastern European"</li>
     * </ul>
     */
    @ParameterizedTest(name = "{0} → display name contains \"{1}\"")
    @CsvSource({
            "America/New_York,    Eastern",
            "America/Chicago,     Central",
            "America/Los_Angeles, Pacific",
            "Europe/London,       Time",
            "Europe/Paris,        Central European",
            "Asia/Kolkata,        India Standard Time",
            "Asia/Kathmandu,      Nepal Time",
            "Asia/Tokyo,          Japan Standard Time",
            "Australia/Sydney,    Australian Eastern",
            "Pacific/Auckland,    New Zealand",
            "Africa/Cairo,        Eastern European"
    })
    @DisplayName("getCurrentTimeInZone returns zone-specific output for major zones")
    void shouldReturnZoneSpecificOutputForMajorZones(
            String zoneId, String expectedDisplayNameKeyword) {

        String result = dateTimeTool.getCurrentTimeInZone(zoneId.trim());

        // Result format: "EEEE, MMMM d yyyy, HH:mm:ss z (Zone Display Name)"
        assertThat(result)
                .as("[%s] Result must not be blank", zoneId)
                .isNotBlank();

        assertThat(result)
                .as("[%s] Result must not be an error message", zoneId)
                .doesNotContain("Invalid");

        assertThat(result)
                .as("[%s] Result must contain the current year", zoneId)
                .contains(String.valueOf(ZonedDateTime.now(ZoneId.of(zoneId.trim())).getYear()));

        assertThat(result)
                .as("[%s] Result must contain a time component", zoneId)
                .matches(".*\\d{2}:\\d{2}:\\d{2}.*");

        assertThat(result)
                .as("[%s] Result must contain the zone display name "
                        + "in the parenthetical suffix", zoneId)
                .contains(expectedDisplayNameKeyword.trim());

        assertThat(result)
                .as("[%s] Result must end with a closing parenthesis "
                        + "from the display name suffix", zoneId)
                .endsWith(")");
    }
}