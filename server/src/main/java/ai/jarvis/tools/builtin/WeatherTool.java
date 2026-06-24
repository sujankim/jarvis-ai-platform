package ai.jarvis.tools.builtin;

import ai.jarvis.tools.JarvisTool;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

@Slf4j
@Component
public class WeatherTool implements JarvisTool {

    private static final String BASE_URL =
            "https://api.openweathermap.org/data/2.5";

    private static final Duration TIMEOUT =
            Duration.ofSeconds(5);

    private final WebClient webClient;
    private final String apiKey;

    public WeatherTool(
            WebClient.Builder webClientBuilder,
            @Value("${jarvis.tools.weather.api-key:}")
            String apiKey) {

        this.apiKey = apiKey;
        this.webClient = webClientBuilder
                .baseUrl(BASE_URL)
                .build();

        if (apiKey != null && !apiKey.isBlank()) {
            log.info(
                    "WeatherTool initialized "
                            + "with API key");
        } else {
            log.info(
                    "WeatherTool initialized "
                            + "without API key — "
                            + "set OPENWEATHER_API_KEY "
                            + "in .env to enable");
        }
    }

    @Tool(description =
            "Get current weather conditions for "
                    + "any city in the world. "
                    + "Returns temperature, weather "
                    + "description, humidity, and "
                    + "wind speed. "
                    + "Use when user asks about weather, "
                    + "temperature, or climate in "
                    + "a specific location.")
    public String getWeather(
            @ToolParam(
                    description =
                            "City name in English. "
                                    + "Examples: London, "
                                    + "Kathmandu, New York, "
                                    + "Tokyo, Sydney")
            String city) {

        if (!isConfigured()) {
            return "Weather tool is not configured. "
                    + "Add OPENWEATHER_API_KEY to "
                    + ".env file to enable weather. "
                    + "Get free key at: "
                    + "openweathermap.org/api";
        }

        if (city == null || city.isBlank()) {
            return "Please specify a city name.";
        }

        log.debug("WeatherTool.getWeather: {}", city);

        try {
            WeatherResponse response = webClient
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/weather")
                            .queryParam("q", city.trim())
                            .queryParam("appid", apiKey)
                            .queryParam("units", "metric")
                            .build())
                    .retrieve()
                    .bodyToMono(WeatherResponse.class)
                    .timeout(TIMEOUT)
                    .block();

            if (response == null) {
                return "Could not retrieve weather "
                        + "for: " + city;
            }

            return formatWeatherResponse(
                    city, response);

        } catch (Exception e) {
            log.warn(
                    "WeatherTool failed for {}: {}",
                    city, e.getMessage());

            if (e.getMessage() != null
                    && e.getMessage().contains("404")) {
                return "City not found: '" + city
                        + "'. Please check the city "
                        + "name and try again.";
            }

            if (e.getMessage() != null
                    && e.getMessage().contains("401")) {
                return "Invalid weather API key. "
                        + "Please check your "
                        + "OPENWEATHER_API_KEY in .env";
            }

            return "Weather service temporarily "
                    + "unavailable. Please try again.";
        }
    }

    @Tool(description =
            "Get current weather for a city with "
                    + "country code for precision. "
                    + "Use when city name is ambiguous "
                    + "(e.g. 'Springfield' exists in "
                    + "many countries). "
                    + "Country code is ISO format: "
                    + "US, GB, NP, JP, AU etc.")
    public String getWeatherByCityAndCountry(
            @ToolParam(
                    description = "City name in English")
            String city,
            @ToolParam(
                    description =
                            "ISO country code: "
                                    + "US, GB, NP, JP, AU, IN etc.")
            String countryCode) {

        if (!isConfigured()) {
            return "Weather tool is not configured. "
                    + "Add OPENWEATHER_API_KEY to .env";
        }

        if (city == null || city.isBlank()
                || countryCode == null
                || countryCode.isBlank()) {
            return "Please specify both city "
                    + "and country code.";
        }

        String query = city.trim() + ","
                + countryCode.trim().toUpperCase();

        log.debug(
                "WeatherTool.getWeatherByCity: {}",
                query);

        try {
            WeatherResponse response = webClient
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/weather")
                            .queryParam("q", query)
                            .queryParam("appid", apiKey)
                            .queryParam("units", "metric")
                            .build())
                    .retrieve()
                    .bodyToMono(WeatherResponse.class)
                    .timeout(TIMEOUT)
                    .block();

            if (response == null) {
                return "Could not retrieve weather "
                        + "for: " + query;
            }

            return formatWeatherResponse(
                    city + ", " + countryCode,
                    response);

        } catch (Exception e) {
            log.warn(
                    "WeatherTool failed for {}: {}",
                    query, e.getMessage());
            return "Could not get weather for "
                    + city + ", " + countryCode
                    + ". Please try again.";
        }
    }

    private boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    private String formatWeatherResponse(
            String city,
            WeatherResponse response) {

        StringBuilder sb = new StringBuilder();
        sb.append(city);

        if (response.main() != null) {
            sb.append(": ")
                    .append(String.format(
                            "%.1f°C",
                            response.main().temp()));

            double diff = Math.abs(
                    response.main().temp()
                            - response.main().feelsLike());
            if (diff > 3) {
                sb.append(String.format(
                        " (feels like %.1f°C)",
                        response.main().feelsLike()));
            }
        }

        if (response.weather() != null
                && !response.weather().isEmpty()) {
            String desc = response.weather()
                    .getFirst().description();
            if (desc != null && !desc.isEmpty()) {
                desc = Character.toUpperCase(
                        desc.charAt(0))
                        + desc.substring(1);
            }
            sb.append(", ").append(desc);
        }

        if (response.main() != null) {
            sb.append(", Humidity: ")
                    .append(response.main().humidity())
                    .append("%");
        }

        if (response.wind() != null) {
            sb.append(", Wind: ")
                    .append(String.format(
                            "%.1f km/h",
                            response.wind().speed()
                                    * 3.6));
        }

        if (response.visibility() != null
                && response.visibility() > 0) {
            sb.append(", Visibility: ")
                    .append(response.visibility() / 1000)
                    .append("km");
        }

        return sb.toString();
    }

    // ── Response Records ──────────────────────────

    record WeatherResponse(
            Main main,
            java.util.List<Weather> weather,
            Wind wind,
            Integer visibility) {}

    record Main(
            double temp,
            @JsonProperty("feels_like")
            double feelsLike,
            int humidity) {}

    record Weather(
            String main,
            String description) {}

     record Wind(
            double speed) {}
        }
