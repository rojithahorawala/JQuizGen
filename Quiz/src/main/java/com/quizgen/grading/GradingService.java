package com.quizgen.grading;

import com.quizgen.attempt.Answer;
import com.quizgen.attempt.AnswerRepository;
import com.quizgen.attempt.Attempt;
import com.quizgen.attempt.AttemptRepository;
import com.quizgen.attempt.AttemptStatus;
import com.quizgen.common.ErrorCodes;
import com.quizgen.common.ResourceNotFoundException;
import com.quizgen.quiz.Question;
import com.quizgen.quiz.QuestionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
public class GradingService {

    private static final Logger log = LoggerFactory.getLogger(GradingService.class);

    private final AttemptRepository attemptRepository;
    private final AnswerRepository answerRepository;

    public GradingService(AttemptRepository attemptRepository, AnswerRepository answerRepository) {
        this.attemptRepository = attemptRepository;
        this.answerRepository = answerRepository;
    }

    @Transactional
    public void gradeAutomatic(Long attemptId) {
        List<Answer> answers = answerRepository.findByAttemptId(attemptId);

        List<Answer> modified = new ArrayList<>();
        for (Answer answer : answers) {
            Question question = answer.getQuestion();
            if (question == null) continue;

            QuestionType type = question.getQuestionType();
            if (type == QuestionType.MULTIPLE_CHOICE || type == QuestionType.TRUE_FALSE) {
                String correct = question.getCorrectAnswer();
                boolean isCorrect = correct != null && correct.equalsIgnoreCase(answer.getAnswerText().trim());
                answer.setCorrect(isCorrect);
                answer.setPointsAwarded(isCorrect ? question.getPoints() : 0);
                modified.add(answer);
            }
        }
        if (!modified.isEmpty()) {
            answerRepository.saveAll(modified);
        }

        doRecalculateScore(attemptId, answers);
        log.info("Auto-graded attempt {}", attemptId);
    }

    @PreAuthorize("hasRole('TEACHER')")
    @Transactional
    public void submitManualGrade(Long attemptId, Long questionId, int pointsAwarded) {
        Answer answer = answerRepository.findFirstByAttemptIdAndQuestionId(attemptId, questionId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCodes.GRADE_001,
                        "Answer not found for attempt " + attemptId + " question " + questionId));

        answer.setPointsAwarded(pointsAwarded);
        answer.setCorrect(pointsAwarded > 0);
        answerRepository.save(answer);

        recalculateScore(attemptId);
        log.info("Manual grade submitted for attempt {} question {}: {} points", attemptId, questionId, pointsAwarded);
    }

    @Transactional
    protected void recalculateScore(Long attemptId) {
        doRecalculateScore(attemptId, answerRepository.findByAttemptId(attemptId));
    }

    private void doRecalculateScore(Long attemptId, List<Answer> answers) {
        Attempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCodes.GRADE_002, "Attempt not found: " + attemptId));

        boolean hasPendingFreeResponse = answers.stream()
                .anyMatch(a -> a.getQuestion() != null
                        && a.getQuestion().getQuestionType() == QuestionType.FREE_RESPONSE
                        && a.getPointsAwarded() == null);

        if (!hasPendingFreeResponse) {
            int totalPoints = answers.stream()
                    .filter(a -> a.getQuestion() != null)
                    .mapToInt(a -> a.getQuestion().getPoints())
                    .sum();

            int awardedPoints = answers.stream()
                    .filter(a -> a.getPointsAwarded() != null)
                    .mapToInt(Answer::getPointsAwarded)
                    .sum();

            BigDecimal score = totalPoints > 0
                    ? BigDecimal.valueOf(awardedPoints).multiply(BigDecimal.valueOf(100))
                              .divide(BigDecimal.valueOf(totalPoints), 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            attempt.setScore(score);
            attempt.setStatus(AttemptStatus.GRADED);
            attemptRepository.save(attempt);
            log.info("Calculated score for attempt {}: {}", attemptId, score);
        }
    }
}
