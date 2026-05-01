package com.quizgen.file;

import org.springframework.web.multipart.MultipartFile;

public interface FileTextExtractor {
    String extractText(MultipartFile file);
}
