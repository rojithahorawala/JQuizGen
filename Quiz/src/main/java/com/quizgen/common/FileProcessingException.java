package com.quizgen.common;

public class FileProcessingException extends AppException {
    public FileProcessingException(String errorCode, String message) {
        super(errorCode, message);
    }

    public FileProcessingException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
