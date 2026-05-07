package com.quizgen.attempt;

import com.quizgen.common.*;
import com.quizgen.grading.GradingService;
import com.quizgen.quiz.Question;
import com.quizgen.quiz.QuestionRepository;
import com.quizgen.quiz.Quiz;
import com.quizgen.quiz.QuizRepository;
import com.quizgen.user.User;
import com.quizgen.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AttemptService {

    private static final Logger log = LoggerFactory.getLogger(AttemptService.class);

    private final AttemptRepository attemptRepository;
    private final AnswerRepository answerRepository;
    private final QuizRepository quizRepository;
    private final QuestionRepository questionRepository;
    private final UserRepository userRepository;
    private final GradingService gradingService;

    public AttemptService(AttemptRepository attemptRepository,
                          AnswerRepository answerRepository,
                          QuizRepository quizRepository,
                          QuestionRepository questionRepository,
                          UserRepository userRepository,
                          @Lazy GradingService gradingService) {
        this.attemptRepository = attemptRepository;
        this.answerRepository = answerRepository;
        this.quizRepository = quizRepository;
        this.questionRepository = questionRepository;
        this.userRepository = userRepository;
        this.gradingService = gradingService;
    }

    @PreAuthorize("hasRole('STUDENT')")
    @Transactional
    public AttemptDto startAttempt(Long quizId, Long studentId) {
        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCodes.QUIZ_001, "Quiz not found: " + quizId));

        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCodes.AUTH_001, "Student not found: " + studentId));

        Attempt attempt = new Attempt();
        attempt.setQuiz(quiz);
        attempt.setStudent(student);
        attempt.setStatus(AttemptStatus.IN_PROGRESS);
        attempt.setStartedAt(LocalDateTime.now());
        attempt = attemptRepository.save(attempt);

        log.info("Started attempt {} for student {} on quiz {}", attempt.getId(), studentId, quizId);
        return toDto(attempt);
    }

    @PreAuthorize("hasRole('STUDENT')")
    @Transactional
    public AttemptDto submitAttempt(Long attemptId, Map<String, String> answers, Long studentId) {
        Attempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCodes.QUIZ_001, "Attempt not found: " + attemptId));

        if (!attempt.getStudent().getId().equals(studentId)) {
            throw new AuthorizationException(ErrorCodes.AUTH_002, "Not your attempt");
        }

        List<Question> questions = questionRepository.findByQuizIdOrderByOrderIndexAsc(attempt.getQuiz().getId());

        List<Answer> answersToSave = new ArrayList<>();
        for (Question question : questions) {
            String answerText = answers.getOrDefault("q_" + question.getId(), "");
            Answer answer = new Answer();
            answer.setAttempt(attempt);
            answer.setQuestion(question);
            answer.setAnswerText(answerText);
            answersToSave.add(answer);
        }
        answerRepository.saveAll(answersToSave);

        attempt.setStatus(AttemptStatus.SUBMITTED);
        attempt.setSubmittedAt(LocalDateTime.now());
        attempt = attemptRepository.save(attempt);

        // Auto-grade MC and TF
        gradingService.gradeAutomatic(attemptId);

        log.info("Submitted attempt {} by student {}", attemptId, studentId);
        return toDto(attempt);
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = true)
    public AttemptDto getAttempt(Long attemptId) {
        Attempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCodes.QUIZ_001, "Attempt not found: " + attemptId));
        return toDto(attempt);
    }

    @PreAuthorize("hasRole('STUDENT')")
    @Transactional(readOnly = true)
    public List<AttemptDto> getStudentAttempts(Long studentId) {
        return attemptRepository.findByStudentIdOrderByStartedAtDesc(studentId).stream()
                .map(this::toSummaryDto)
                .collect(Collectors.toList());
    }

    @PreAuthorize("hasRole('TEACHER')")
    @Transactional(readOnly = true)
    public List<AttemptDto> getAttemptsByQuiz(Long quizId) {
        return attemptRepository.findByQuizIdOrderByStartedAtDesc(quizId).stream()
                .map(this::toSummaryDto)
                .collect(Collectors.toList());
    }

    @PreAuthorize("hasRole('TEACHER')")
    @Transactional(readOnly = true)
    public List<AttemptDto> getSubmittedAttemptsWithPendingGrading() {
        return attemptRepository.findSubmittedAttempts().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    private AttemptDto toSummaryDto(Attempt attempt) {
        String studentUsername = attempt.getStudent() != null ? attempt.getStudent().getUsername() : "";
        String studentEmail    = attempt.getStudent() != null ? attempt.getStudent().getEmail()    : "";
        return new AttemptDto(
                attempt.getId(),
                attempt.getQuiz() != null ? attempt.getQuiz().getId() : null,
                attempt.getQuiz() != null ? attempt.getQuiz().getTitle() : "",
                attempt.getStartedAt(),
                attempt.getSubmittedAt(),
                attempt.getScore(),
                attempt.getStatus().name(),
                List.of(),
                studentUsername,
                studentEmail
        );
    }

    private AttemptDto toDto(Attempt attempt) {
        List<AnswerDto> answerDtos = attempt.getAnswers().stream()
                .sorted(Comparator.comparing(a -> a.getQuestion() != null ? a.getQuestion().getOrderIndex() : 0))
                .map(a -> {
                    com.quizgen.quiz.Question q = a.getQuestion();
                    List<String> opts = (q != null && q.getOptions() != null)
                            ? q.getOptions().stream()
                                .map(com.quizgen.quiz.QuestionOption::getOptionText)
                                .collect(Collectors.toList())
                            : List.of();
                    return new AnswerDto(
                            a.getId(),
                            q != null ? q.getId() : null,
                            q != null ? q.getQuestionText() : "",
                            q != null ? q.getQuestionType().name() : "",
                            q != null ? q.getCorrectAnswer() : null,
                            opts,
                            a.getAnswerText(),
                            a.isCorrect(),
                            a.getPointsAwarded(),
                            a.getAiFeedback()
                    );
                })
                .collect(Collectors.toList());

        String studentUsername = attempt.getStudent() != null ? attempt.getStudent().getUsername() : "";
        String studentEmail    = attempt.getStudent() != null ? attempt.getStudent().getEmail()    : "";

        return new AttemptDto(
                attempt.getId(),
                attempt.getQuiz() != null ? attempt.getQuiz().getId() : null,
                attempt.getQuiz() != null ? attempt.getQuiz().getTitle() : "",
                attempt.getStartedAt(),
                attempt.getSubmittedAt(),
                attempt.getScore(),
                attempt.getStatus().name(),
                answerDtos,
                studentUsername,
                studentEmail
        );
    }
}
