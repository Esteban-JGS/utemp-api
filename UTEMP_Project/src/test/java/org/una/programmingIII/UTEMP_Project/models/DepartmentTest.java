package org.una.programmingIII.UTEMP_Project.models;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DepartmentTest {

    @InjectMocks
    private Department department;

    @Mock
    private Faculty faculty;

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
        department = new Department();
        department.setFaculty(faculty);
        department.setCreatedAt(LocalDateTime.now());
        department.setLastUpdate(LocalDateTime.now());
    }

    @Test
    void testValidDepartment() {
        department.setName("Computer Science");
        department.setFaculty(faculty);

        Set<ConstraintViolation<Department>> violations = validator.validate(department);
        assertTrue(violations.isEmpty(), "No violations should occur for a valid department");
    }

    @Test
    void testNullName() {
        department.setName(null);
        department.setFaculty(faculty);

        Set<ConstraintViolation<Department>> violations = validator.validate(department);
        assertEquals(1, violations.size());
        assertEquals("Department name must not be null", violations.iterator().next().getMessage());
    }

    @Test
    void testNullFaculty() {
        department.setName("Computer Science");
        department.setFaculty(null);

        Set<ConstraintViolation<Department>> violations = validator.validate(department);
        assertEquals(1, violations.size());
        assertEquals("Faculty must not be null", violations.iterator().next().getMessage());
    }

    @Test
    void testPrePersist() {
        department.onCreate();
        assertNotNull(department.getCreatedAt(), "Created at timestamp should not be null");
        assertNotNull(department.getLastUpdate(), "Last update timestamp should not be null");
    }

    @Test
    void testPreUpdate() {
        department.onCreate();
        // Start from an older timestamp so the check does not depend on the clock resolution
        department.setLastUpdate(LocalDateTime.now().minusMinutes(1));
        LocalDateTime previousUpdate = department.getLastUpdate();
        department.onUpdate();
        assertNotEquals(previousUpdate, department.getLastUpdate(), "lastUpdate should be updated on update call");
    }

    @Test
    void testAddCourse() {
        Course course = mock(Course.class);
        department.getCourses().add(course);
        assertEquals(1, department.getCourses().size(), "Course list should contain the added course");
    }

    @Test
    void testEmptyCourses() {
        department.setCourses(new ArrayList<>());
        assertTrue(department.getCourses().isEmpty(), "Courses list should be empty initially");
    }

    @Test
    void testNonNullCourses() {
        List<Course> courses = department.getCourses();
        assertNotNull(courses, "Courses list should not be null after instantiation");
    }

}
