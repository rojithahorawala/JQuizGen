package com.quizgen.common;

import java.time.LocalDateTime;
import java.util.List;

public record QuizDto(Long id, String title, String scope, String status, int questionCount, LocalDateTime createdAt, List<QuestionDto> questions) {}
