package com.quizgen.ai;

import com.quizgen.attempt.Answer;
import com.quizgen.attempt.AnswerRepository;
import com.quizgen.quiz.Question;
import com.quizgen.quiz.QuestionOption;
import com.quizgen.quiz.QuestionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AIFeedbackService {

    private static final Logger log = LoggerFactory.getLogger(AIFeedbackService.class);

    private final AnswerRepository answerRepository;
    private final GeminiClient geminiClient;

    public AIFeedbackService(AnswerRepository answerRepository, GeminiClient geminiClient) {
        this.answerRepository = answerRepository;
        this.geminiClient = geminiClient;
    }

    @Transactional
    public void generateFeedbackForAttempt(Long attemptId) {
        List<Answer> answers = answerRepository.findByAttemptId(attemptId);

        for (Answer answer : answers) {
            Question question = answer.getQuestion();
            if (question == null) continue;

            QuestionType type = question.getQuestionType();
            if ((type == QuestionType.MULTIPLE_CHOICE || type == QuestionType.TRUE_FALSE)
                    && Boolean.FALSE.equals(answer.isCorrect())) {
                try {
                    String prompt = buildPrompt(question, answer.getAnswerText(), type);
                    String feedback = geminiClient.generateText(prompt);
                    answer.setAiFeedback(feedback);
                    answerRepository.save(answer);
                    log.info("Generated AI feedback for answer {} (attempt {})", answer.getId(), attemptId);
                } catch (Exception e) {
                    log.warn("Could not generate AI feedback for answer {} in attempt {}: {}",
                            answer.getId(), attemptId, e.getMessage());
                }
            }
        }
    }

    private String buildPrompt(Question question, String studentAnswer, QuestionType type) {
        StringBuilder sb = new StringBuilder();
        sb.append("A student answered a quiz question incorrectly.\n\n");
        sb.append("Question: ").append(question.getQuestionText()).append("\n");

        if (type == QuestionType.MULTIPLE_CHOICE) {
            List<QuestionOption> options = question.getOptions();
            String[] labels = {"A", "B", "C", "D"};
            sb.append("Options:\n");
            for (int i = 0; i < options.size() && i < 4; i++) {
                sb.append("  ").append(labels[i]).append(") ").append(options.get(i).getOptionText()).append("\n");
            }
            sb.append("Correct answer: Option ").append(question.getCorrectAnswer()).append("\n");
            sb.append("Student answered: Option ").append(studentAnswer).append("\n");
        } else {
            sb.append("Correct answer: ").append(question.getCorrectAnswer()).append("\n");
            sb.append("Student answered: ").append(studentAnswer).append("\n");
        }

        sb.append("\nIn 2-3 sentences, explain why the student's answer is wrong and what makes the correct answer right. Be educational and encouraging.");
        return sb.toString();
    }
}
