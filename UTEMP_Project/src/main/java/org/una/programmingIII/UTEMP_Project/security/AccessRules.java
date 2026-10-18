package org.una.programmingIII.UTEMP_Project.security;

import org.springframework.stereotype.Component;
import org.una.programmingIII.UTEMP_Project.models.UserRole;
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

    public AccessRules(NotificationRepository notificationRepository, SubmissionRepository submissionRepository) {
        this.notificationRepository = notificationRepository;
        this.submissionRepository = submissionRepository;
    }

    public boolean ownsNotification(Long notificationId, CustomUserDetails principal) {
        return notificationRepository.isOwner(notificationId, userId(principal));
    }

    /** The student who handed it in, the teacher of the course, or an admin. */
    public boolean canViewSubmission(Long submissionId, CustomUserDetails principal) {
        return isAdmin(principal)
                || submissionRepository.isOwner(submissionId, userId(principal))
                || submissionRepository.isCourseTeacher(submissionId, userId(principal));
    }

    public boolean isAdmin(CustomUserDetails principal) {
        return principal.getUser().getRole() == UserRole.ADMIN;
    }

    private static Long userId(CustomUserDetails principal) {
        return principal.getUser().getId();
    }
}
