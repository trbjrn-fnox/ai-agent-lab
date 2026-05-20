# First Agent

A Spring Boot application that exposes a Spring AI agent-style chat endpoint as a REST API. The agent can answer
questions about weather and time in New York, London, and Tokyo using Gemini 2.0 Flash through Google AI Studio API
keys.

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
│   ├── WeatherTimeAgent.java         # Spring AI tool functions
│   └── AgentController.java          # REST controller exposing the chat client over HTTP
└── config/
    └── AgentConfig.java              # Spring bean configuration for chat client and memory
```

### Key Components

**`FirstAgentApplication.java`** -- Standard Spring Boot application entry point.

**`WeatherTimeAgent.java`** -- Defines Spring AI tools:

- `getCurrentTime(city)` -- Looks up the city's timezone from Java's `ZoneId` registry and returns the current time.
- `getWeather(city)` -- Returns mock weather data for New York, London, and Tokyo.

**`AgentController.java`** -- REST controller mounted at `/api/agent`:

- `POST /api/agent/chat` -- Send a message to the agent and receive a response. Accepts JSON with `message` and optional
  `conversationId` fields.
- `GET /api/agent/health` -- Health check endpoint.
- Uses Spring AI `ChatClient`, the Google GenAI model integration, Spring AI tool calling, and in-memory chat memory
  keyed by `conversationId`.

**`AgentConfig.java`** -- Registers the Spring-managed `ChatClient` and in-memory `ChatMemory` beans.

## Configuration

Create a `.env` file in the project root:

```bash
export GOOGLE_API_KEY=your-api-key-here
```

Application settings are in `src/main/resources/application.properties`:

| Property                                    | Default             | Description                                   |
|---------------------------------------------|---------------------|-----------------------------------------------|
| `server.port`                               | `8080`              | HTTP server port                              |
| `spring.ai.model.chat`                      | `google-genai`      | Enables the Spring AI Google GenAI chat model |
| `spring.ai.google.genai.api-key`            | `${GOOGLE_API_KEY}` | Google AI Studio API key                      |
| `spring.ai.google.genai.chat.options.model` | `gemini-2.5-flash`  | Gemini model used for chat                    |

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
  "content": "The weather in Tokyo is rainy with a temperature of 18°C (64°F).",
  "conversationId": "user1"
}
```
