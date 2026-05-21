package com.firstagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "case-agent")
public record CaseApiProperties(
        String baseUrl,
        String openapiUrl,
        String cookie,
        List<String> allowedMethods,
        List<String> allowedOperations) {

    private static final String DEFAULT_BASE_URL = "https://default-8080-aptic-integration.dev-pod-status.fnox.se";
    private static final String DEFAULT_OPENAPI_PATH = "/internalapi/aptic-integration/v3/api-docs";

    public CaseApiProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = DEFAULT_BASE_URL;
        }
        if (openapiUrl == null || openapiUrl.isBlank()) {
            openapiUrl = baseUrl + DEFAULT_OPENAPI_PATH;
        }
        if (cookie == null) {
            cookie = "";
        }
        if (allowedMethods == null || allowedMethods.isEmpty()) {
            allowedMethods = List.of("GET");
        }
        if (allowedOperations == null) {
            allowedOperations = List.of();
        }
    }

    public boolean hasCookie() {
        return !cookie.isBlank();
    }
}
