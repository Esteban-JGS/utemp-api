package org.una.programmingIII.UTEMP_Project.exceptions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.una.programmingIII.UTEMP_Project.controllers.responses.ApiResponse;

import java.util.stream.Collectors;

/**
 * Single place where exceptions become HTTP responses, so controllers don't need try/catch blocks.
 * Every error body has the same shape: {@code {"statusCode": ..., "message": ..., "data": null}}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler({ResourceNotFoundException.class, UserNotFoundException.class, FileNotFoundDataBaseException.class})
    public ResponseEntity<ApiResponse<Void>> handleNotFound(RuntimeException e) {
        logger.warn("Resource not found: {}", e.getMessage());
        return build(HttpStatus.NOT_FOUND, "Resource not found");
    }

    @ExceptionHandler(ResourceAlreadyExistsException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceAlreadyExistsException(ResourceAlreadyExistsException e) {
        logger.warn("Resource already exists: {}", e.getMessage());
        return build(HttpStatus.CONFLICT, "Resource already exists");
    }

    @ExceptionHandler({InvalidDataException.class, IllegalArgumentException.class,
            HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(Exception e) {
        logger.warn("Invalid request: {}", e.getMessage());
        return build(HttpStatus.BAD_REQUEST, "Invalid data");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException e) {
        String details = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        logger.warn("Validation failed: {}", details);
        return build(HttpStatus.BAD_REQUEST, "Invalid data: " + details);
    }

    @ExceptionHandler(RelationConflictException.class)
    public ResponseEntity<ApiResponse<Void>> handleRelationConflictException(RelationConflictException e) {
        logger.warn("Relation conflict: {}", e.getMessage());
        return build(HttpStatus.CONFLICT, "Relation conflict");
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthenticationException(AuthenticationException e) {
        logger.warn("Authentication failed: {}", e.getMessage());
        return build(HttpStatus.UNAUTHORIZED, "Invalid credentials");
    }

    @ExceptionHandler(TokenExpiredException.class)
    public ResponseEntity<ApiResponse<Void>> handleTokenExpiredException(TokenExpiredException e) {
        logger.warn("Token expired: {}", e.getMessage());
        return build(HttpStatus.UNAUTHORIZED, "Token expired");
    }

    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidTokenException(InvalidTokenException e) {
        logger.warn("Invalid token: {}", e.getMessage());
        return build(HttpStatus.UNAUTHORIZED, "Invalid token");
    }

    // Thrown by @PreAuthorize and by ownership checks; without this handler it would fall into the generic 500
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(AccessDeniedException e) {
        logger.warn("Access denied: {}", e.getMessage());
        return build(HttpStatus.FORBIDDEN, "Access denied");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception e) {
        // Spring MVC exceptions (unknown route, wrong HTTP method, ...) already carry the right status
        if (e instanceof ErrorResponse errorResponse) {
            HttpStatusCode status = errorResponse.getStatusCode();
            logger.warn("Request error {}: {}", status.value(), e.getMessage());
            return ResponseEntity.status(status).body(new ApiResponse<>(status.value(), errorResponse.getBody().getTitle()));
        }
        logger.error("An unexpected error occurred: {}", e.getMessage(), e);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    }

    private ResponseEntity<ApiResponse<Void>> build(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ApiResponse<>(status.value(), message));
    }
}
