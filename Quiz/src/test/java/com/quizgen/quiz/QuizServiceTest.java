package com.quizgen.quiz;

import com.quizgen.common.QuizDto;
import com.quizgen.common.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuizServiceTest {

    @Mock
    private QuizRepository quizRepository;

    @InjectMocks
    private QuizService quizService;

    private Quiz sampleQuiz;

    @BeforeEach
    void setUp() {
        sampleQuiz = new Quiz();
        sampleQuiz.setId(1L);
        sampleQuiz.setTitle("Test Quiz");
        sampleQuiz.setScope(QuizScope.UNIVERSAL);
        sampleQuiz.setStatus(QuizStatus.READY);
        sampleQuiz.setQuestionCount(10);
    }

    @Test
    void getQuizByIdReturnsDto() {
        when(quizRepository.findById(1L)).thenReturn(Optional.of(sampleQuiz));

        QuizDto dto = quizService.getQuizById(1L);

        assertThat(dto.id()).isEqualTo(1L);
        assertThat(dto.title()).isEqualTo("Test Quiz");
        assertThat(dto.scope()).isEqualTo("UNIVERSAL");
        assertThat(dto.status()).isEqualTo("READY");
    }

    @Test
    void getQuizByIdThrowsWhenNotFound() {
        when(quizRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> quizService.getQuizById(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getAvailableQuizzesForStudentReturnsList() {
        when(quizRepository.findAvailableUniversalQuizzes()).thenReturn(List.of(sampleQuiz));

        List<QuizDto> result = quizService.getAvailableQuizzesForStudent(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).scope()).isEqualTo("UNIVERSAL");
    }

    @Test
    void getPersonalQuizzesByStudentReturnsList() {
        Quiz personal = new Quiz();
        personal.setId(2L);
        personal.setTitle("My Notes Quiz");
        personal.setScope(QuizScope.PERSONAL);
        personal.setStatus(QuizStatus.READY);
        personal.setQuestionCount(5);

        when(quizRepository.findPersonalQuizzesByStudent(42L)).thenReturn(List.of(personal));

        List<QuizDto> result = quizService.getPersonalQuizzesByStudent(42L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).scope()).isEqualTo("PERSONAL");
    }

    @Test
    void getQuizzesByTeacherReturnsList() {
        when(quizRepository.findByCreatedByIdOrderByCreatedAtDesc(5L)).thenReturn(List.of(sampleQuiz));

        List<QuizDto> result = quizService.getQuizzesByTeacher(5L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).title()).isEqualTo("Test Quiz");
    }

    @Test
    void getAvailableQuizzesReturnsEmptyListWhenNone() {
        when(quizRepository.findAvailableUniversalQuizzes()).thenReturn(List.of());

        List<QuizDto> result = quizService.getAvailableQuizzesForStudent(1L);

        assertThat(result).isEmpty();
    }
}
