package com.firstagent.agent;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class WeatherTimeAgentTest {

    @Test
    void getsCurrentWeatherFromOpenMeteo() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        WeatherTimeAgent agent = new WeatherTimeAgent(restClientBuilder);

        server.expect(once(), requestTo("https://geocoding-api.open-meteo.com/v1/search?name=Tokyo&count=1&language=en&format=json"))
                .andRespond(withSuccess("""
                        {"results":[{"name":"Tokyo","country":"Japan","latitude":35.6895,"longitude":139.6917}]}
                        """, MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("https://api.open-meteo.com/v1/forecast?latitude=35.6895&longitude=139.6917&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m&temperature_unit=celsius&wind_speed_unit=kmh"))
                .andRespond(withSuccess("""
                        {"current":{"temperature_2m":18.2,"relative_humidity_2m":82,"weather_code":61,"wind_speed_10m":11.7}}
                        """, MediaType.APPLICATION_JSON));

        Map<String, String> response = agent.getWeather("Tokyo");

        assertThat(response).containsEntry("status", "success");
        assertThat(response.get("report"))
                .isEqualTo("The weather in Tokyo, Japan is rainy with a temperature of 18.2°C, 82% humidity, and 11.7 km/h wind.");
        server.verify();
    }

    @Test
    void reportsWhenCityCannotBeGeocoded() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        WeatherTimeAgent agent = new WeatherTimeAgent(restClientBuilder);

        server.expect(once(), requestTo("https://geocoding-api.open-meteo.com/v1/search?name=Unknownville&count=1&language=en&format=json"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        Map<String, String> response = agent.getWeather("Unknownville");

        assertThat(response).containsEntry("status", "error");
        assertThat(response.get("report")).isEqualTo("Weather information for 'Unknownville' is not available.");
        server.verify();
    }
}
