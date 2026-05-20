package com.firstagent.config;

import com.firstagent.agent.WeatherTimeAgent;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration for the agent chat client and conversation memory.
 */
@Configuration
public class AgentConfig {

    private static final String SYSTEM_PROMPT = """
            You can answer questions about the current weather and time in New York, London, and Tokyo.
            If the user asks about the weather or time in one of these cities, use the appropriate tool
            (getCurrentTime for time and getWeather for weather) to get the information and provide a clear
            and concise answer. If the user asks about a different city, politely inform them that you only
            have information for New York, London, and Tokyo.
            """;

    @Bean
    public ChatMemory chatMemory() {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                .maxMessages(20)
                .build();
    }

    @Bean
    public ChatClient weatherTimeChatClient(ChatClient.Builder chatClientBuilder,
                                            WeatherTimeAgent weatherTimeAgent,
                                            ChatMemory chatMemory) {
        return chatClientBuilder
                .defaultSystem(SYSTEM_PROMPT)
                .defaultTools(weatherTimeAgent)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }
}
