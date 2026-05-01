package com.quizgen.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quizgen.common.AIServiceException;
import com.quizgen.common.ErrorCodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ResponseParser {

    private static final Logger log = LoggerFactory.getLogger(ResponseParser.class);

    private final ObjectMapper objectMapper;

    public ResponseParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<ParsedQuestion> parse(String jsonResponse) {
        try {
            // Strip potential markdown code fences
            String cleaned = jsonResponse.trim();
            if (cleaned.startsWith("```")) {
                int start = cleaned.indexOf('\n') + 1;
                int end = cleaned.lastIndexOf("```");
                if (end > start) {
                    cleaned = cleaned.substring(start, end).trim();
                }
            }

            JsonNode root = objectMapper.readTree(cleaned);
            JsonNode questionsNode = root.path("questions");

            if (!questionsNode.isArray()) {
                throw new AIServiceException(ErrorCodes.AI_002, "Response does not contain a 'questions' array");
            }

            List<ParsedQuestion> questions = new ArrayList<>();
            for (JsonNode qNode : questionsNode) {
                String type = qNode.path("type").asText();
                String text = qNode.path("text").asText();
                String correctAnswer = qNode.path("correctAnswer").asText(null);
                int points = qNode.path("points").asInt(1);

                List<String> options = new ArrayList<>();
                JsonNode optionsNode = qNode.path("options");
                if (optionsNode.isArray()) {
                    for (JsonNode opt : optionsNode) {
                        options.add(opt.asText());
                    }
                }

                questions.add(new ParsedQuestion(type, text, options, correctAnswer, points));
            }

            log.info("Parsed {} questions from AI response", questions.size());
            return questions;

        } catch (AIServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to parse AI response: {}", jsonResponse.substring(0, Math.min(200, jsonResponse.length())));
            throw new AIServiceException(ErrorCodes.AI_002, "Failed to parse AI response: " + e.getMessage(), e);
        }
    }
}
