package org.una.programmingIII.UTEMP_Project.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.una.programmingIII.UTEMP_Project.models.User;
import org.una.programmingIII.UTEMP_Project.models.UserPermission;
import org.una.programmingIII.UTEMP_Project.models.UserState;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CustomUserDetailsTest {

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setIdentificationNumber("000000000");
        testUser.setName("Test User");
        testUser.setEmail("testuser@example.com");
        testUser.setPassword("password");
        testUser.setState(UserState.ACTIVE);
    }

    @Test
    void constructor_UserNotNull() {
        CustomUserDetails customUserDetails = new CustomUserDetails(testUser);
        assertNotNull(customUserDetails);
    }

    @Test
    void getUsername() {
        CustomUserDetails customUserDetails = new CustomUserDetails(testUser);
        assertEquals(testUser.getIdentificationNumber(), customUserDetails.getUsername());
    }

    @Test
    void getAuthorities() {
        testUser.setPermissions(List.of(UserPermission.MANAGE_USERS, UserPermission.MANAGE_COURSES));
        CustomUserDetails customUserDetails = new CustomUserDetails(testUser);
        // Each permission becomes an authority with the same name, which is what @PreAuthorize checks
        assertEquals(List.of("MANAGE_USERS", "MANAGE_COURSES"),
                customUserDetails.getAuthorities().stream().map(Object::toString).toList());
    }

    @Test
    void isEnabled() {
        testUser.setState(UserState.ACTIVE);
        CustomUserDetails customUserDetails = new CustomUserDetails(testUser);
        assertTrue(customUserDetails.isEnabled());

        testUser.setState(UserState.INACTIVE);
        customUserDetails = new CustomUserDetails(testUser);
        assertFalse(customUserDetails.isEnabled());
    }

    @Test
    void isAccountNonLocked() {
        testUser.setState(UserState.ACTIVE);
        CustomUserDetails customUserDetails = new CustomUserDetails(testUser);
        assertTrue(customUserDetails.isAccountNonLocked());

        testUser.setState(UserState.SUSPENDED);
        customUserDetails = new CustomUserDetails(testUser);
        assertFalse(customUserDetails.isAccountNonLocked());
    }
}
