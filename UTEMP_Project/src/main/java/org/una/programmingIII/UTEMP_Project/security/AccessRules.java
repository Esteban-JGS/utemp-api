package org.una.programmingIII.UTEMP_Project.security;

import org.springframework.stereotype.Component;
import org.una.programmingIII.UTEMP_Project.models.UserRole;
import org.una.programmingIII.UTEMP_Project.repositories.AssignmentRepository;
import org.una.programmingIII.UTEMP_Project.repositories.CourseRepository;
import org.una.programmingIII.UTEMP_Project.repositories.EnrollmentRepository;
import org.una.programmingIII.UTEMP_Project.repositories.NotificationRepository;
import org.una.programmingIII.UTEMP_Project.repositories.SubmissionRepository;
import org.una.programmingIII.UTEMP_Project.services.CustomUserDetails;

/**
 * Ownership rules used from {@code @PreAuthorize} as {@code @access}, for example
 * {@code @PreAuthorize("@access.ownsNotification(#id, principal)")}.
 * Permissions say what kind of action a user may do; these rules say on which records.
 */
@Component("access")
public class AccessRules {

    private final NotificationRepository notificationRepository;
    private final SubmissionRepository submissionRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final CourseRepository courseRepository;
    private final AssignmentRepository assignmentRepository;

    public AccessRules(NotificationRepository notificationRepository, SubmissionRepository submissionRepository,
                       EnrollmentRepository enrollmentRepository, CourseRepository courseRepository,
                       AssignmentRepository assignmentRepository) {
        this.notificationRepository = notificationRepository;
        this.submissionRepository = submissionRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.courseRepository = courseRepository;
        this.assignmentRepository = assignmentRepository;
    }

    public boolean ownsNotification(Long notificationId, CustomUserDetails principal) {
        return notificationRepository.isOwner(notificationId, userId(principal));
    }

    /** The teacher of the course or an admin. */
    public boolean canManageCourse(Long courseId, CustomUserDetails principal) {
        return isAdmin(principal) || courseRepository.isTeacher(courseId, userId(principal));
    }

    /** The teacher of the assignment's course or an admin. */
    public boolean canManageAssignment(Long assignmentId, CustomUserDetails principal) {
        return isAdmin(principal) || assignmentRepository.isCourseTeacher(assignmentId, userId(principal));
    }

    /** The teacher of the submission's course or an admin. */
    public boolean canManageSubmission(Long submissionId, CustomUserDetails principal) {
        return isAdmin(principal) || submissionRepository.isCourseTeacher(submissionId, userId(principal));
    }

    /** The teacher of the course, its students, or an admin. */
    public boolean canViewCourse(Long courseId, CustomUserDetails principal) {
        return canManageCourse(courseId, principal) || enrollmentRepository.isEnrolled(courseId, userId(principal));
    }

    /** The student who handed it in, the teacher of the course, or an admin. */
    public boolean canViewSubmission(Long submissionId, CustomUserDetails principal) {
        return canManageSubmission(submissionId, principal) || submissionRepository.isOwner(submissionId, userId(principal));
    }

    /** Students hand in work as themselves, and only for courses they are enrolled in. */
    public boolean canSubmit(Long assignmentId, Long studentId, CustomUserDetails principal) {
        return isAdmin(principal)
                || (userId(principal).equals(studentId)
                && enrollmentRepository.isEnrolledInCourseOfAssignment(assignmentId, studentId));
    }

    public boolean isAdmin(CustomUserDetails principal) {
        return principal.getUser().getRole() == UserRole.ADMIN;
    }

    private static Long userId(CustomUserDetails principal) {
        return principal.getUser().getId();
    }
}
