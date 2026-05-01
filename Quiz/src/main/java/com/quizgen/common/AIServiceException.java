package com.quizgen.common;

public class AIServiceException extends AppException {
    public AIServiceException(String errorCode, String message) {
        super(errorCode, message);
    }

    public AIServiceException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
