package org.una.programmingIII.UTEMP_Project.security;

import org.springframework.stereotype.Component;
import org.una.programmingIII.UTEMP_Project.repositories.NotificationRepository;
import org.una.programmingIII.UTEMP_Project.services.CustomUserDetails;

/**
 * Ownership rules used from {@code @PreAuthorize} as {@code @access}, for example
 * {@code @PreAuthorize("@access.ownsNotification(#id, principal)")}.
 * Permissions say what kind of action a user may do; these rules say on which records.
 */
@Component("access")
public class AccessRules {

    private final NotificationRepository notificationRepository;

    public AccessRules(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    public boolean ownsNotification(Long notificationId, CustomUserDetails principal) {
        return notificationRepository.isOwner(notificationId, userId(principal));
    }

    private static Long userId(CustomUserDetails principal) {
        return principal.getUser().getId();
    }
}
