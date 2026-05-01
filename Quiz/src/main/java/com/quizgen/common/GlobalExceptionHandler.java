package com.quizgen.common;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ModelAndView handleNotFound(ResourceNotFoundException ex) {
        log.warn("Resource not found: {} - {}", ex.getErrorCode(), ex.getMessage());
        return buildErrorView(HttpStatus.NOT_FOUND, ex.getErrorCode(), ex.getMessage());
    }

    @ExceptionHandler(AuthorizationException.class)
    public ModelAndView handleForbidden(AuthorizationException ex) {
        log.warn("Authorization error: {} - {}", ex.getErrorCode(), ex.getMessage());
        return buildErrorView(HttpStatus.FORBIDDEN, ex.getErrorCode(), ex.getMessage());
    }

    @ExceptionHandler(AuthenticationException.class)
    public ModelAndView handleUnauthorized(AuthenticationException ex) {
        log.warn("Authentication error: {} - {}", ex.getErrorCode(), ex.getMessage());
        return buildErrorView(HttpStatus.UNAUTHORIZED, ex.getErrorCode(), ex.getMessage());
    }

    @ExceptionHandler(ValidationException.class)
    public ModelAndView handleValidation(ValidationException ex) {
        log.warn("Validation error: {} - {}", ex.getErrorCode(), ex.getMessage());
        return buildErrorView(HttpStatus.BAD_REQUEST, ex.getErrorCode(), ex.getMessage());
    }

    @ExceptionHandler(FileProcessingException.class)
    public ModelAndView handleFileProcessing(FileProcessingException ex) {
        log.error("File processing error: {} - {}", ex.getErrorCode(), ex.getMessage());
        return buildErrorView(HttpStatus.UNPROCESSABLE_ENTITY, ex.getErrorCode(), ex.getMessage());
    }

    @ExceptionHandler(AIServiceException.class)
    public ModelAndView handleAIService(AIServiceException ex) {
        log.error("AI service error: {} - {}", ex.getErrorCode(), ex.getMessage());
        return buildErrorView(HttpStatus.BAD_GATEWAY, ex.getErrorCode(), ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public Object handleGeneral(Exception ex, HttpServletRequest request) {
        if (ex instanceof org.springframework.security.access.AccessDeniedException ae) throw ae;
        if (ex instanceof org.springframework.security.core.AuthenticationException ae) throw ae;
        log.error("Unexpected error", ex);
        return buildErrorView(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCodes.SYS_001, "An unexpected error occurred.");
    }

    private ModelAndView buildErrorView(HttpStatus status, String errorCode, String message) {
        ModelAndView mav = new ModelAndView("error/error");
        mav.setStatus(status);
        mav.addObject("status", status.value());
        mav.addObject("errorCode", errorCode);
        mav.addObject("errorMessage", message);
        return mav;
    }
}
