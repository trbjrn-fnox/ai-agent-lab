package com.firstagent.agent;

import com.firstagent.config.CaseApiProperties;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenApiToolProviderTest {

    @Test
    void buildsAllowedGetOperationFromOpenApiAndExecutesIt() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        CaseApiProperties properties = new CaseApiProperties(
                "https://case-api.example",
                "https://case-api.example/internalapi/aptic-integration/v3/api-docs",
                "kube-pod-status-session=test-cookie",
                List.of("GET"),
                List.of("getCase"));

        server.expect(once(), requestTo("https://case-api.example/internalapi/aptic-integration/v3/api-docs"))
                .andExpect(header(HttpHeaders.COOKIE, "kube-pod-status-session=test-cookie"))
                .andRespond(withSuccess(openApiDocument(), MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("https://case-api.example/internalapi/aptic-integration/cases/12345"))
                .andExpect(header(HttpHeaders.COOKIE, "kube-pod-status-session=test-cookie"))
                .andRespond(withSuccess("""
                        {"caseNumber":12345,"clientName":"Test Client","statusDescription":"Open"}
                        """, MediaType.APPLICATION_JSON));

        OpenApiToolProvider provider = new OpenApiToolProvider(properties, restClientBuilder);
        ToolCallback getCase = provider.toolCallbacks().getFirst();

        assertThat(getCase.getToolDefinition().name()).isEqualTo("getCase");
        assertThat(getCase.getToolDefinition().inputSchema()).contains("\"caseNumber\"");

        String response = getCase.call("""
                {"caseNumber":12345}
                """);

        assertThat(response)
                .contains("\"status\":\"success\"")
                .contains("\"operationId\":\"getCase\"")
                .contains("\"caseNumber\":12345");
        server.verify();
    }

    @Test
    void doesNotBuildDisallowedPostOperation() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        CaseApiProperties properties = new CaseApiProperties(
                "https://case-api.example",
                "https://case-api.example/internalapi/aptic-integration/v3/api-docs",
                "",
                List.of("GET"),
                List.of("createCase"));

        server.expect(once(), requestTo("https://case-api.example/internalapi/aptic-integration/v3/api-docs"))
                .andRespond(withSuccess(openApiDocument(), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> new OpenApiToolProvider(properties, restClientBuilder))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("createCase");
        server.verify();
    }

    @Test
    void reportsMissingRequiredParameter() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        CaseApiProperties properties = new CaseApiProperties(
                "https://case-api.example",
                "https://case-api.example/internalapi/aptic-integration/v3/api-docs",
                "",
                List.of("GET"),
                List.of("getCase"));

        server.expect(once(), requestTo("https://case-api.example/internalapi/aptic-integration/v3/api-docs"))
                .andRespond(withSuccess(openApiDocument(), MediaType.APPLICATION_JSON));

        OpenApiToolProvider provider = new OpenApiToolProvider(properties, restClientBuilder);

        String response = provider.toolCallbacks().getFirst().call("{}");

        assertThat(response)
                .contains("\"status\":\"error\"")
                .contains("Missing required parameter: caseNumber");
        server.verify();
    }

    private String openApiDocument() {
        return """
                {
                  "openapi": "3.0.1",
                  "paths": {
                    "/internalapi/aptic-integration/cases/{caseNumber}": {
                      "get": {
                        "operationId": "getCase",
                        "summary": "Get a case in Aptic",
                        "parameters": [
                          {
                            "name": "caseNumber",
                            "in": "path",
                            "required": true,
                            "description": "The numeric Aptic case number",
                            "schema": {"type": "integer", "format": "int64"}
                          }
                        ],
                        "responses": {"200": {"description": "OK"}}
                      }
                    },
                    "/internalapi/aptic-integration/cases": {
                      "post": {
                        "operationId": "createCase",
                        "summary": "Create a case in Aptic",
                        "responses": {"201": {"description": "Created"}}
                      }
                    }
                  }
                }
                """;
    }
}
