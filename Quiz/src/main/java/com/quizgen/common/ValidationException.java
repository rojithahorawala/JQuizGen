package com.quizgen.common;

public class ValidationException extends AppException {
    public ValidationException(String errorCode, String message) {
        super(errorCode, message);
    }
}
