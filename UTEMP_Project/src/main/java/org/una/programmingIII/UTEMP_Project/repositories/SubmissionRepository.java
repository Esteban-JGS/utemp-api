package org.una.programmingIII.UTEMP_Project.repositories;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.una.programmingIII.UTEMP_Project.models.Submission;

public interface SubmissionRepository extends JpaRepository<Submission, Long> {
    @Query("SELECT s FROM Submission s WHERE s.assignment.id = :assignmentId")
    Page<Submission> findByAssignmentId(@Param("assignmentId") Long assignmentId, Pageable pageable);

    @Query("select count(s) > 0 from Submission s where s.id = :submissionId and s.student.id = :userId")
    boolean isOwner(@Param("submissionId") Long submissionId, @Param("userId") Long userId);

    @Query("select count(s) > 0 from Submission s where s.id = :submissionId and s.assignment.course.teacher.id = :userId")
    boolean isCourseTeacher(@Param("submissionId") Long submissionId, @Param("userId") Long userId);
}
