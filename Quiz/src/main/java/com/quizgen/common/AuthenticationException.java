package com.quizgen.common;

public class AuthenticationException extends AppException {
    public AuthenticationException(String errorCode, String message) {
        super(errorCode, message);
    }
}
