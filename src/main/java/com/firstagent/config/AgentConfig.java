package com.firstagent.config;

import com.firstagent.agent.WeatherTimeAgent;
import com.google.adk.agents.BaseAgent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration that exposes Google ADK agents as Spring beans.
 * This makes agents injectable into controllers and services.
 */
@Configuration
public class AgentConfig {

    @Bean
    public BaseAgent weatherTimeAgent() {
        return WeatherTimeAgent.ROOT_AGENT;
    }
}
