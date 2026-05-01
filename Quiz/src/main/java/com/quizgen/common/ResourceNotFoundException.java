package com.quizgen.common;

public class ResourceNotFoundException extends AppException {
    public ResourceNotFoundException(String errorCode, String message) {
        super(errorCode, message);
    }
}
