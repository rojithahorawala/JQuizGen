package com.quizgen.ai;

import com.quizgen.common.ErrorCodes;
import com.quizgen.common.FileProcessingException;
import com.quizgen.common.GenerationJobDto;
import com.quizgen.common.GenerationStatusDto;
import com.quizgen.common.ResourceNotFoundException;
import com.quizgen.user.User;
import com.quizgen.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class QuizGenerationServiceImpl implements QuizGenerationService {

    private static final Logger log = LoggerFactory.getLogger(QuizGenerationServiceImpl.class);

    private final GenerationJobRepository jobRepository;
    private final UserRepository userRepository;
    private final QuizGenerationAsyncExecutor asyncExecutor;

    public QuizGenerationServiceImpl(GenerationJobRepository jobRepository,
                                     UserRepository userRepository,
                                     QuizGenerationAsyncExecutor asyncExecutor) {
        this.jobRepository = jobRepository;
        this.userRepository = userRepository;
        this.asyncExecutor = asyncExecutor;
    }

    @Override
    @Transactional
    public GenerationJobDto generateQuizAsync(List<MultipartFile> files, int questionCount, Long userId, String scope) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCodes.QUIZ_001, "User not found: " + userId));

        // Read file bytes eagerly before async boundary
        List<byte[]> fileContents = new ArrayList<>();
        List<String> fileNames = new ArrayList<>();
        List<String> contentTypes = new ArrayList<>();

        for (MultipartFile file : files) {
            if (!file.isEmpty()) {
                try {
                    fileContents.add(file.getBytes());
                    fileNames.add(file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown");
                    contentTypes.add(file.getContentType() != null ? file.getContentType() : "application/octet-stream");
                } catch (IOException e) {
                    throw new FileProcessingException(ErrorCodes.FILE_002, "Failed to read file: " + file.getOriginalFilename(), e);
                }
            }
        }

        GenerationJob job = new GenerationJob();
        job.setUser(user);
        job.setStatus(JobStatus.PENDING);
        job = jobRepository.save(job);

        log.info("Created generation job {} for user {} with {} files", job.getId(), userId, fileContents.size());

        asyncExecutor.processAsync(job.getId(), fileContents, fileNames, contentTypes, questionCount, userId, scope);

        return new GenerationJobDto(job.getId());
    }

    @Override
    public GenerationStatusDto getJobStatus(Long jobId) {
        GenerationJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCodes.QUIZ_001, "Job not found: " + jobId));

        Long quizId = job.getQuiz() != null ? job.getQuiz().getId() : null;
        return new GenerationStatusDto(job.getStatus().name(), quizId, job.getErrorCode());
    }
}
