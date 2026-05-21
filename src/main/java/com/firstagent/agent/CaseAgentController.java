package com.firstagent.agent;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller that exposes the case information agent over HTTP.
 */
@RestController
@RequestMapping("/api/case-agent")
public class CaseAgentController {

    private final ChatClient chatClient;

    public CaseAgentController(@Qualifier("caseChatClient") ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    /**
     * Send a chat message to the case agent and receive a response.
     *
     * @param request contains the user message and optional conversationId
     * @return the agent's response
     */
    @PostMapping("/chat")
    public ChatResponse chat(@RequestBody ChatRequest request) {
        String conversationId = request.conversationId() != null ? request.conversationId() : "default-conversation";

        String content = chatClient.prompt()
                .advisors(advisors -> advisors.param(ChatMemory.CONVERSATION_ID, conversationId))
                .user(request.message())
                .call()
                .content();

        return new ChatResponse(content, conversationId);
    }

    /**
     * Request body for the chat endpoint.
     */
    public record ChatRequest(String message, String conversationId) {
    }

    /**
     * Response body for the chat endpoint.
     */
    public record ChatResponse(String content, String conversationId) {
    }
}
