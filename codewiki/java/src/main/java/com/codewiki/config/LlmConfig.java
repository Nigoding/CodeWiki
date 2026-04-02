package com.codewiki.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Spring AI ChatClient / ChatModel bean definitions.
 *
 * Two separate ChatClient beans are produced:
 *  - primaryChatClient  – uses the preferred model (e.g. gpt-4o)
 *  - fallbackChatClient – uses the cheaper / more available model (e.g. gpt-4o-mini)
 *
 * Strategy beans inject whichever they need via @Primary / @Qualifier.
 * This is preferable to a single "smart" client that switches models internally,
 * because each strategy can decide independently when to fall back.
 */
@Configuration
@EnableConfigurationProperties(LlmProperties.class)
public class LlmConfig {

    @Bean
    @Primary
    public OpenAiChatModel primaryChatModel(LlmProperties props) {
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(props.getBaseUrl())
                .apiKey(props.getApiKey())
                .build();

        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(props.getPrimaryModel())
                .temperature(props.getTemperature())
                .maxTokens(props.getMaxTokens())
                .build();

        return OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(options)
                .build();
    }

    @Bean
    @Qualifier("fallback")
    public OpenAiChatModel fallbackChatModel(LlmProperties props) {
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(props.getBaseUrl())
                .apiKey(props.getApiKey())
                .build();

        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(props.getFallbackModel())
                .temperature(props.getTemperature())
                .maxTokens(props.getMaxTokens())
                .build();

        return OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(options)
                .build();
    }

    /** ChatClient wrapping the primary model. */
    @Bean
    @Primary
    public ChatClient primaryChatClient(@Primary OpenAiChatModel primaryChatModel) {
        return ChatClient.builder(primaryChatModel).build();
    }

    /** ChatClient wrapping the fallback model. */
    @Bean
    @Qualifier("fallback")
    public ChatClient fallbackChatClient(@Qualifier("fallback") OpenAiChatModel fallbackChatModel) {
        return ChatClient.builder(fallbackChatModel).build();
    }
}
