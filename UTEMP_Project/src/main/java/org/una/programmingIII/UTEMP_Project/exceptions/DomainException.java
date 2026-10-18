package org.una.programmingIII.UTEMP_Project.exceptions;

/**
 * Base class for business errors that map to a specific HTTP status in {@link GlobalExceptionHandler}
 * (404, 400, 409...). Services let these propagate unchanged instead of wrapping them in a generic error.
 */
public abstract class DomainException extends RuntimeException {

    protected DomainException(String message) {
        super(message);
    }
}
