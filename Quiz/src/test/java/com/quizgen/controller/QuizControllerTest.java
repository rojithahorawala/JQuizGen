package com.quizgen.controller;

import com.quizgen.ai.QuizGenerationService;
import com.quizgen.attempt.AttemptService;
import com.quizgen.auth.CustomUserDetailsService;
import com.quizgen.common.AttemptDto;
import com.quizgen.common.GenerationStatusDto;
import com.quizgen.quiz.QuizController;
import com.quizgen.quiz.QuizService;
import com.quizgen.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(QuizController.class)
class QuizControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean QuizGenerationService quizGenerationService;
    @MockBean QuizService quizService;
    @MockBean AttemptService attemptService;
    @MockBean UserRepository userRepository;
    @MockBean CustomUserDetailsService customUserDetailsService;

    @Test
    @WithMockUser
    void statusEndpointReturnsJsonWithStatusAndQuizId() throws Exception {
        when(quizGenerationService.getJobStatus(1L))
                .thenReturn(new GenerationStatusDto("READY", 5L, null));

        mockMvc.perform(get("/quiz/status/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.quizId").value(5));
    }

    @Test
    @WithMockUser
    void statusEndpointReturnsFailedWithErrorCode() throws Exception {
        when(quizGenerationService.getJobStatus(2L))
                .thenReturn(new GenerationStatusDto("FAILED", null, "AI-001"));

        mockMvc.perform(get("/quiz/status/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.errorCode").value("AI-001"));
    }

    @Test
    @WithMockUser
    void generatingPageRendersWithJobId() throws Exception {
        mockMvc.perform(get("/quiz/generating/1"))
                .andExpect(status().isOk())
                .andExpect(view().name("quiz/generating"))
                .andExpect(model().attribute("jobId", 1L));
    }

    @Test
    @WithMockUser
    void completePageRendersWithAttempt() throws Exception {
        AttemptDto dto = new AttemptDto(
                1L, 1L, "Test Quiz",
                LocalDateTime.now(), LocalDateTime.now(),
                null, "SUBMITTED", List.of(),
                "student", "student@test.com"
        );
        when(attemptService.getAttempt(1L)).thenReturn(dto);

        mockMvc.perform(get("/quiz/complete/1"))
                .andExpect(status().isOk())
                .andExpect(view().name("quiz/complete"))
                .andExpect(model().attributeExists("attempt"));
    }

}
