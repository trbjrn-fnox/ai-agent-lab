# First Agent

A Spring Boot application that exposes a Google ADK (Agent Development Kit) agent as a REST API. The agent can answer questions about weather and time in New York, London, and Tokyo using Gemini 2.0 Flash.

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
│   ├── WeatherTimeAgent.java         # Agent definition and tool functions
│   └── AgentController.java          # REST controller exposing the agent over HTTP
└── config/
    └── AgentConfig.java              # Spring bean configuration for the agent
```

### Key Components

**`FirstAgentApplication.java`** -- Standard Spring Boot application entry point.

**`WeatherTimeAgent.java`** -- Defines the agent and its tools:
- Builds an `LlmAgent` (backed by `gemini-2.0-flash`) with two function tools.
- `getCurrentTime(city)` -- Looks up the city's timezone from Java's `ZoneId` registry and returns the current time.
- `getWeather(city)` -- Returns mock weather data for New York, London, and Tokyo.

**`AgentController.java`** -- REST controller mounted at `/api/agent`:
- `POST /api/agent/chat` -- Send a message to the agent and receive a response. Accepts JSON with `message` and optional `userId` fields.
- `GET /api/agent/health` -- Health check endpoint.
- Manages per-user sessions in memory via `InMemoryRunner`.

**`AgentConfig.java`** -- Registers the `WeatherTimeAgent` as a Spring bean so it can be injected into the controller.

## Configuration

Create a `.env` file in the project root:

```bash
export GOOGLE_GENAI_USE_VERTEXAI=FALSE
export GOOGLE_API_KEY=your-api-key-here
```

Application settings are in `src/main/resources/application.properties`:

| Property | Default | Description |
|---|---|---|
| `server.port` | `8080` | HTTP server port |
| `google.genai.use-vertexai` | `false` | Set to `true` to use Vertex AI instead of Google AI Studio |

## Running Locally

1. Source your environment variables:

   ```bash
   source .env
   ```

2. Build and run with Maven:

   ```bash
   mvn spring-boot:run
   ```

   Alternatively, to launch the Google ADK Dev UI:

   ```bash
   mvn exec:java
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
     -e GOOGLE_GENAI_USE_VERTEXAI=FALSE \
     first-agent
   ```

## API Usage

### Chat

```bash
curl -X POST http://localhost:8080/api/agent/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "What is the weather in Tokyo?", "userId": "user1"}'
```

Response:

```json
{
  "response": "The weather in Tokyo is rainy with a temperature of 18°C (64°F)."
}
```

### Health Check

```bash
curl http://localhost:8080/api/agent/health
```

Response:

```json
{
  "status": "ok",
  "agent": "city_agent"
}
```
