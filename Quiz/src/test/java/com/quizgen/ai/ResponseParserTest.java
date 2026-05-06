package com.quizgen.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quizgen.common.AIServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResponseParserTest {

    private ResponseParser parser;

    @BeforeEach
    void setUp() {
        parser = new ResponseParser(new ObjectMapper());
    }

    @Test
    void parsesMultipleChoiceQuestion() {
        String json = """
                {"questions":[
                  {"type":"MULTIPLE_CHOICE","text":"What is Java?",
                   "options":["A language","A coffee","A tool","An OS"],
                   "correctAnswer":"A","points":1}
                ]}""";

        List<ParsedQuestion> questions = parser.parse(json);

        assertThat(questions).hasSize(1);
        assertThat(questions.get(0).type()).isEqualTo("MULTIPLE_CHOICE");
        assertThat(questions.get(0).correctAnswer()).isEqualTo("A");
        assertThat(questions.get(0).options()).hasSize(4);
        assertThat(questions.get(0).points()).isEqualTo(1);
    }

    @Test
    void parsesTrueFalseQuestion() {
        String json = """
                {"questions":[
                  {"type":"TRUE_FALSE","text":"Java is object-oriented?",
                   "correctAnswer":"TRUE","points":1}
                ]}""";

        List<ParsedQuestion> questions = parser.parse(json);

        assertThat(questions.get(0).type()).isEqualTo("TRUE_FALSE");
        assertThat(questions.get(0).correctAnswer()).isEqualTo("TRUE");
        assertThat(questions.get(0).options()).isEmpty();
    }

    @Test
    void parsesFreeResponseQuestion() {
        String json = """
                {"questions":[
                  {"type":"FREE_RESPONSE","text":"Explain polymorphism.","points":5}
                ]}""";

        List<ParsedQuestion> questions = parser.parse(json);

        assertThat(questions.get(0).type()).isEqualTo("FREE_RESPONSE");
        assertThat(questions.get(0).points()).isEqualTo(5);
        assertThat(questions.get(0).correctAnswer()).isNull();
    }

    @Test
    void stripsMarkdownCodeFences() {
        String fenced = "```json\n{\"questions\":[{\"type\":\"TRUE_FALSE\",\"text\":\"Q?\",\"correctAnswer\":\"TRUE\",\"points\":1}]}\n```";

        List<ParsedQuestion> questions = parser.parse(fenced);

        assertThat(questions).hasSize(1);
    }

    @Test
    void parsesMultipleMixedQuestions() {
        String json = """
                {"questions":[
                  {"type":"MULTIPLE_CHOICE","text":"Q1?","options":["A","B","C","D"],"correctAnswer":"B","points":1},
                  {"type":"TRUE_FALSE","text":"Q2?","correctAnswer":"FALSE","points":1},
                  {"type":"FREE_RESPONSE","text":"Q3?","points":5}
                ]}""";

        List<ParsedQuestion> questions = parser.parse(json);

        assertThat(questions).hasSize(3);
    }

    @Test
    void throwsOnMissingQuestionsArray() {
        String json = "{\"data\":[]}";

        assertThatThrownBy(() -> parser.parse(json))
                .isInstanceOf(AIServiceException.class)
                .hasMessageContaining("questions");
    }

    @Test
    void throwsOnInvalidJson() {
        assertThatThrownBy(() -> parser.parse("not json at all {{{}"))
                .isInstanceOf(AIServiceException.class);
    }
}
