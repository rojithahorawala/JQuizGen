package com.quizgen.quiz;

import com.quizgen.common.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class QuizService {

    private static final Logger log = LoggerFactory.getLogger(QuizService.class);

    private final QuizRepository quizRepository;

    public QuizService(QuizRepository quizRepository) {
        this.quizRepository = quizRepository;
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = true)
    public QuizDto getQuizById(Long id) {
        Quiz quiz = quizRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCodes.QUIZ_001, "Quiz not found: " + id));
        return toDto(quiz);
    }

    @Transactional(readOnly = true)
    public List<QuizDto> getAvailableQuizzesForStudent(Long studentId) {
        return quizRepository.findAvailableUniversalQuizzes().stream()
                .map(this::toDtoSummary)
                .collect(Collectors.toList());
    }

    @PreAuthorize("hasRole('TEACHER')")
    @Transactional(readOnly = true)
    public List<QuizDto> getQuizzesByTeacher(Long teacherId) {
        return quizRepository.findByCreatedByIdOrderByCreatedAtDesc(teacherId).stream()
                .map(this::toDtoSummary)
                .collect(Collectors.toList());
    }

    @PreAuthorize("hasRole('STUDENT')")
    @Transactional(readOnly = true)
    public List<QuizDto> getPersonalQuizzesByStudent(Long studentId) {
        return quizRepository.findPersonalQuizzesByStudent(studentId).stream()
                .map(this::toDtoSummary)
                .collect(Collectors.toList());
    }

    private QuizDto toDto(Quiz quiz) {
        List<QuestionDto> questionDtos = quiz.getQuestions().stream()
                .map(q -> {
                    List<QuestionOptionDto> optionDtos = q.getOptions().stream()
                            .map(o -> new QuestionOptionDto(o.getId(), o.getOptionText(), o.isCorrect()))
                            .collect(Collectors.toList());
                    return new QuestionDto(q.getId(), q.getQuestionText(),
                            q.getQuestionType().name(), q.getPoints(), q.getOrderIndex(), optionDtos);
                })
                .collect(Collectors.toList());
        return new QuizDto(quiz.getId(), quiz.getTitle(), quiz.getScope().name(),
                quiz.getStatus().name(), quiz.getQuestionCount(), quiz.getCreatedAt(), questionDtos);
    }

    private QuizDto toDtoSummary(Quiz quiz) {
        return new QuizDto(quiz.getId(), quiz.getTitle(), quiz.getScope().name(),
                quiz.getStatus().name(), quiz.getQuestionCount(), quiz.getCreatedAt(), List.of());
    }
}
