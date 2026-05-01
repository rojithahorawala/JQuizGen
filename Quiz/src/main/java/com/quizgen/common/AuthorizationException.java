package com.quizgen.common;

public class AuthorizationException extends AppException {
    public AuthorizationException(String errorCode, String message) {
        super(errorCode, message);
    }
}
