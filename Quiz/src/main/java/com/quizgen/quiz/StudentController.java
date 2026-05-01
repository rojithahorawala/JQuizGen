package com.quizgen.quiz;

import com.quizgen.ai.QuizGenerationService;
import com.quizgen.attempt.AttemptService;
import com.quizgen.common.AttemptDto;
import com.quizgen.common.GenerationJobDto;
import com.quizgen.common.QuizDto;
import com.quizgen.user.User;
import com.quizgen.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Controller
@RequestMapping("/student")
public class StudentController {

    private static final Logger log = LoggerFactory.getLogger(StudentController.class);

    private final QuizGenerationService quizGenerationService;
    private final QuizService quizService;
    private final AttemptService attemptService;
    private final UserRepository userRepository;

    public StudentController(QuizGenerationService quizGenerationService,
                             QuizService quizService,
                             AttemptService attemptService,
                             UserRepository userRepository) {
        this.quizGenerationService = quizGenerationService;
        this.quizService = quizService;
        this.attemptService = attemptService;
        this.userRepository = userRepository;
    }

    @GetMapping("/dashboard")
    public String dashboard(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        User user = userRepository.findByEmail(userDetails.getUsername()).orElseThrow();
        List<QuizDto> availableQuizzes = quizService.getAvailableQuizzesForStudent(user.getId());
        List<QuizDto> personalQuizzes = quizService.getPersonalQuizzesByStudent(user.getId());
        List<AttemptDto> history = attemptService.getStudentAttempts(user.getId());

        model.addAttribute("availableQuizzes", availableQuizzes);
        model.addAttribute("personalQuizzes", personalQuizzes);
        model.addAttribute("history", history);
        model.addAttribute("user", user);
        return "student/dashboard";
    }

    @GetMapping("/attempts/{attemptId}")
    public String attemptDetail(@PathVariable Long attemptId,
                                @AuthenticationPrincipal UserDetails userDetails,
                                Model model) {
        User user = userRepository.findByEmail(userDetails.getUsername()).orElseThrow();
        AttemptDto attempt = attemptService.getAttempt(attemptId);
        if (!attempt.studentUsername().equals(user.getUsername())) {
            return "redirect:/student/dashboard";
        }
        model.addAttribute("attempt", attempt);
        return "student/attempt-detail";
    }

    @GetMapping("/upload")
    public String uploadPage() {
        return "student/upload";
    }

    @PostMapping("/upload")
    public String upload(@RequestParam("files") List<MultipartFile> files,
                         @RequestParam(value = "questionCount", defaultValue = "15") int questionCount,
                         @AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepository.findByEmail(userDetails.getUsername()).orElseThrow();
        GenerationJobDto job = quizGenerationService.generateQuizAsync(files, questionCount, user.getId(), "PERSONAL");
        return "redirect:/quiz/generating/" + job.jobId();
    }
}
