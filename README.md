# First Agent

A Spring Boot application that exposes a Spring AI agent-style chat endpoint as a REST API. The agent can answer
questions about current weather for cities worldwide using Open-Meteo, and current time for cities that match Java's
timezone registry, using Gemini through Google AI Studio API keys.

## Prerequisites

- Java 25
- Maven 3.8+
- A Google AI Studio API key ([get one here](https://aistudio.google.com/apikey))
- Docker (optional, for containerized deployment)

## Project Structure

```
src/main/java/com/firstagent/
├── FirstAgentApplication.java        # Spring Boot entry point
├── agent/
│   ├── CaseAgentController.java      # REST controller exposing the case information chat client
│   ├── OpenApiToolProvider.java      # Builds allowed Spring AI tools from OpenAPI operations
│   ├── WeatherTimeAgent.java         # Spring AI tool functions
│   └── AgentController.java          # REST controller exposing the chat client over HTTP
└── config/
    ├── AgentConfig.java              # Spring bean configuration for chat clients and memory
    └── CaseApiProperties.java        # Case API settings
```

### Key Components

**`FirstAgentApplication.java`** -- Standard Spring Boot application entry point.

**`WeatherTimeAgent.java`** -- Defines Spring AI tools:

- `getCurrentTime(city)` -- Looks up the city's timezone from Java's `ZoneId` registry and returns the current time.
- `getWeather(city)` -- Uses Open-Meteo's public geocoding and forecast APIs to return current weather.

**`AgentController.java`** -- REST controller mounted at `/api/agent`:

- `POST /api/agent/chat` -- Send a message to the agent and receive a response. Accepts JSON with `message` and optional
  `conversationId` fields.
- `GET /api/agent/health` -- Health check endpoint.
- Uses Spring AI `ChatClient`, the Google GenAI model integration, Spring AI tool calling, and in-memory chat memory
  keyed by `conversationId`.

**`CaseAgentController.java`** -- REST controller mounted at `/api/case-agent`:

- `POST /api/case-agent/chat` -- Send a message to the case information agent and receive a response.
- Uses OpenAPI-generated tools restricted by `case-agent.allowed-operations` and `case-agent.allowed-methods`.

**`AgentConfig.java`** -- Registers the Spring-managed `ChatClient` and in-memory `ChatMemory` beans.

## Configuration

Create a `.env` file in the project root:

```bash
export GOOGLE_API_KEY=your-api-key-here
export CASE_API_COOKIE='kube-pod-status-session=your-cookie-here'
```

Application settings are in `src/main/resources/application.properties`:

| Property                                    | Default             | Description                                   |
|---------------------------------------------|---------------------|-----------------------------------------------|
| `server.port`                               | `8080`              | HTTP server port                              |
| `spring.ai.model.chat`                      | `google-genai`      | Enables the Spring AI Google GenAI chat model |
| `spring.ai.google.genai.api-key`            | `${GOOGLE_API_KEY}` | Google AI Studio API key                      |
| `spring.ai.google.genai.chat.options.model` | `gemini-2.5-flash`  | Gemini model used for chat                    |
| `case-agent.base-url`                       | dev pod URL         | Base URL for the Aptic integration case API   |
| `case-agent.openapi-url`                    | `${CASE_API_OPENAPI_URL}` | OpenAPI document URL for generating tools |
| `case-agent.cookie`                         | `${CASE_API_COOKIE}`| Cookie header value for the protected API     |
| `case-agent.allowed-methods`                | `GET`               | HTTP methods that can become tools            |
| `case-agent.allowed-operations`             | selected GET operations | OpenAPI operation IDs exposed as tools    |

## Running Locally

1. Source your environment variables:

   ```bash
   source .env
   ```

2. Build and run with Maven:

   ```bash
   mvn spring-boot:run
   ```

3. The application starts on `http://localhost:8080`.

## Running with Docker

1. Build the image:

   ```bash
   docker build -t first-agent .
   ```

2. Run the container, passing your API key via the `.env` file:

   ```bash
   docker run -p 8080:8080 --env-file .env first-agent
   ```

   Or pass environment variables directly:

   ```bash
   docker run -p 8080:8080 \
     -e GOOGLE_API_KEY=your-api-key \
     first-agent
   ```

## API Usage

### Chat

```bash
curl -X POST http://localhost:8080/api/agent/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "What is the weather in Tokyo?", "conversationId": "user1"}'
```

Response:

```json
{
  "content": "The weather in Tokyo, Japan is rainy with a temperature of 18.0°C, 82% humidity, and 12.0 km/h wind.",
  "conversationId": "user1"
}
```

### Case Agent Chat

```bash
curl -X POST http://localhost:8080/api/case-agent/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "Get case 12345", "conversationId": "case-user1"}'
```
