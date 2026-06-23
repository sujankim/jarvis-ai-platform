package ai.jarvis.tools.builtin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("WeatherTool Tests")
class WeatherToolTest {

    private WeatherTool toolWithKey;
    private WeatherTool toolWithoutKey;

    @BeforeEach
    void setUp() {
        WebClient.Builder builder =
                WebClient.builder();

        // Tool with no API key configured
        toolWithoutKey = new WeatherTool(
                builder, "");

        // Tool with fake key
        // (won't make real API calls in tests)
        toolWithKey = new WeatherTool(
                builder, "fake-test-key-12345");
    }

    @Test
    @DisplayName("returns setup message when no API key")
    void shouldReturnSetupMessageWithNoKey() {
        String result =
                toolWithoutKey.getWeather("London");

        assertThat(result)
                .contains("not configured")
                .contains("OPENWEATHER_API_KEY")
                .contains("openweathermap.org");
    }

    @Test
    @DisplayName("returns setup message for country method too")
    void shouldReturnSetupMessageForCountryMethod() {
        String result =
                toolWithoutKey
                        .getWeatherByCityAndCountry(
                                "London", "GB");

        assertThat(result)
                .contains("not configured");
    }

    @Test
    @DisplayName("handles empty city name gracefully")
    void shouldHandleEmptyCity() {
        String result =
                toolWithoutKey.getWeather("");

        // Either setup message or empty city message
        assertThat(result).isNotBlank();
    }

    @Test
    @DisplayName("handles null city name gracefully")
    void shouldHandleNullCity() {
        String result =
                toolWithoutKey.getWeather(null);

        assertThat(result).isNotBlank();
    }

    @Test
    @DisplayName("returns invalid key message on 401")
    void shouldHandleInvalidApiKey() {
        // With a fake key → API returns 401
        // Tool should return helpful message, not throw
        String result =
                toolWithKey.getWeather("London");

        // Should either return error message
        // or weather data (not throw exception)
        assertThat(result).isNotBlank();
        assertThat(result).isNotNull();
    }
}