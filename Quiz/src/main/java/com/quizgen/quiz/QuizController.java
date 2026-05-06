package com.quizgen.quiz;

import com.quizgen.ai.AIFeedbackService;
import com.quizgen.ai.QuizGenerationService;
import com.quizgen.attempt.AttemptService;
import com.quizgen.common.AttemptDto;
import com.quizgen.common.GenerationStatusDto;
import com.quizgen.common.QuizDto;
import com.quizgen.user.User;
import com.quizgen.user.UserRepository;
import com.quizgen.user.UserRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Controller
@RequestMapping("/quiz")
public class QuizController {

    private static final Logger log = LoggerFactory.getLogger(QuizController.class);

    private final QuizGenerationService quizGenerationService;
    private final QuizService quizService;
    private final AttemptService attemptService;
    private final UserRepository userRepository;
    private final AIFeedbackService aiFeedbackService;

    public QuizController(QuizGenerationService quizGenerationService,
                          QuizService quizService,
                          AttemptService attemptService,
                          UserRepository userRepository,
                          AIFeedbackService aiFeedbackService) {
        this.quizGenerationService = quizGenerationService;
        this.quizService = quizService;
        this.attemptService = attemptService;
        this.userRepository = userRepository;
        this.aiFeedbackService = aiFeedbackService;
    }

    @GetMapping("/status/{jobId}")
    @ResponseBody
    public ResponseEntity<GenerationStatusDto> getStatus(@PathVariable Long jobId) {
        GenerationStatusDto status = quizGenerationService.getJobStatus(jobId);
        return ResponseEntity.ok(status);
    }

    @GetMapping("/generating/{jobId}")
    public String generatingPage(@PathVariable Long jobId, Model model) {
        model.addAttribute("jobId", jobId);
        return "quiz/generating";
    }

    @GetMapping("/take/{quizId}")
    public String takeQuiz(@PathVariable Long quizId,
                           @AuthenticationPrincipal UserDetails userDetails,
                           Model model) {
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new com.quizgen.common.ResourceNotFoundException(
                        com.quizgen.common.ErrorCodes.AUTH_001, "User not found"));

        // Teachers cannot take quizzes
        if (user.getRole() == UserRole.TEACHER) {
            return "redirect:/teacher/dashboard?info=Teachers+cannot+take+quizzes";
        }

        QuizDto quiz = quizService.getQuizById(quizId);
        AttemptDto attempt = attemptService.startAttempt(quizId, user.getId());
        model.addAttribute("quiz", quiz);
        model.addAttribute("attempt", attempt);
        return "quiz/take";
    }

    @PostMapping("/submit/{attemptId}")
    public String submitQuiz(@PathVariable Long attemptId,
                             @RequestParam Map<String, String> params,
                             @AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new com.quizgen.common.ResourceNotFoundException(
                        com.quizgen.common.ErrorCodes.AUTH_001, "User not found"));

        attemptService.submitAttempt(attemptId, params, user.getId());
        // Transaction committed — trigger async AI feedback for wrong MC/TF answers
        aiFeedbackService.generateFeedbackForAttempt(attemptId);
        return "redirect:/quiz/complete/" + attemptId;
    }

    @GetMapping("/complete/{attemptId}")
    public String completePage(@PathVariable Long attemptId, Model model) {
        AttemptDto attempt = attemptService.getAttempt(attemptId);
        model.addAttribute("attempt", attempt);
        return "quiz/complete";
    }
}
