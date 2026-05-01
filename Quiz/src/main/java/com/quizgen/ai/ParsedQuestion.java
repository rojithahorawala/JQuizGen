package com.quizgen.ai;

import java.util.List;

public record ParsedQuestion(
        String type,
        String text,
        List<String> options,
        String correctAnswer,
        int points
) {}
