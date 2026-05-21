package com.firstagent.config;

import com.firstagent.agent.OpenApiToolProvider;
import com.firstagent.agent.WeatherTimeAgent;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.web.client.RestClient;

/**
 * Spring configuration for the agent chat client and conversation memory.
 */
@Configuration
@EnableConfigurationProperties(CaseApiProperties.class)
public class AgentConfig {

    private static final String WEATHER_TIME_SYSTEM_PROMPT = """
            You can answer questions about the current weather for cities worldwide and the current time
            in cities that match Java's timezone registry.
            If the user asks about the weather or time, use the appropriate tool
            (getCurrentTime for time and getWeather for weather) to get the information and provide a clear
            and concise answer. If a tool reports that information is unavailable, politely explain that to the user.
            """;

    private static final String CASE_SYSTEM_PROMPT = """
            You answer questions about case information using read-only tools generated from the configured OpenAPI document.
            Always use the tools when the user asks for case information; do not attempt to answer those questions without tools.
            Use the tool names and parameter descriptions to decide which operation to call.
            Do not invent case data; if a tool reports an error or unavailable data, explain that clearly and concisely.
            Rather than giving raw information, respond as you were the inventor of dad jokes.
            """;

    @Bean
    public ChatMemory chatMemory() {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                .maxMessages(20)
                .build();
    }

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    public ChatClient weatherTimeChatClient(ChatClient.Builder chatClientBuilder,
                                            WeatherTimeAgent weatherTimeAgent,
                                            ChatMemory chatMemory) {
        return chatClientBuilder
                .defaultSystem(WEATHER_TIME_SYSTEM_PROMPT)
                .defaultTools(weatherTimeAgent)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }

    @Bean
    public ChatClient caseChatClient(ChatClient.Builder chatClientBuilder,
                                     OpenApiToolProvider openApiToolProvider,
                                     ChatMemory chatMemory) {
        return chatClientBuilder
                .defaultSystem(CASE_SYSTEM_PROMPT)
                .defaultToolCallbacks(openApiToolProvider.toolCallbacks())
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }
}
