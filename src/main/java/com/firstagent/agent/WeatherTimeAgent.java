package com.firstagent.agent;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Tool provider that can answer questions about weather and time.
 */
@Service
public class WeatherTimeAgent {

    private final RestClient restClient;

    public WeatherTimeAgent(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }

    /**
     * Returns the current time for a given city by matching it to a known timezone.
     */
    @Tool(description = "Get the current time for a given city")
    public Map<String, String> getCurrentTime(
            @ToolParam(description = "The name of the city to get the time for")
            String city) {

        String normalizedCity = Normalizer.normalize(city, Normalizer.Form.NFD)
                .trim()
                .toLowerCase()
                .replaceAll("(\\p{IsM}+|\\p{IsP}+)", "")
                .replaceAll("\\s+", "_");

        return ZoneId.getAvailableZoneIds().stream()
                .filter(zid -> zid.toLowerCase().endsWith("/" + normalizedCity))
                .findFirst()
                .map(zid -> Map.of(
                        "status", "success",
                        "report", "The current time in " + city + " is "
                                + ZonedDateTime.now(ZoneId.of(zid))
                                .format(DateTimeFormatter.ofPattern("HH:mm"))
                                + "."))
                .orElse(Map.of(
                        "status", "error",
                        "report", "Sorry, I don't have timezone information for " + city + "."));
    }

    /**
     * Returns a current weather report for a given city.
     */
    @Tool(description = "Get the current weather for a given city")
    public Map<String, String> getWeather(
            @ToolParam(description = "The name of the city to get the weather for")
            String city) {

        if (city == null) {
            return Map.of(
                    "status", "error",
                    "report", "Please provide a city name.");
        }

        String searchName = city.trim();
        if (searchName.isEmpty()) {
            return Map.of(
                    "status", "error",
                    "report", "Please provide a city name.");
        }

        try {
            GeocodingResult location = findLocation(searchName);
            if (location == null) {
                return Map.of(
                        "status", "error",
                        "report", "Weather information for '" + city + "' is not available.");
            }

            ForecastResponse forecast = restClient.get()
                    .uri("https://api.open-meteo.com/v1/forecast"
                                    + "?latitude={latitude}&longitude={longitude}"
                                    + "&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m"
                                    + "&temperature_unit=celsius&wind_speed_unit=kmh",
                            location.latitude(), location.longitude())
                    .retrieve()
                    .body(ForecastResponse.class);

            if (forecast == null || forecast.current() == null || !forecast.current().isComplete()) {
                return Map.of(
                        "status", "error",
                        "report", "Weather information for '" + city + "' is not available.");
            }

            CurrentWeather current = forecast.current();
            return Map.of(
                    "status", "success",
                    "report", "The weather in " + location.displayName()
                            + " is " + describeWeatherCode(current.weatherCode())
                            + " with a temperature of " + formatDecimal(current.temperatureCelsius()) + "°C, "
                            + current.relativeHumidity() + "% humidity, and "
                            + formatDecimal(current.windSpeedKmh()) + " km/h wind.");
        } catch (RestClientException ex) {
            return Map.of(
                    "status", "error",
                    "report", "The weather service is currently unavailable: " + ex.getMessage());
        }
    }

    private GeocodingResult findLocation(String city) {
        GeocodingResponse response = restClient.get()
                .uri("https://geocoding-api.open-meteo.com/v1/search"
                                + "?name={city}&count=1&language=en&format=json",
                        city)
                .retrieve()
                .body(GeocodingResponse.class);

        if (response == null || response.results() == null || response.results().isEmpty()) {
            return null;
        }

        return response.results().getFirst();
    }

    private String describeWeatherCode(int weatherCode) {
        return switch (weatherCode) {
            case 0 -> "clear";
            case 1, 2 -> "partly cloudy";
            case 3 -> "overcast";
            case 45, 48 -> "foggy";
            case 51, 53, 55 -> "drizzly";
            case 56, 57 -> "freezing drizzle";
            case 61, 63, 65 -> "rainy";
            case 66, 67 -> "freezing rain";
            case 71, 73, 75 -> "snowy";
            case 77 -> "snowy with snow grains";
            case 80, 81, 82 -> "showery";
            case 85, 86 -> "snowy with snow showers";
            case 95 -> "stormy";
            case 96, 99 -> "stormy with hail";
            default -> "experiencing weather code " + weatherCode;
        };
    }

    private String formatDecimal(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private record GeocodingResponse(List<GeocodingResult> results) {
    }

    private record GeocodingResult(String name, String country, double latitude, double longitude) {

        private String displayName() {
            if (country == null || country.isBlank()) {
                return name;
            }
            return name + ", " + country;
        }
    }

    private record ForecastResponse(CurrentWeather current) {
    }

    private record CurrentWeather(
            @JsonProperty("temperature_2m")
            Double temperatureCelsius,
            @JsonProperty("relative_humidity_2m")
            Integer relativeHumidity,
            @JsonProperty("weather_code")
            Integer weatherCode,
            @JsonProperty("wind_speed_10m")
            Double windSpeedKmh) {

        private boolean isComplete() {
            return temperatureCelsius != null
                    && relativeHumidity != null
                    && weatherCode != null
                    && windSpeedKmh != null;
        }
    }
}
