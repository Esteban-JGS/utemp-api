package org.una.programmingIII.UTEMP_Project.exceptions;

public class InvalidDataException extends DomainException {
    public InvalidDataException(String message) {
        super("Invalid data: " + message);
    }
}
