package com.firstagent.agent;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Tool provider that can answer questions about weather and time.
 */
@Service
public class WeatherTimeAgent {

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
     * Returns a mock weather report for a given city.
     */
    @Tool(description = "Get the current weather for a given city")
    public Map<String, String> getWeather(
            @ToolParam(description = "The name of the city to get the weather for")
            String city) {

        if ("new york".equalsIgnoreCase(city)) {
            return Map.of(
                    "status", "success",
                    "report", "The weather in New York is sunny with a temperature of 25°C (77°F).");
        } else if ("london".equalsIgnoreCase(city)) {
            return Map.of(
                    "status", "success",
                    "report", "The weather in London is cloudy with a temperature of 15°C (59°F).");
        } else if ("tokyo".equalsIgnoreCase(city)) {
            return Map.of(
                    "status", "success",
                    "report", "The weather in Tokyo is rainy with a temperature of 18°C (64°F).");
        } else {
            return Map.of(
                    "status", "error",
                    "report", "Weather information for '" + city + "' is not available.");
        }
    }
}
