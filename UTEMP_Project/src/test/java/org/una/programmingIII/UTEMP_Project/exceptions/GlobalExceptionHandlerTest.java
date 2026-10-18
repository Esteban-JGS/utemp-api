package org.una.programmingIII.UTEMP_Project.exceptions;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.una.programmingIII.UTEMP_Project.controllers.responses.ApiResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void accessDeniedBecomesForbiddenNotServerError() {
        assertStatus(handler.handleAccessDeniedException(new AccessDeniedException("nope")), HttpStatus.FORBIDDEN);
    }

    @Test
    void badCredentialsBecomeUnauthorized() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleAuthenticationException(new BadCredentialsException("Bad credentials"));
        assertStatus(response, HttpStatus.UNAUTHORIZED);
        assertEquals("Invalid credentials", response.getBody().getMessage());
    }

    @Test
    void domainExceptionsMapToTheirStatus() {
        assertStatus(handler.handleNotFound(new ResourceNotFoundException("Course", 1L)), HttpStatus.NOT_FOUND);
        assertStatus(handler.handleNotFound(new UserNotFoundException("missing")), HttpStatus.NOT_FOUND);
        assertStatus(handler.handleBadRequest(new InvalidDataException("bad")), HttpStatus.BAD_REQUEST);
        assertStatus(handler.handleResourceAlreadyExistsException(new ResourceAlreadyExistsException("User", "dup")), HttpStatus.CONFLICT);
        assertStatus(handler.handleInvalidTokenException(new InvalidTokenException("bad")), HttpStatus.UNAUTHORIZED);
        assertStatus(handler.handleTokenExpiredException(new TokenExpiredException("old")), HttpStatus.UNAUTHORIZED);
    }

    @Test
    void springMvcErrorsKeepTheirOwnStatus() {
        assertStatus(handler.handleGenericException(new NoResourceFoundException(HttpMethod.GET, "missing")), HttpStatus.NOT_FOUND);
    }

    @Test
    void unexpectedErrorsDoNotLeakDetails() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleGenericException(new IllegalStateException("db password is hunter2"));
        assertStatus(response, HttpStatus.INTERNAL_SERVER_ERROR);
        assertEquals("An unexpected error occurred", response.getBody().getMessage());
    }

    private static void assertStatus(ResponseEntity<ApiResponse<Void>> response, HttpStatus expected) {
        assertEquals(expected, response.getStatusCode());
        assertEquals(expected.value(), response.getBody().getStatusCode());
    }
}
