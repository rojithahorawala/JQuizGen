package com.quizgen.common;

import java.util.List;

public record QuestionDto(Long id, String questionText, String questionType, int points, int orderIndex, List<QuestionOptionDto> options) {}
