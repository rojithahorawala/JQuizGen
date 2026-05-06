package com.quizgen.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quizgen.common.AIServiceException;
import com.quizgen.common.ErrorCodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Map;

@Component
public class GeminiClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);

    private final WebClient claudeWebClient;
    private final ObjectMapper objectMapper;

    @Value("${claude.api.model}")
    private String model;

    public GeminiClient(WebClient claudeWebClient, ObjectMapper objectMapper) {
        this.claudeWebClient = claudeWebClient;
        this.objectMapper = objectMapper;
    }

    public String generateContent(String prompt) {
        Map<String, Object> systemMessage = Map.of(
                "type", "text",
                "text", "You are an expert educational quiz generator. Respond with valid JSON only.",
                "cache_control", Map.of("type", "ephemeral")
        );
        return callClaude(systemMessage, prompt, 8192);
    }

    public String generateText(String prompt) {
        Map<String, Object> systemMessage = Map.of(
                "type", "text",
                "text", "You are a helpful educational assistant. Provide clear, concise explanations."
        );
        return callClaude(systemMessage, prompt, 300);
    }

    private String callClaude(Map<String, Object> systemMessage, String prompt, int maxTokens) {
        try {
            Map<String, Object> requestBody = Map.of(
                    "model", model,
                    "max_tokens", maxTokens,
                    "system", new Object[]{systemMessage},
                    "messages", new Object[]{Map.of("role", "user", "content", prompt)}
            );

            String responseJson = claudeWebClient.post()
                    .uri("/v1/messages")
                    .bodyValue(objectMapper.writeValueAsString(requestBody))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode root = objectMapper.readTree(responseJson);
            String content = root.path("content").get(0).path("text").asText();
            log.info("Claude API call successful, received {} chars", content.length());
            return content;

        } catch (WebClientResponseException e) {
            log.error("Claude API HTTP error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new AIServiceException(ErrorCodes.AI_001, "Claude API returned error: " + e.getStatusCode(), e);
        } catch (Exception e) {
            log.error("Claude API call failed", e);
            throw new AIServiceException(ErrorCodes.AI_001, "Failed to call Claude API: " + e.getMessage(), e);
        }
    }
}
