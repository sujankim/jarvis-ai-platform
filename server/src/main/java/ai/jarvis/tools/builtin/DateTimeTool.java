package ai.jarvis.tools.builtin;

import ai.jarvis.tools.JarvisTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

/**
 * Tool for date and time queries.
 *
 * PROVIDES:
 * - Current date and time (local)
 * - Current time in any timezone
 * - Timezone offset information
 *
 * WHY NEEDED despite WorkingMemoryBuilder:
 * WorkingMemoryBuilder injects local time ONCE.
 * DateTimeTool lets AI query ANY timezone ON DEMAND.
 *
 * Example AI usage:
 * User: "What time is it in New York?"
 * AI calls: getCurrentTimeInZone("America/New_York")
 * Returns: "Monday, June 19 2026, 10:30:45 EDT (UTC-4)"
 */
@Slf4j
@Component
public class DateTimeTool implements JarvisTool {

    private static final DateTimeFormatter FULL_FORMATTER =
            DateTimeFormatter.ofPattern(
                    "EEEE, MMMM d yyyy, HH:mm:ss z");

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern(
                    "EEEE, MMMM d yyyy");

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern(
                    "HH:mm:ss z");

    /**
     * Get current date and time in local system timezone.
     * AI calls this for "what time is it" questions.
     *
     * @return formatted current date and time string
     */
    @Tool(description =
            "Get the current date and time in the "
                    + "local system timezone. "
                    + "Use when user asks what time or "
                    + "date it is without specifying "
                    + "a timezone.")
    public String getCurrentDateTime() {
        ZonedDateTime now = ZonedDateTime.now();
        String result = now.format(FULL_FORMATTER);

        log.debug("DateTimeTool.getCurrentDateTime: {}",
                result);
        return result;
    }

    /**
     * Get current time in a specific timezone.
     * AI calls this for timezone conversion questions.
     *
     * @param timezone IANA timezone ID
     *                 Examples: "America/New_York",
     *                 "Asia/Tokyo", "Europe/London",
     *                 "Asia/Kathmandu"
     * @return formatted time in requested timezone
     */
    @Tool(description =
            "Get the current date and time in a "
                    + "specific timezone. "
                    + "Use when user asks about time "
                    + "in a specific city or country. "
                    + "Parameter must be a valid IANA "
                    + "timezone ID like "
                    + "'America/New_York' or "
                    + "'Asia/Tokyo' or "
                    + "'Europe/London'.")
    public String getCurrentTimeInZone(
            @ToolParam(
                    description =
                            "IANA timezone ID. "
                                    + "Examples: America/New_York, "
                                    + "Asia/Tokyo, Europe/London, "
                                    + "Asia/Kathmandu, "
                                    + "Australia/Sydney")
            String timezone) {

        try {
            ZoneId zoneId = ZoneId.of(timezone);
            ZonedDateTime now =
                    ZonedDateTime.now(zoneId);

            boolean isDst = TimeZone.getTimeZone(zoneId).inDaylightTime(java.util.Date.from(now.toInstant()));
            String zoneName = TimeZone.getTimeZone(zoneId).getDisplayName(isDst,TimeZone.LONG, Locale.ENGLISH);
            String result = now.format(FULL_FORMATTER)
                    + " (" + zoneName + ")";

            log.debug(
                    "DateTimeTool.getCurrentTimeInZone"
                            + "({}): {}",
                    timezone, result);

            return result;

        } catch (Exception e) {
            log.warn(
                    "Invalid timezone: {} — {}",
                    timezone, e.getMessage());

            return "Invalid timezone: '"
                    + timezone + "'. "
                    + "Please use a valid IANA timezone "
                    + "like 'America/New_York' or "
                    + "'Asia/Kathmandu'.";
        }
    }

    /**
     * Get current date only (no time component).
     * AI calls this for "what day is today" questions.
     *
     * @return formatted current date string
     */
    @Tool(description =
            "Get only the current date (no time). "
                    + "Use when user asks what day or "
                    + "date it is today, or questions "
                    + "about the day of the week.")
    public String getCurrentDate() {
        String result = ZonedDateTime.now()
                .format(DATE_FORMATTER);

        log.debug("DateTimeTool.getCurrentDate: {}",
                result);
        return result;
    }

    /**
     * List all available timezone IDs for a region.
     * AI calls this when user asks about timezones
     * for a country or region.
     *
     * @param region region prefix like "America",
     *               "Asia", "Europe", "Australia"
     * @return comma-separated list of matching zones
     */
    @Tool(description =
            "List available timezone IDs for a "
                    + "geographic region. "
                    + "Use when you need to find the "
                    + "correct timezone ID for a city. "
                    + "Parameter is a region prefix: "
                    + "America, Asia, Europe, "
                    + "Australia, Africa, Pacific.")
    public String listTimezonesForRegion(
            @ToolParam(
                    description =
                            "Region prefix: America, "
                                    + "Asia, Europe, Australia, "
                                    + "Africa, Pacific")
            String region) {

        Set<String> allZones = ZoneId.getAvailableZoneIds();

        String matching = allZones.stream()
                .filter(z -> z.startsWith(region + "/"))
                .sorted()
                .reduce((a, b) -> a + ", " + b)
                .orElse("No timezones found for: "
                        + region);

        log.debug(
                "DateTimeTool.listTimezonesForRegion"
                        + "({}): found zones",
                region);

        return matching;
    }
}