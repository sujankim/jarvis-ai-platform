package ai.jarvis.tools.builtin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Function;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

@DisplayName("WeatherTool Tests")
class WeatherToolTest {

    private WeatherTool toolWithKey;
    private WeatherTool toolWithoutKey;
    private WebClient webClient;
    private WebClient.Builder builder;
    private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;
    private WebClient.RequestHeadersSpec requestHeadersSpec;
    private WebClient.ResponseSpec responseSpec;

    @BeforeEach
    void setUp() {
        builder = mock(WebClient.Builder.class);
        webClient = mock(WebClient.class);
        requestHeadersUriSpec = mock(WebClient.RequestHeadersUriSpec.class);
        requestHeadersSpec = mock(WebClient.RequestHeadersSpec.class);
        responseSpec = mock(WebClient.ResponseSpec.class);

        when(builder.baseUrl(any(String.class))).thenReturn(builder);
        when(builder.build()).thenReturn(webClient);

        toolWithoutKey = new WeatherTool(builder, "");
        toolWithKey = new WeatherTool(builder, "fake-test-key-12345");
    }

    @Test
    @DisplayName("returns setup message when no API key")
    void shouldReturnSetupMessageWithNoKey() {
        String result = toolWithoutKey.getWeather("London");
        assertThat(result)
                .contains("not configured")
                .contains("OPENWEATHER_API_KEY")
                .contains("openweathermap.org");
    }

    @Test
    @DisplayName("returns setup message for country method too")
    void shouldReturnSetupMessageForCountryMethod() {
        String result = toolWithoutKey.getWeatherByCityAndCountry("London", "GB");
        assertThat(result).contains("not configured");
    }

    @Test
    @DisplayName("handles empty city name gracefully")
    void shouldHandleEmptyCity() {
        String result = toolWithKey.getWeather("");
        assertThat(result).contains("Please specify a city name.");
    }

    @Test
    @DisplayName("handles null city name gracefully")
    void shouldHandleNullCity() {
        String result = toolWithKey.getWeather(null);
        assertThat(result).contains("Please specify a city name.");
    }

    @Test
    @DisplayName("returns invalid key message on 401")
    void shouldHandleInvalidApiKey() {
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(any(Class.class)))
                .thenReturn(Mono.error(WebClientResponseException.create(401, "Unauthorized", null, null, null)));

        String result = toolWithKey.getWeather("London");
        assertThat(result).contains("Invalid weather API key");
    }

    @Test
    @DisplayName("returns city not found message on 404")
    void shouldHandleCityNotFound() {
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(any(Class.class)))
                .thenReturn(Mono.error(WebClientResponseException.create(404, "Not Found", null, null, null)));

        String result = toolWithKey.getWeather("UnknownCity");
        assertThat(result).contains("City not found");
    }

    @Test
    @DisplayName("returns formatted weather data with feels like difference")
    void shouldReturnFormattedWeatherWithFeelsLike() {
        WeatherTool.Main main = new WeatherTool.Main(25.0, 29.0, 80);
        WeatherTool.Weather weather = new WeatherTool.Weather("Clear", "clear sky");
        WeatherTool.Wind wind = new WeatherTool.Wind(5.0);
        WeatherTool.WeatherResponse response = new WeatherTool.WeatherResponse(main, List.of(weather), wind, 10000);

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(WeatherTool.WeatherResponse.class)).thenReturn(Mono.just(response));

        String result = toolWithKey.getWeather("Kolkata");

        assertThat(result)
                .contains("25.0°C")
                .contains("feels like 29.0°C")
                .contains("Clear sky")
                .contains("Humidity: 80%")
                .contains("Wind: 18.0 km/h")
                .contains("Visibility: 10km");
    }
}
