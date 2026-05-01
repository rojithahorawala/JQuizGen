package com.quizgen.quiz;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QuizRepository extends JpaRepository<Quiz, Long> {

    List<Quiz> findByCreatedByIdOrderByCreatedAtDesc(Long userId);

    @Query("SELECT q FROM Quiz q WHERE q.scope = com.quizgen.quiz.QuizScope.UNIVERSAL AND q.status IN (com.quizgen.quiz.QuizStatus.READY, com.quizgen.quiz.QuizStatus.ACTIVE) ORDER BY q.createdAt DESC")
    List<Quiz> findAvailableUniversalQuizzes();

    @Query("SELECT q FROM Quiz q WHERE q.createdBy.id = :userId AND q.scope = com.quizgen.quiz.QuizScope.PERSONAL ORDER BY q.createdAt DESC")
    List<Quiz> findPersonalQuizzesByStudent(@Param("userId") Long userId);
}
