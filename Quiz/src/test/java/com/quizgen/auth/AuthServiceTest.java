package com.quizgen.auth;

import com.quizgen.common.RegisterRequest;
import com.quizgen.common.ValidationException;
import com.quizgen.user.User;
import com.quizgen.user.UserRepository;
import com.quizgen.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthService authService;

    private RegisterRequest request;

    @BeforeEach
    void setUp() {
        request = new RegisterRequest();
        request.setEmail("student@test.com");
        request.setUsername("testuser");
        request.setPassword("password123");
        request.setRole("STUDENT");
    }

    @Test
    void registersNewUserSuccessfully() {
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed_password");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User result = authService.register(request);

        assertThat(result.getEmail()).isEqualTo("student@test.com");
        assertThat(result.getRole()).isEqualTo(UserRole.STUDENT);
        verify(userRepository).save(any(User.class));
    }

    @Test
    void throwsValidationExceptionOnDuplicateEmail() {
        when(userRepository.existsByEmail(anyString())).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void throwsValidationExceptionOnInvalidRole() {
        request.setRole("ADMIN");
        when(userRepository.existsByEmail(anyString())).thenReturn(false);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void normalizesEmailToLowercaseAndTrimmed() {
        request.setEmail("  STUDENT@TEST.COM  ");
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User result = authService.register(request);

        assertThat(result.getEmail()).isEqualTo("student@test.com");
    }

    @Test
    void encodesPasswordBeforeSaving() {
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("$2a$bcrypt_hash");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User result = authService.register(request);

        assertThat(result.getPasswordHash()).isEqualTo("$2a$bcrypt_hash");
    }

    @Test
    void acceptsTeacherRole() {
        request.setRole("TEACHER");
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User result = authService.register(request);

        assertThat(result.getRole()).isEqualTo(UserRole.TEACHER);
    }
}
