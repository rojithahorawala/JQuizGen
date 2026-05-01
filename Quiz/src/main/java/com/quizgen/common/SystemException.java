package com.quizgen.common;

public class SystemException extends AppException {
    public SystemException(String errorCode, String message) {
        super(errorCode, message);
    }

    public SystemException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
