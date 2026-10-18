package org.una.programmingIII.UTEMP_Project.repositories;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.una.programmingIII.UTEMP_Project.models.Course;
import org.una.programmingIII.UTEMP_Project.models.Enrollment;
import org.una.programmingIII.UTEMP_Project.models.User;

import java.util.Optional;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {
    Optional<Enrollment> findByStudentAndCourse(User user, Course course);

    @Query("SELECT e FROM Enrollment e WHERE e.course.id = :courseId")
    Page<Enrollment> findByCourseId(@Param("courseId") Long courseId, Pageable pageable);

    @Query("SELECT e FROM Enrollment e WHERE e.student.id = :studentId")
    Page<Enrollment> findByStudentId(@Param("studentId") Long studentId, Pageable pageable);

    @Query("select count(e) > 0 from Enrollment e where e.course.id = :courseId and e.student.id = :studentId")
    boolean isEnrolled(@Param("courseId") Long courseId, @Param("studentId") Long studentId);

    @Query("""
            select count(e) > 0 from Enrollment e, Assignment a
            where a.id = :assignmentId and e.course = a.course and e.student.id = :studentId
            and e.state = org.una.programmingIII.UTEMP_Project.models.EnrollmentState.ENROLLED
            """)
    boolean isEnrolledInCourseOfAssignment(@Param("assignmentId") Long assignmentId, @Param("studentId") Long studentId);
}
