package org.una.programmingIII.UTEMP_Project.exceptions;

public class RelationConflictException extends DomainException {
    public RelationConflictException(String message) {
        super("Relation conflict: " + message);
    }
}