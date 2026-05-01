package com.quizgen.auth;

import com.quizgen.common.ErrorCodes;
import com.quizgen.common.RegisterRequest;
import com.quizgen.common.ValidationException;
import com.quizgen.user.User;
import com.quizgen.user.UserRepository;
import com.quizgen.user.UserRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public User register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ValidationException(ErrorCodes.AUTH_003, "Email already in use: " + request.getEmail());
        }

        User user = new User();
        user.setEmail(request.getEmail().trim().toLowerCase());
        user.setUsername(request.getUsername().trim());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));

        UserRole role;
        try {
            role = UserRole.valueOf(request.getRole().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ValidationException(ErrorCodes.AUTH_003, "Invalid role: " + request.getRole());
        }
        user.setRole(role);

        User saved = userRepository.save(user);
        log.info("Registered new user: {} with role {}", saved.getEmail(), saved.getRole());
        return saved;
    }
}
