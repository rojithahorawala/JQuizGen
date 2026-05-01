package com.quizgen.file;

import com.quizgen.common.ErrorCodes;
import com.quizgen.common.FileProcessingException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

@Component
public class TikaFileTextExtractor implements FileTextExtractor {

    private static final Logger log = LoggerFactory.getLogger(TikaFileTextExtractor.class);

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "pdf", "docx", "doc", "csv", "xls", "xlsx", "txt", "md"
    );

    @Override
    public String extractText(MultipartFile file) {
        String filename = file.getOriginalFilename();
        if (filename == null || filename.isEmpty()) {
            throw new FileProcessingException(ErrorCodes.FILE_001, "File has no name");
        }

        String extension = getExtension(filename);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new FileProcessingException(ErrorCodes.FILE_001,
                    "Unsupported file type: " + extension + ". Allowed: " + ALLOWED_EXTENSIONS);
        }

        try (InputStream is = file.getInputStream()) {
            AutoDetectParser parser = new AutoDetectParser();
            BodyContentHandler handler = new BodyContentHandler(-1);
            Metadata metadata = new Metadata();
            ParseContext context = new ParseContext();
            parser.parse(is, handler, metadata, context);
            String text = handler.toString().trim();
            log.info("Extracted {} characters from file: {}", text.length(), filename);
            return text;
        } catch (IOException | SAXException | TikaException e) {
            log.error("Failed to parse file: {}", filename, e);
            throw new FileProcessingException(ErrorCodes.FILE_004, "Failed to parse file: " + filename, e);
        }
    }

    private String getExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == filename.length() - 1) {
            return "";
        }
        return filename.substring(dotIndex + 1).toLowerCase();
    }
}
