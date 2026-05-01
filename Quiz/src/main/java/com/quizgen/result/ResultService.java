package com.quizgen.result;

import com.quizgen.attempt.AttemptService;
import com.quizgen.common.AttemptDto;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ResultService {

    private final AttemptService attemptService;

    public ResultService(AttemptService attemptService) {
        this.attemptService = attemptService;
    }

    public List<AttemptDto> getResultsForQuiz(Long quizId) {
        return attemptService.getAttemptsByQuiz(quizId);
    }

    public List<AttemptDto> getResultsForStudent(Long studentId) {
        return attemptService.getStudentAttempts(studentId);
    }
}
