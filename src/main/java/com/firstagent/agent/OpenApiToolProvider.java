package com.firstagent.agent;

import com.firstagent.config.CaseApiProperties;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriBuilder;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Builds Spring AI tools from an OpenAPI document and executes only explicitly allowed operations.
 */
@Service
public class OpenApiToolProvider {

    private static final Set<String> SUPPORTED_PARAMETER_LOCATIONS = Set.of("path", "query");

    private final ObjectMapper objectMapper;
    private final RestClient apiRestClient;
    private final RestClient openApiRestClient;
    private final List<ToolCallback> toolCallbacks;

    public OpenApiToolProvider(CaseApiProperties properties, RestClient.Builder restClientBuilder) {
        this.objectMapper = new ObjectMapper();
        this.apiRestClient = authenticatedBuilder(restClientBuilder.clone(), properties)
                .baseUrl(properties.baseUrl())
                .build();
        this.openApiRestClient = authenticatedBuilder(restClientBuilder.clone(), properties).build();
        this.toolCallbacks = loadToolCallbacks(properties);
    }

    public List<ToolCallback> toolCallbacks() {
        return toolCallbacks;
    }

    private RestClient.Builder authenticatedBuilder(RestClient.Builder builder, CaseApiProperties properties) {
        if (properties.hasCookie()) {
            builder.defaultHeader(HttpHeaders.COOKIE, properties.cookie());
        }
        return builder;
    }

    private List<ToolCallback> loadToolCallbacks(CaseApiProperties properties) {
        if (properties.allowedOperations().isEmpty()) {
            throw new IllegalStateException("case-agent.allowed-operations must contain at least one operationId");
        }

        JsonNode openApiDocument = fetchOpenApiDocument(properties.openapiUrl());
        Map<String, OpenApiOperation> operations = extractAllowedOperations(openApiDocument, properties);
        List<ToolCallback> callbacks = new ArrayList<>();

        for (String operationId : properties.allowedOperations()) {
            OpenApiOperation operation = operations.get(operationId);
            if (operation == null) {
                throw new IllegalStateException("Allowed OpenAPI operation was not found or is not allowed: " + operationId);
            }
            callbacks.add(new OpenApiOperationToolCallback(operation));
        }

        return List.copyOf(callbacks);
    }

    private JsonNode fetchOpenApiDocument(String openapiUrl) {
        try {
            String response = openApiRestClient.get()
                    .uri(openapiUrl)
                    .retrieve()
                    .body(String.class);

            if (response == null || response.isBlank()) {
                throw new IllegalStateException("OpenAPI document response was empty");
            }

            return objectMapper.readTree(response);
        } catch (RestClientException ex) {
            throw new IllegalStateException("Failed to fetch OpenAPI document", ex);
        } catch (JacksonException ex) {
            throw new IllegalStateException("Failed to parse OpenAPI document", ex);
        }
    }

    private Map<String, OpenApiOperation> extractAllowedOperations(JsonNode openApiDocument, CaseApiProperties properties) {
        Set<String> allowedOperationIds = new LinkedHashSet<>(properties.allowedOperations());
        Set<String> allowedMethods = new LinkedHashSet<>(properties.allowedMethods().stream()
                .map(method -> method.toUpperCase(Locale.ROOT))
                .toList());
        Map<String, OpenApiOperation> operations = new LinkedHashMap<>();

        for (Map.Entry<String, JsonNode> pathEntry : openApiDocument.path("paths").properties()) {
            String path = pathEntry.getKey();
            JsonNode pathDefinition = pathEntry.getValue();
            List<OpenApiParameter> pathParameters = extractParameters(pathDefinition.path("parameters"));

            for (Map.Entry<String, JsonNode> methodEntry : pathDefinition.properties()) {
                String method = methodEntry.getKey().toUpperCase(Locale.ROOT);
                JsonNode operationDefinition = methodEntry.getValue();
                String operationId = operationDefinition.path("operationId").asText("");

                if (!allowedOperationIds.contains(operationId) || !allowedMethods.contains(method)) {
                    continue;
                }

                List<OpenApiParameter> parameters = new ArrayList<>(pathParameters);
                parameters.addAll(extractParameters(operationDefinition.path("parameters")));
                operations.put(operationId, new OpenApiOperation(
                        operationId,
                        method,
                        path,
                        operationDescription(operationId, operationDefinition),
                        inputSchema(parameters),
                        List.copyOf(parameters)));
            }
        }

        return operations;
    }

    private List<OpenApiParameter> extractParameters(JsonNode parametersDefinition) {
        if (!parametersDefinition.isArray()) {
            return List.of();
        }

        List<OpenApiParameter> parameters = new ArrayList<>();
        for (JsonNode parameterDefinition : parametersDefinition) {
            String location = parameterDefinition.path("in").asText("");
            if (!SUPPORTED_PARAMETER_LOCATIONS.contains(location)) {
                continue;
            }

            String name = parameterDefinition.path("name").asText("");
            if (name.isBlank()) {
                continue;
            }

            JsonNode schema = parameterDefinition.path("schema");
            parameters.add(new OpenApiParameter(
                    name,
                    location,
                    parameterDefinition.path("required").asBoolean(false),
                    parameterDefinition.path("description").asText(""),
                    schema.isMissingNode() ? null : schema.deepCopy()));
        }

        return List.copyOf(parameters);
    }

