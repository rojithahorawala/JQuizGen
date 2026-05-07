package com.quizgen.quiz;

import com.quizgen.ai.QuizGenerationService;
import com.quizgen.attempt.AttemptService;
import com.quizgen.common.AttemptDto;
import com.quizgen.common.GenerationJobDto;
import com.quizgen.common.QuizDto;
import com.quizgen.grading.GradingService;
import com.quizgen.result.ResultService;
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
@RequestMapping("/teacher")
public class TeacherController {

    private static final Logger log = LoggerFactory.getLogger(TeacherController.class);

    private final QuizGenerationService quizGenerationService;
    private final QuizService quizService;
    private final AttemptService attemptService;
    private final GradingService gradingService;
    private final ResultService resultService;
    private final UserRepository userRepository;

    public TeacherController(QuizGenerationService quizGenerationService,
                             QuizService quizService,
                             AttemptService attemptService,
                             GradingService gradingService,
                             ResultService resultService,
                             UserRepository userRepository) {
        this.quizGenerationService = quizGenerationService;
        this.quizService = quizService;
        this.attemptService = attemptService;
        this.gradingService = gradingService;
        this.resultService = resultService;
        this.userRepository = userRepository;
    }

    @GetMapping("/dashboard")
    public String dashboard(@AuthenticationPrincipal UserDetails userDetails,
                            @RequestParam(required = false) String info,
                            Model model) {
        User user = userRepository.findByEmail(userDetails.getUsername()).orElseThrow();
        List<QuizDto> quizzes = quizService.getQuizzesByTeacher(user.getId());
        List<AttemptDto> pendingGrading = attemptService.getSubmittedAttemptsWithPendingGrading();

        model.addAttribute("quizzes", quizzes);
        model.addAttribute("pendingGradingCount", pendingGrading.size());
        model.addAttribute("user", user);
        if (info != null) {
            model.addAttribute("info", info);
        }
        return "teacher/dashboard";
    }

    @GetMapping("/upload")
    public String uploadPage() {
        return "teacher/upload";
    }

    @PostMapping("/upload")
    public String upload(@RequestParam("files") List<MultipartFile> files,
                         @RequestParam(value = "questionCount", defaultValue = "15") int questionCount,
                         @RequestParam(value = "questionTypes", required = false) List<String> questionTypes,
                         @AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepository.findByEmail(userDetails.getUsername()).orElseThrow();
        GenerationJobDto job = quizGenerationService.generateQuizAsync(files, questionCount, user.getId(), "UNIVERSAL", questionTypes);
        return "redirect:/quiz/generating/" + job.jobId();
    }

    @GetMapping("/grade")
    public String gradePage(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        List<AttemptDto> pendingAttempts = attemptService.getSubmittedAttemptsWithPendingGrading();
        model.addAttribute("pendingAttempts", pendingAttempts);
        return "teacher/grade";
    }

    @PostMapping("/grade/{attemptId}/question/{questionId}")
    public String submitGrade(@PathVariable Long attemptId,
                              @PathVariable Long questionId,
                              @RequestParam int pointsAwarded) {
        gradingService.submitManualGrade(attemptId, questionId, pointsAwarded);
        return "redirect:/teacher/grade";
    }

    @GetMapping("/quiz/{quizId}")
    public String viewQuiz(@PathVariable Long quizId, Model model) {
        QuizDto quiz = quizService.getQuizById(quizId);
        model.addAttribute("quiz", quiz);
        return "teacher/quiz-detail";
    }

    @GetMapping("/results/{quizId}")
    public String results(@PathVariable Long quizId, Model model) {
        List<AttemptDto> results = resultService.getResultsForQuiz(quizId);
        QuizDto quiz = quizService.getQuizById(quizId);
        model.addAttribute("quiz", quiz);
        model.addAttribute("results", results);
        return "teacher/results";
    }
}
