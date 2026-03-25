package com.firstagent.agent;

import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.LlmAgent;
import com.google.adk.tools.Annotations.Schema;
import com.google.adk.tools.FunctionTool;

import java.text.Normalizer;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * A sample multi-tool agent that can answer questions about weather and time.
 * Uses Google ADK's LlmAgent with FunctionTool bindings.
 */
public class WeatherTimeAgent {

    public static final BaseAgent ROOT_AGENT = initAgent();

    private static BaseAgent initAgent() {
        return LlmAgent.builder()
                .name("city_agent")
                .model("gemini-2.0-flash")
                .description("Helpful person on the street in New York, London or Tokyo.")
                .instruction("""
                        You can answer questions about the current weather and time in New York, London, and Tokyo.
                         If the user asks about the weather or time in one of these cities, use the appropriate tool (getCurrentTime for time and getWeather for weather) to get the information and provide a clear and concise answer.
                         If the user asks about a different city, politely inform them that you only have information for New York, London, and Tokyo.
                        """)
                .tools(
                        FunctionTool.create(WeatherTimeAgent.class, "getCurrentTime"),
                        FunctionTool.create(WeatherTimeAgent.class, "getWeather"))
                .build();
    }

    /**
     * Returns the current time for a given city by matching it to a known timezone.
     */
    @Schema(description = "Get the current time for a given city")
    public static Map<String, String> getCurrentTime(
            @Schema(name = "city", description = "The name of the city to get the time for")
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
    @Schema(description = "Get the current weather for a given city")
    public static Map<String, String> getWeather(
            @Schema(name = "city", description = "The name of the city to get the weather for")
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
