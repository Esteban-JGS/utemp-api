package org.una.programmingIII.UTEMP_Project.models;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@ExtendWith(MockitoExtension.class)
public class CourseTest {

    @InjectMocks
    private Course course;

    private Validator validator;

    @Mock
    private User teacher;

    @Mock
    private Department department;

    @BeforeEach
    public void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
        course = Course.builder()
                .name("Test Course")
                .description("This is a test course.")
                .teacher(teacher)
                .department(department)
                .state(CourseState.ACTIVE)
                .createdAt(LocalDateTime.now())
                .lastUpdate(LocalDateTime.now())
                .build();
        course.onCreate(); // What @PrePersist does when the course is saved
    }

    @Test
    void testCreateCourse() {
        assertNotNull(course);
        assertEquals("Test Course", course.getName());
        assertEquals("This is a test course.", course.getDescription());
        assertNotNull(course.getCreatedAt());
        assertNotNull(course.getLastUpdate());
        assertEquals(CourseState.ACTIVE, course.getState());
        assertTrue(course.getAssignments().isEmpty());
        assertTrue(course.getEnrollments().isEmpty());
    }

    @Test
    void testPrePersist() {
        assertNotNull(course.getCreatedAt());
        assertNotNull(course.getLastUpdate());
    }

    @Test
    void testPreUpdate() {
        // An older value instead of waiting, so the test does not depend on the clock resolution
        LocalDateTime previousUpdate = LocalDateTime.now().minusDays(1);
        course.setLastUpdate(previousUpdate);

        course.onUpdate();
        assertTrue(course.getLastUpdate().isAfter(previousUpdate), "lastUpdate should be updated on update call");
    }


    @Test
    void testValidationNameNotNull() {
        course.setName(null);
        Set<ConstraintViolation<Course>> violations = validator.validate(course);
        assertEquals(1, violations.size());
        assertEquals("Course name must not be null", violations.iterator().next().getMessage());
    }

    @Test
    void testValidationDescriptionNotNull() {
        course.setDescription(null);
        Set<ConstraintViolation<Course>> violations = validator.validate(course);
        assertEquals(1, violations.size());
        assertEquals("Description must not be null", violations.iterator().next().getMessage());
    }

    @Test
    void testValidationTeacherNotNull() {
        course.setTeacher(null);
        Set<ConstraintViolation<Course>> violations = validator.validate(course);
        assertEquals(1, violations.size());
        assertEquals("Teacher must not be null", violations.iterator().next().getMessage());
    }

    @Test
    void testValidationDepartmentNotNull() {
        course.setDepartment(null);
        Set<ConstraintViolation<Course>> violations = validator.validate(course);
        assertEquals(1, violations.size());
        assertEquals("Department must not be null", violations.iterator().next().getMessage());
    }

    @Test
    void testAddAssignment() {
        Assignment assignment = new Assignment();
        assignment.setCourse(course);
        course.getAssignments().add(assignment);
        assertEquals(1, course.getAssignments().size());
        assertTrue(course.getAssignments().contains(assignment));
    }

    @Test
    void testAddEnrollment() {
        Enrollment enrollment = new Enrollment();
        enrollment.setCourse(course);
        course.getEnrollments().add(enrollment);
        assertEquals(1, course.getEnrollments().size());
        assertTrue(course.getEnrollments().contains(enrollment));
    }

    @Test
    void testStateChange() {
        course.setState(CourseState.INACTIVE);
        assertEquals(CourseState.INACTIVE, course.getState());
    }

    @Test
    void testUpdateFields() {
        String newName = "Updated Course Name";
        String newDescription = "Updated Course Description";
        course.setName(newName);
        course.setDescription(newDescription);
        course.onUpdate(); // What @PreUpdate does
        assertEquals(newName, course.getName());
        assertEquals(newDescription, course.getDescription());
        assertNotNull(course.getLastUpdate());
    }

    @Test
    void testBuilder() {
        Course builtCourse = Course.builder()
                .name("Built Course")
                .description("Description for built course")
                .teacher(teacher)
                .department(department)
                .state(CourseState.ACTIVE)
                .build();

        assertNotNull(builtCourse);
        assertEquals("Built Course", builtCourse.getName());
        assertEquals("Description for built course", builtCourse.getDescription());
        assertEquals(CourseState.ACTIVE, builtCourse.getState());
    }

    @Test
    void testRequiredFieldsAreValidated() {
        // Required fields are enforced by Bean Validation (and NOT NULL columns), not by the builder
        Course invalid = Course.builder().name(null).description(null).teacher(null).department(null).state(null).build();
        Set<String> fields = new HashSet<>();
        validator.validate(invalid).forEach(v -> fields.add(v.getPropertyPath().toString()));
        assertTrue(fields.containsAll(Set.of("name", "description", "teacher", "department", "state")), "violations: " + fields);
    }


    @Test
    void testExtremeDescription() {
        String extremeDescription = "D".repeat(500); // The longest description allowed
        course.setDescription(extremeDescription);
        Set<ConstraintViolation<Course>> violations = validator.validate(course);
        assertTrue(violations.isEmpty(), "The description should have no violations");
    }

    @Test
    void testEmptyState() {
        course.setState(null);
        Set<ConstraintViolation<Course>> violations = validator.validate(course);
        assertEquals(1, violations.size());
        assertEquals("State must not be null", violations.iterator().next().getMessage());
    }
}
