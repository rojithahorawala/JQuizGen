package com.quizgen.attempt;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AnswerRepository extends JpaRepository<Answer, Long> {

    List<Answer> findByAttemptId(Long attemptId);

    Optional<Answer> findFirstByAttemptIdAndQuestionId(Long attemptId, Long questionId);

    @Query("SELECT a FROM Answer a WHERE a.attempt.id = :attemptId AND a.question.questionType = com.quizgen.quiz.QuestionType.FREE_RESPONSE AND a.pointsAwarded IS NULL")
    List<Answer> findUngradedFreeResponseByAttemptId(@Param("attemptId") Long attemptId);
}
