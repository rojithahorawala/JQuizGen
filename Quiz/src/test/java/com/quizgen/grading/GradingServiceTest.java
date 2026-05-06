package com.quizgen.grading;

import com.quizgen.attempt.Answer;
import com.quizgen.attempt.Attempt;
import com.quizgen.attempt.AnswerRepository;
import com.quizgen.attempt.AttemptRepository;
import com.quizgen.attempt.AttemptStatus;
import com.quizgen.quiz.Question;
import com.quizgen.quiz.QuestionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GradingServiceTest {

    @Mock private AttemptRepository attemptRepository;
    @Mock private AnswerRepository answerRepository;

    @InjectMocks
    private GradingService gradingService;

    private Question mcQuestion;
    private Question tfQuestion;
    private Question frQuestion;
    private Attempt attempt;

    @BeforeEach
    void setUp() {
        mcQuestion = new Question();
        mcQuestion.setId(1L);
        mcQuestion.setQuestionType(QuestionType.MULTIPLE_CHOICE);
        mcQuestion.setCorrectAnswer("A");
        mcQuestion.setPoints(1);

        tfQuestion = new Question();
        tfQuestion.setId(2L);
        tfQuestion.setQuestionType(QuestionType.TRUE_FALSE);
        tfQuestion.setCorrectAnswer("TRUE");
        tfQuestion.setPoints(1);

        frQuestion = new Question();
        frQuestion.setId(3L);
        frQuestion.setQuestionType(QuestionType.FREE_RESPONSE);
        frQuestion.setPoints(5);

        attempt = new Attempt();
        attempt.setId(10L);
        attempt.setStatus(AttemptStatus.SUBMITTED);
    }

    @Test
    void gradesMcCorrectAnswer() {
        Answer answer = makeAnswer(mcQuestion, "A");
        when(answerRepository.findByAttemptId(10L)).thenReturn(List.of(answer));
        when(attemptRepository.findById(10L)).thenReturn(Optional.of(attempt));

        gradingService.gradeAutomatic(10L);

        assertThat(answer.isCorrect()).isTrue();
        assertThat(answer.getPointsAwarded()).isEqualTo(1);
    }

    @Test
    void gradesMcWrongAnswer() {
        Answer answer = makeAnswer(mcQuestion, "B");
        when(answerRepository.findByAttemptId(10L)).thenReturn(List.of(answer));
        when(attemptRepository.findById(10L)).thenReturn(Optional.of(attempt));

        gradingService.gradeAutomatic(10L);

        assertThat(answer.isCorrect()).isFalse();
        assertThat(answer.getPointsAwarded()).isEqualTo(0);
    }

    @Test
    void gradesTrueFalseCorrect() {
        Answer answer = makeAnswer(tfQuestion, "TRUE");
        when(answerRepository.findByAttemptId(10L)).thenReturn(List.of(answer));
        when(attemptRepository.findById(10L)).thenReturn(Optional.of(attempt));

        gradingService.gradeAutomatic(10L);

        assertThat(answer.isCorrect()).isTrue();
        assertThat(answer.getPointsAwarded()).isEqualTo(1);
    }

    @Test
    void gradesTrueFalseCaseInsensitive() {
        Answer answer = makeAnswer(tfQuestion, "true");
        when(answerRepository.findByAttemptId(10L)).thenReturn(List.of(answer));
        when(attemptRepository.findById(10L)).thenReturn(Optional.of(attempt));

        gradingService.gradeAutomatic(10L);

        assertThat(answer.isCorrect()).isTrue();
    }

    @Test
    void doesNotAutoGradeFreeResponse() {
        Answer answer = makeAnswer(frQuestion, "Some detailed answer");
        when(answerRepository.findByAttemptId(10L)).thenReturn(List.of(answer));
        when(attemptRepository.findById(10L)).thenReturn(Optional.of(attempt));

        gradingService.gradeAutomatic(10L);

        assertThat(answer.isCorrect()).isNull();
        assertThat(answer.getPointsAwarded()).isNull();
    }

    @Test
    void calculatesScoreAndSetsGradedWhenNoFreeResponsePending() {
        Answer answer = makeAnswer(mcQuestion, "A");
        when(answerRepository.findByAttemptId(10L)).thenReturn(List.of(answer));
        when(attemptRepository.findById(10L)).thenReturn(Optional.of(attempt));

        gradingService.gradeAutomatic(10L);

        assertThat(attempt.getStatus()).isEqualTo(AttemptStatus.GRADED);
        assertThat(attempt.getScore()).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    void doesNotSetScoreWhileFreeResponseIsUngraded() {
        Answer frAnswer = makeAnswer(frQuestion, "My explanation");
        // pointsAwarded is null = ungraded
        when(answerRepository.findByAttemptId(10L)).thenReturn(List.of(frAnswer));
        when(attemptRepository.findById(10L)).thenReturn(Optional.of(attempt));

        gradingService.gradeAutomatic(10L);

        assertThat(attempt.getScore()).isNull();
        assertThat(attempt.getStatus()).isEqualTo(AttemptStatus.SUBMITTED);
    }

    @Test
    void manualGradeSetsPointsAndIsCorrectTrue() {
        Answer frAnswer = makeAnswer(frQuestion, "Good answer");
        frAnswer.setId(99L);
        when(answerRepository.findByAttemptIdAndQuestionId(10L, 3L)).thenReturn(Optional.of(frAnswer));
        when(answerRepository.findByAttemptId(10L)).thenReturn(List.of(frAnswer));
        when(attemptRepository.findById(10L)).thenReturn(Optional.of(attempt));

        gradingService.submitManualGrade(10L, 3L, 4);

        assertThat(frAnswer.getPointsAwarded()).isEqualTo(4);
        assertThat(frAnswer.isCorrect()).isTrue();
    }

    @Test
    void manualGradeOfZeroSetsIsCorrectFalse() {
        Answer frAnswer = makeAnswer(frQuestion, "Wrong answer");
        frAnswer.setId(99L);
        when(answerRepository.findByAttemptIdAndQuestionId(10L, 3L)).thenReturn(Optional.of(frAnswer));
        when(answerRepository.findByAttemptId(10L)).thenReturn(List.of(frAnswer));
        when(attemptRepository.findById(10L)).thenReturn(Optional.of(attempt));

        gradingService.submitManualGrade(10L, 3L, 0);

        assertThat(frAnswer.getPointsAwarded()).isEqualTo(0);
        assertThat(frAnswer.isCorrect()).isFalse();
    }

    @Test
    void partialScoreCalculatedCorrectly() {
        // 1 MC worth 1 pt, answered wrong → 0/1 = 0%
        Answer mcAnswer = makeAnswer(mcQuestion, "B");
        Answer tfAnswerObj = makeAnswer(tfQuestion, "TRUE");
        // After auto-grade: mc=0pts, tf=1pt → 1/2 = 50%
        when(answerRepository.findByAttemptId(10L)).thenReturn(List.of(mcAnswer, tfAnswerObj));
        when(attemptRepository.findById(10L)).thenReturn(Optional.of(attempt));

        gradingService.gradeAutomatic(10L);

        assertThat(attempt.getScore()).isEqualByComparingTo(new BigDecimal("50.00"));
    }

    private Answer makeAnswer(Question question, String text) {
        Answer a = new Answer();
        a.setQuestion(question);
        a.setAnswerText(text);
        return a;
    }
}