    private String operationDescription(String operationId, JsonNode operationDefinition) {
        String summary = operationDefinition.path("summary").asText("");
        String description = operationDefinition.path("description").asText("");
        String combined = (summary + "\n" + description).trim();
        if (combined.isBlank()) {
            return "Call OpenAPI operation " + operationId;
        }
        return combined;
    }

    private String inputSchema(List<OpenApiParameter> parameters) {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        ArrayNode required = schema.putArray("required");

        for (OpenApiParameter parameter : parameters) {
            ObjectNode parameterSchema = parameterSchema(parameter);
            properties.set(parameter.name(), parameterSchema);
            if (parameter.required()) {
                required.add(parameter.name());
            }
        }

        return schema.toString();
    }

    private ObjectNode parameterSchema(OpenApiParameter parameter) {
        ObjectNode schema = objectMapper.createObjectNode();
        JsonNode openApiSchema = parameter.schema();
        if (openApiSchema != null && openApiSchema.isObject()) {
            schema.setAll((ObjectNode) openApiSchema);
        } else {
            schema.put("type", "string");
        }
        if (!parameter.description().isBlank()) {
            schema.put("description", parameter.description());
        }
        return schema;
    }

    private String execute(OpenApiOperation operation, String toolInput) {
        try {
            JsonNode input = objectMapper.readTree(toolInput);
            Map<String, String> pathVariables = new LinkedHashMap<>();
            Map<String, String> queryParameters = new LinkedHashMap<>();

            for (OpenApiParameter parameter : operation.parameters()) {
                String value = inputValue(input, parameter.name());
                if (parameter.required() && (value == null || value.isBlank())) {
                    return error("Missing required parameter: " + parameter.name());
                }
                if (value == null || value.isBlank()) {
                    continue;
                }
                if ("path".equals(parameter.location())) {
                    pathVariables.put(parameter.name(), value);
                } else if ("query".equals(parameter.location())) {
                    queryParameters.put(parameter.name(), value);
                }
            }

            String response = apiRestClient.method(HttpMethod.valueOf(operation.method()))
                    .uri(buildUri(operation.path(), pathVariables, queryParameters))
                    .retrieve()
                    .body(String.class);

            if (response == null || response.isBlank()) {
                return error("The API returned an empty response for operation " + operation.operationId());
            }

            return success(operation.operationId(), response);
        } catch (RestClientException ex) {
            return error("The API is currently unavailable: " + ex.getMessage());
        } catch (JacksonException ex) {
            return error("Invalid tool input JSON: " + ex.getMessage());
        } catch (IllegalArgumentException ex) {
            return error(ex.getMessage());
        }
    }

    private Function<UriBuilder, URI> buildUri(String path, Map<String, String> pathVariables, Map<String, String> queryParameters) {
        return uriBuilder -> {
            UriBuilder builder = uriBuilder.path(path);
            queryParameters.forEach(builder::queryParam);
            return builder.build(pathVariables);
        };
    }

    private String inputValue(JsonNode input, String name) {
        JsonNode value = input.path(name);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        return value.asText();
    }

    private String success(String operationId, String response) {
        return """
                {"status":"success","operationId":%s,"response":%s}
                """.formatted(jsonString(operationId), response);
    }

    private String error(String message) {
        return """
                {"status":"error","message":%s}
                """.formatted(jsonString(message));
    }

    private String jsonString(String value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException ex) {
            throw new IllegalStateException("Failed to render JSON string", ex);
        }
    }

    private record OpenApiOperation(
            String operationId,
            String method,
            String path,
            String description,
            String inputSchema,
            List<OpenApiParameter> parameters) {
    }

    private record OpenApiParameter(
            String name,
            String location,
            boolean required,
            String description,
            JsonNode schema) {
    }

    private class OpenApiOperationToolCallback implements ToolCallback {

        private final OpenApiOperation operation;
        private final ToolDefinition toolDefinition;

        private OpenApiOperationToolCallback(OpenApiOperation operation) {
            this.operation = operation;
            this.toolDefinition = DefaultToolDefinition.builder()
                    .name(operation.operationId())
                    .description(operation.description())
                    .inputSchema(operation.inputSchema())
                    .build();
        }

        @Override
        public ToolDefinition getToolDefinition() {
            return toolDefinition;
        }

        @Override
        public String call(String toolInput) {
            return execute(operation, toolInput);
        }

        @Override
        public String call(String toolInput, ToolContext toolContext) {
            return execute(operation, toolInput);
        }
    }
}
