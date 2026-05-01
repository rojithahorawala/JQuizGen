package com.quizgen.ai;

import com.quizgen.common.ErrorCodes;
import com.quizgen.file.InMemoryMultipartFile;
import com.quizgen.file.FileTextExtractor;
import com.quizgen.quiz.*;
import com.quizgen.user.User;
import com.quizgen.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
public class QuizGenerationAsyncExecutor {

    private static final Logger log = LoggerFactory.getLogger(QuizGenerationAsyncExecutor.class);

    private final GenerationJobRepository jobRepository;
    private final QuizRepository quizRepository;
    private final UserRepository userRepository;
    private final FileTextExtractor fileTextExtractor;
    private final PromptBuilder promptBuilder;
    private final GeminiClient geminiClient;
    private final ResponseParser responseParser;

    public QuizGenerationAsyncExecutor(GenerationJobRepository jobRepository,
                                       QuizRepository quizRepository,
                                       UserRepository userRepository,
                                       FileTextExtractor fileTextExtractor,
                                       PromptBuilder promptBuilder,
                                       GeminiClient geminiClient,
                                       ResponseParser responseParser) {
        this.jobRepository = jobRepository;
        this.quizRepository = quizRepository;
        this.userRepository = userRepository;
        this.fileTextExtractor = fileTextExtractor;
        this.promptBuilder = promptBuilder;
        this.geminiClient = geminiClient;
        this.responseParser = responseParser;
    }

    @Async
    public void processAsync(Long jobId,
                             List<byte[]> fileContents,
                             List<String> fileNames,
                             List<String> contentTypes,
                             int questionCount,
                             Long userId,
                             String quizScope) {
        GenerationJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Job not found: " + jobId));

        try {
            job.setStatus(JobStatus.PROCESSING);
            jobRepository.save(job);

            // Extract text from each file
            StringBuilder combinedText = new StringBuilder();
            for (int i = 0; i < fileContents.size(); i++) {
                InMemoryMultipartFile multipartFile = new InMemoryMultipartFile(
                        fileNames.get(i),
                        fileNames.get(i),
                        contentTypes.get(i),
                        fileContents.get(i)
                );
                String text = fileTextExtractor.extractText(multipartFile);
                combinedText.append(text).append("\n\n");
                log.info("Extracted text from file {}: {} chars", fileNames.get(i), text.length());
            }

            // Build prompt and call Claude
            String prompt = promptBuilder.buildQuizPrompt(combinedText.toString(), questionCount);
            String aiResponse = geminiClient.generateContent(prompt);

            // Parse response
            List<ParsedQuestion> parsedQuestions = responseParser.parse(aiResponse);

            // Fetch user fresh inside async method
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found: " + userId));

            // Persist quiz
            Quiz quiz = saveQuizWithQuestions(parsedQuestions, user, quizScope, questionCount);

            // Update job to READY
            job.setStatus(JobStatus.READY);
            job.setQuiz(quiz);
            job.setCompletedAt(LocalDateTime.now());
            jobRepository.save(job);

            log.info("Quiz generation completed. Job={}, Quiz={}", jobId, quiz.getId());

        } catch (Exception e) {
            log.error("Quiz generation failed for job {}", jobId, e);
            job.setStatus(JobStatus.FAILED);
            job.setErrorCode(determineErrorCode(e));
            job.setCompletedAt(LocalDateTime.now());
            jobRepository.save(job);
        }
    }

    @Transactional
    protected Quiz saveQuizWithQuestions(List<ParsedQuestion> parsedQuestions, User user, String quizScope, int questionCount) {
        Quiz quiz = new Quiz();
        quiz.setTitle("Quiz - " + LocalDateTime.now().toLocalDate());
        quiz.setCreatedBy(user);
        quiz.setScope(com.quizgen.quiz.QuizScope.valueOf(quizScope));
        quiz.setStatus(QuizStatus.READY);
        quiz.setQuestionCount(parsedQuestions.size());
        quiz = quizRepository.save(quiz);

        List<Question> questions = new ArrayList<>();
        for (int i = 0; i < parsedQuestions.size(); i++) {
            ParsedQuestion pq = parsedQuestions.get(i);
            Question question = new Question();
            question.setQuiz(quiz);
            question.setQuestionText(pq.text());
            question.setOrderIndex(i);
            question.setPoints(pq.points());

            QuestionType type;
            try {
                type = QuestionType.valueOf(pq.type());
            } catch (IllegalArgumentException e) {
                type = QuestionType.FREE_RESPONSE;
            }
            question.setQuestionType(type);
            question.setCorrectAnswer(pq.correctAnswer());

            // Build options for MC
            List<QuestionOption> options = new ArrayList<>();
            if (type == QuestionType.MULTIPLE_CHOICE && pq.options() != null) {
                String[] labels = {"A", "B", "C", "D"};
                for (int j = 0; j < pq.options().size() && j < 4; j++) {
                    QuestionOption opt = new QuestionOption();
                    opt.setQuestion(question);
                    opt.setOptionText(pq.options().get(j));
                    opt.setCorrect(labels[j].equals(pq.correctAnswer()));
                    options.add(opt);
                }
            } else if (type == QuestionType.TRUE_FALSE) {
                QuestionOption trueOpt = new QuestionOption();
                trueOpt.setQuestion(question);
                trueOpt.setOptionText("True");
                trueOpt.setCorrect("TRUE".equalsIgnoreCase(pq.correctAnswer()));
                options.add(trueOpt);

                QuestionOption falseOpt = new QuestionOption();
                falseOpt.setQuestion(question);
                falseOpt.setOptionText("False");
                falseOpt.setCorrect("FALSE".equalsIgnoreCase(pq.correctAnswer()));
                options.add(falseOpt);
            }

            question.setOptions(options);
            questions.add(question);
        }

        quiz.setQuestions(questions);
        return quizRepository.save(quiz);
    }

    private String determineErrorCode(Exception e) {
        if (e instanceof com.quizgen.common.FileProcessingException fpe) {
            return fpe.getErrorCode();
        }
        if (e instanceof com.quizgen.common.AIServiceException ase) {
            return ase.getErrorCode();
        }
        return ErrorCodes.SYS_001;
    }
}
