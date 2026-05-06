package com.quizgen.file;

import com.quizgen.common.FileProcessingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TikaFileTextExtractorTest {

    private TikaFileTextExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new TikaFileTextExtractor();
    }

    @Test
    void extractsTextFromPlainTextFile() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "notes.txt", "text/plain",
                "Hello World study content".getBytes()
        );

        String result = extractor.extractText(file);

        assertThat(result).contains("Hello World study content");
    }

    @Test
    void extractsMarkdownFile() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "notes.md", "text/markdown",
                "# Heading\nSome body content here".getBytes()
        );

        String result = extractor.extractText(file);

        assertThat(result).isNotBlank();
    }

    @Test
    void throwsOnUnsupportedExtension() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.png", "image/png", new byte[]{1, 2, 3}
        );

        assertThatThrownBy(() -> extractor.extractText(file))
                .isInstanceOf(FileProcessingException.class)
                .hasMessageContaining("Unsupported file type");
    }

    @Test
    void throwsOnExecutableExtension() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "malware.exe", "application/octet-stream", new byte[]{1, 2, 3}
        );

        assertThatThrownBy(() -> extractor.extractText(file))
                .isInstanceOf(FileProcessingException.class);
    }

    @Test
    void throwsWhenFilenameIsNull() {
        MockMultipartFile file = new MockMultipartFile(
                "file", null, "text/plain", "content".getBytes()
        );

        assertThatThrownBy(() -> extractor.extractText(file))
                .isInstanceOf(FileProcessingException.class);
    }

    @Test
    void throwsWhenFilenameIsEmpty() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "", "text/plain", "content".getBytes()
        );

        assertThatThrownBy(() -> extractor.extractText(file))
                .isInstanceOf(FileProcessingException.class);
    }
}
