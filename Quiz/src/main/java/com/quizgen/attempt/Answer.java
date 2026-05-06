package com.quizgen.attempt;

import com.quizgen.quiz.Question;
import jakarta.persistence.*;

@Entity
@Table(name = "answers")
public class Answer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attempt_id", nullable = false)
    private Attempt attempt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id")
    private Question question;

    @Column(name = "answer_text", nullable = false, columnDefinition = "TEXT")
    private String answerText;

    @Column(name = "is_correct")
    private Boolean correct;

    @Column(name = "points_awarded")
    private Integer pointsAwarded;

    @Column(name = "ai_feedback", columnDefinition = "TEXT")
    private String aiFeedback;

    public Answer() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Attempt getAttempt() { return attempt; }
    public void setAttempt(Attempt attempt) { this.attempt = attempt; }

    public Question getQuestion() { return question; }
    public void setQuestion(Question question) { this.question = question; }

    public String getAnswerText() { return answerText; }
    public void setAnswerText(String answerText) { this.answerText = answerText; }

    public Boolean isCorrect() { return correct; }
    public void setCorrect(Boolean correct) { this.correct = correct; }

    public Integer getPointsAwarded() { return pointsAwarded; }
    public void setPointsAwarded(Integer pointsAwarded) { this.pointsAwarded = pointsAwarded; }

    public String getAiFeedback() { return aiFeedback; }
    public void setAiFeedback(String aiFeedback) { this.aiFeedback = aiFeedback; }
}
