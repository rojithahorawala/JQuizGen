package com.quizgen.controller;

import com.quizgen.ai.QuizGenerationService;
import com.quizgen.attempt.AttemptService;
import com.quizgen.auth.CustomUserDetailsService;
import com.quizgen.common.AttemptDto;
import com.quizgen.common.QuizDto;
import com.quizgen.grading.GradingService;
import com.quizgen.quiz.QuizService;
import com.quizgen.quiz.TeacherController;
import com.quizgen.result.ResultService;
import com.quizgen.user.User;
import com.quizgen.user.UserRepository;
import com.quizgen.user.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TeacherController.class)
class TeacherControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean QuizGenerationService quizGenerationService;
    @MockBean QuizService quizService;
    @MockBean AttemptService attemptService;
    @MockBean GradingService gradingService;
    @MockBean ResultService resultService;
    @MockBean UserRepository userRepository;
    @MockBean CustomUserDetailsService customUserDetailsService;

    @Test
    @WithMockUser(username = "teacher@test.com", roles = "TEACHER")
    void dashboardLoadsForTeacher() throws Exception {
        User teacher = makeUser(1L, "teacher@test.com", UserRole.TEACHER);
        when(userRepository.findByEmail("teacher@test.com")).thenReturn(Optional.of(teacher));
        when(quizService.getQuizzesByTeacher(1L)).thenReturn(List.of());
        when(attemptService.getSubmittedAttemptsWithPendingGrading()).thenReturn(List.of());

        mockMvc.perform(get("/teacher/dashboard"))
                .andExpect(status().isOk())
                .andExpect(view().name("teacher/dashboard"))
                .andExpect(model().attributeExists("quizzes", "pendingGradingCount", "user"));
    }

    @Test
    @WithMockUser(username = "teacher@test.com", roles = "TEACHER")
    void gradePageRendersWithPendingAttempts() throws Exception {
        when(attemptService.getSubmittedAttemptsWithPendingGrading()).thenReturn(List.of());

        mockMvc.perform(get("/teacher/grade"))
                .andExpect(status().isOk())
                .andExpect(view().name("teacher/grade"))
                .andExpect(model().attributeExists("pendingAttempts"));
    }

    @Test
    @WithMockUser(username = "teacher@test.com", roles = "TEACHER")
    void resultsPageRendersForQuiz() throws Exception {
        QuizDto quiz = new QuizDto(1L, "Test Quiz", "UNIVERSAL", "READY", 10, LocalDateTime.now(), List.of());
        when(quizService.getQuizById(1L)).thenReturn(quiz);
        when(resultService.getResultsForQuiz(1L)).thenReturn(List.of());

        mockMvc.perform(get("/teacher/results/1"))
                .andExpect(status().isOk())
                .andExpect(view().name("teacher/results"))
                .andExpect(model().attributeExists("quiz", "results"));
    }

    @Test
    @WithMockUser(username = "teacher@test.com", roles = "TEACHER")
    void submitGradeRedirectsToGradePage() throws Exception {
        mockMvc.perform(post("/teacher/grade/10/question/3")
                        .with(csrf())
                        .param("pointsAwarded", "4"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/teacher/grade"));
    }

    @Test
    @WithMockUser(username = "teacher@test.com", roles = "TEACHER")
    void uploadPageRendersForTeacher() throws Exception {
        mockMvc.perform(get("/teacher/upload"))
                .andExpect(status().isOk())
                .andExpect(view().name("teacher/upload"));
    }

    private User makeUser(Long id, String email, UserRole role) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setUsername(email.split("@")[0]);
        user.setRole(role);
        return user;
    }
}
