package com.firstagent.agent;

import com.google.adk.agents.BaseAgent;
import com.google.adk.events.Event;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Flowable;
import jakarta.annotation.PostConstruct;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * REST controller that exposes the Google ADK agent over HTTP.
 * Provides endpoints for chatting with the agent and managing sessions.
 */
@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final BaseAgent agent;
    private InMemoryRunner runner;

    /**
     * Map of userId -> Session for session management.
     */
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    public AgentController(BaseAgent agent) {
        this.agent = agent;
    }

    @PostConstruct
    public void init() {
        this.runner = new InMemoryRunner(agent);
    }

    /**
     * Send a chat message to the agent and receive a response.
     *
     * @param request contains the user message and optional userId
     * @return the agent's response
     */
    @PostMapping("/chat")
    public Map<String, String> chat(@RequestBody ChatRequest request) {
        String userId = request.userId() != null ? request.userId() : "default-user";

        Session session = sessions.computeIfAbsent(userId, uid ->
                runner.sessionService()
                        .createSession(runner.appName(), uid)
                        .blockingGet());

        Content userMsg = Content.fromParts(Part.fromText(request.message()));
        Flowable<Event> events = runner.runAsync(userId, session.id(), userMsg);

        StringBuilder response = new StringBuilder();
        events.blockingForEach(event -> {
            if (event.finalResponse()) {
                response.append(event.stringifyContent());
            }
        });

        return Map.of("response", response.toString());
    }

    /**
     * Health check endpoint.
     */
    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok", "agent", agent.name());
    }

    /**
     * Request body for the chat endpoint.
     */
    public record ChatRequest(String message, String userId) {
    }
}
