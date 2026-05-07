package com.quizgen.common;

import java.util.List;

public record AnswerDto(
    Long id,
    Long questionId,
    String questionText,
    String questionType,
    String correctAnswer,
    List<String> options,
    String answerText,
    Boolean correct,
    Integer pointsAwarded,
    String aiFeedback,
    int maxPoints
) {}
