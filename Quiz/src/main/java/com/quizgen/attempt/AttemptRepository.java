package com.quizgen.attempt;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AttemptRepository extends JpaRepository<Attempt, Long> {

    List<Attempt> findByStudentIdOrderByStartedAtDesc(Long studentId);

    List<Attempt> findByQuizIdOrderByStartedAtDesc(Long quizId);

    @Query("SELECT a FROM Attempt a WHERE a.status = com.quizgen.attempt.AttemptStatus.SUBMITTED ORDER BY a.submittedAt ASC")
    List<Attempt> findSubmittedAttempts();

    @Query("SELECT COUNT(a) FROM Attempt a WHERE a.student.id = :studentId AND a.quiz.id = :quizId")
    long countByStudentIdAndQuizId(@Param("studentId") Long studentId, @Param("quizId") Long quizId);
}
