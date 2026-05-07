package com.quizgen.ai;

import com.quizgen.common.GenerationJobDto;
import com.quizgen.common.GenerationStatusDto;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface QuizGenerationService {
    GenerationJobDto generateQuizAsync(List<MultipartFile> files, int questionCount, Long userId, String scope,
                                       List<String> questionTypes);
    GenerationStatusDto getJobStatus(Long jobId);
}
