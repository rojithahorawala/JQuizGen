package com.quizgen.controller;

import com.quizgen.auth.AuthController;
import com.quizgen.auth.AuthService;
import com.quizgen.auth.CustomUserDetailsService;
import com.quizgen.common.ErrorCodes;
import com.quizgen.common.RegisterRequest;
import com.quizgen.common.ValidationException;
import com.quizgen.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    AuthService authService;

    @MockBean
    CustomUserDetailsService customUserDetailsService;

    @Test
    void loginPageReturns200() throws Exception {
        mockMvc.perform(get("/auth/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/login"));
    }

    @Test
    void registerPageReturns200() throws Exception {
        mockMvc.perform(get("/auth/register"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/register"));
    }

    @Test
    void registerRedirectsToLoginOnSuccess() throws Exception {
        when(authService.register(any(RegisterRequest.class))).thenReturn(new User());

        mockMvc.perform(post("/auth/register")
                        .param("email", "new@test.com")
                        .param("username", "newuser")
                        .param("password", "password123")
                        .param("role", "STUDENT"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/auth/login?registered=true"));
    }

    @Test
    void registerShowsErrorOnDuplicateEmail() throws Exception {
        when(authService.register(any(RegisterRequest.class)))
                .thenThrow(new ValidationException(ErrorCodes.AUTH_003, "Email already in use"));

        mockMvc.perform(post("/auth/register")
                        .param("email", "existing@test.com")
                        .param("username", "testuser")
                        .param("password", "password123")
                        .param("role", "STUDENT"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/register"))
                .andExpect(model().attributeExists("error"));
    }

    @Test
    void unauthenticatedUserCanAccessLoginPage() throws Exception {
        mockMvc.perform(get("/auth/login"))
                .andExpect(status().isOk());
    }

    @Test
    void registerPageHasRegisterRequestAttribute() throws Exception {
        mockMvc.perform(get("/auth/register"))
                .andExpect(model().attributeExists("registerRequest"));
    }
}
