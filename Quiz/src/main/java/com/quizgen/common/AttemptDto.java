package com.quizgen.common;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record AttemptDto(Long id, Long quizId, String quizTitle, LocalDateTime startedAt, LocalDateTime submittedAt, BigDecimal score, String status, List<AnswerDto> answers, String studentUsername, String studentEmail) {}
