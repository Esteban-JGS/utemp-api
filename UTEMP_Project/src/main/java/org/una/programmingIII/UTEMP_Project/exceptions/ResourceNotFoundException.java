package org.una.programmingIII.UTEMP_Project.exceptions;

public class ResourceNotFoundException extends DomainException {
    public ResourceNotFoundException(String resource, Long id) {
        super(resource + " not found with id: " + id);
    }
}