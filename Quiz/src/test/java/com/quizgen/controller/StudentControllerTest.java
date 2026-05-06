package com.quizgen.controller;

import com.quizgen.ai.QuizGenerationService;
import com.quizgen.attempt.AttemptService;
import com.quizgen.auth.CustomUserDetailsService;
import com.quizgen.quiz.QuizService;
import com.quizgen.quiz.StudentController;
import com.quizgen.user.User;
import com.quizgen.user.UserRepository;
import com.quizgen.user.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(StudentController.class)
class StudentControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean QuizGenerationService quizGenerationService;
    @MockBean QuizService quizService;
    @MockBean AttemptService attemptService;
    @MockBean UserRepository userRepository;
    @MockBean CustomUserDetailsService customUserDetailsService;

    @Test
    @WithMockUser(username = "student@test.com", roles = "STUDENT")
    void dashboardLoadsForStudent() throws Exception {
        User user = makeUser(1L, "student@test.com", UserRole.STUDENT);
        when(userRepository.findByEmail("student@test.com")).thenReturn(Optional.of(user));
        when(quizService.getAvailableQuizzesForStudent(1L)).thenReturn(List.of());
        when(quizService.getPersonalQuizzesByStudent(1L)).thenReturn(List.of());
        when(attemptService.getStudentAttempts(1L)).thenReturn(List.of());

        mockMvc.perform(get("/student/dashboard"))
                .andExpect(status().isOk())
                .andExpect(view().name("student/dashboard"))
                .andExpect(model().attributeExists("user", "availableQuizzes", "personalQuizzes", "history"));
    }

    @Test
    @WithMockUser(username = "student@test.com", roles = "STUDENT")
    void uploadPageRendersForStudent() throws Exception {
        mockMvc.perform(get("/student/upload"))
                .andExpect(status().isOk())
                .andExpect(view().name("student/upload"));
    }

    @Test
    @WithMockUser(username = "student@test.com", roles = "STUDENT")
    void attemptDetailRedirectsIfNotOwner() throws Exception {
        User user = makeUser(1L, "student@test.com", UserRole.STUDENT);
        user.setUsername("student");

        com.quizgen.common.AttemptDto dto = new com.quizgen.common.AttemptDto(
                99L, 1L, "Quiz", null, null, null,
                "SUBMITTED", List.of(), "other_student", "other@test.com"
        );

        when(userRepository.findByEmail("student@test.com")).thenReturn(Optional.of(user));
        when(attemptService.getAttempt(99L)).thenReturn(dto);

        mockMvc.perform(get("/student/attempts/99"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/student/dashboard"));
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
