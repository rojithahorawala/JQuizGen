package com.quizgen.attempt;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AttemptRepository extends JpaRepository<Attempt, Long> {

    @Query("SELECT a FROM Attempt a JOIN FETCH a.quiz JOIN FETCH a.student WHERE a.student.id = :studentId ORDER BY a.startedAt DESC")
    List<Attempt> findByStudentIdOrderByStartedAtDesc(@Param("studentId") Long studentId);

    @Query("SELECT a FROM Attempt a JOIN FETCH a.quiz JOIN FETCH a.student WHERE a.quiz.id = :quizId ORDER BY a.startedAt DESC")
    List<Attempt> findByQuizIdOrderByStartedAtDesc(@Param("quizId") Long quizId);

    @Query("SELECT a FROM Attempt a JOIN FETCH a.quiz JOIN FETCH a.student WHERE a.status = com.quizgen.attempt.AttemptStatus.SUBMITTED ORDER BY a.submittedAt ASC")
    List<Attempt> findSubmittedAttempts();

    @Query("SELECT COUNT(a) FROM Attempt a WHERE a.student.id = :studentId AND a.quiz.id = :quizId")
    long countByStudentIdAndQuizId(@Param("studentId") Long studentId, @Param("quizId") Long quizId);
}
