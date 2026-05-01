package com.quizgen.common;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

@Controller
public class CustomErrorController implements ErrorController {

    @RequestMapping("/error")
    public ModelAndView handleError(HttpServletRequest request) {
        Integer statusCode = (Integer) request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        if (statusCode == null) statusCode = 500;

        String errorCode = switch (statusCode) {
            case 403 -> "AUTH-002";
            case 404 -> "QUIZ-001";
            case 401 -> "AUTH-001";
            default -> "SYS-001";
        };

        String message = switch (statusCode) {
            case 403 -> "You do not have permission to access this page.";
            case 404 -> "The page you are looking for could not be found.";
            case 401 -> "Please log in to continue.";
            default -> "An unexpected error occurred.";
        };

        ModelAndView mav = new ModelAndView("error/error");
        mav.setStatus(HttpStatus.valueOf(statusCode));
        mav.addObject("status", statusCode);
        mav.addObject("errorCode", errorCode);
        mav.addObject("errorMessage", message);
        return mav;
    }
}
