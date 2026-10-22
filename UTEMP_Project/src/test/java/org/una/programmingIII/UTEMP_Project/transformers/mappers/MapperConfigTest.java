package org.una.programmingIII.UTEMP_Project.transformers.mappers;

import org.junit.jupiter.api.Test;
import org.modelmapper.ModelMapper;
import org.una.programmingIII.UTEMP_Project.dtos.*;
import org.una.programmingIII.UTEMP_Project.models.*;

import java.time.Instant;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Uses the application's ModelMapper configuration on every entity/DTO pair the services convert.
 */
class MapperConfigTest {

    private static final LocalDateTime CREATED = LocalDateTime.of(2026, 3, 1, 10, 30);

    private final ModelMapper modelMapper = new MapperConfig().modelMapper();

    @Test
    void timestampsSurviveTheConversionToDto() {
        User user = new User();
        user.setCreatedAt(CREATED);
        user.setLastUpdate(CREATED);
        Assignment assignment = new Assignment();
        assignment.setDeadline(Instant.parse("2026-12-01T23:59:00Z"));

        UserDTO userDTO = modelMapper.map(user, UserDTO.class);
        AssignmentDTO assignmentDTO = modelMapper.map(assignment, AssignmentDTO.class);

        assertEquals(CREATED, userDTO.getCreatedAt());
        assertEquals(CREATED, userDTO.getLastUpdate());
        assertEquals(Instant.parse("2026-12-01T23:59:00Z"), assignmentDTO.getDeadline());
    }

    @Test
    void submissionFlatFieldsNeverOverrideTheNestedOnes() {
        SubmissionDTO dto = SubmissionDTO.builder()
                .assignment(AssignmentDTO.builder().id(5L).build())
                .student(UserDTO.builder().id(3L).build())
                .assignmentId(99L)
                .studentId(98L)
                .studentName("Someone else")
                .build();

        Submission submission = modelMapper.map(dto, Submission.class);

        assertEquals(5L, submission.getAssignment().getId());
        assertEquals(3L, submission.getStudent().getId());
        assertNull(submission.getStudent().getName());
    }

    @Test
    void referencedIdsSurviveTheConversionToEntity() {
        Enrollment enrollment = modelMapper.map(EnrollmentDTO.builder()
                .course(CourseDTO.builder().id(1L).build())
                .student(UserDTO.builder().id(3L).build())
                .build(), Enrollment.class);
        assertEquals(1L, enrollment.getCourse().getId());
        assertEquals(3L, enrollment.getStudent().getId());

        Course course = modelMapper.map(CourseDTO.builder()
                .name("Course")
                .teacher(UserDTO.builder().id(2L).build())
                .department(DepartmentDTO.builder().id(4L).build())
                .build(), Course.class);
        assertEquals(2L, course.getTeacher().getId());
        assertEquals(4L, course.getDepartment().getId());

        Submission submission = modelMapper.map(SubmissionDTO.builder()
                .assignment(AssignmentDTO.builder().id(5L).build())
                .student(UserDTO.builder().id(3L).build())
                .build(), Submission.class);
        assertEquals(5L, submission.getAssignment().getId());
        assertEquals(3L, submission.getStudent().getId());

        Assignment assignment = modelMapper.map(AssignmentDTO.builder()
                .course(CourseDTO.builder().id(1L).build())
                .build(), Assignment.class);
        assertEquals(1L, assignment.getCourse().getId());

        Grade grade = modelMapper.map(GradeDTO.builder()
                .submission(SubmissionDTO.builder().id(6L).build())
                .build(), Grade.class);
        assertEquals(6L, grade.getSubmission().getId());

        Department department = modelMapper.map(DepartmentDTO.builder()
                .faculty(FacultyDTO.builder().id(7L).build())
                .build(), Department.class);
        assertEquals(7L, department.getFaculty().getId());

        Faculty faculty = modelMapper.map(FacultyDTO.builder()
                .university(UniversityDTO.builder().id(8L).build())
                .build(), Faculty.class);
        assertEquals(8L, faculty.getUniversity().getId());

        FileMetadatum file = modelMapper.map(FileMetadatumDTO.builder()
                .submission(SubmissionDTO.builder().id(6L).build())
                .student(UserDTO.builder().id(3L).build())
                .build(), FileMetadatum.class);
        assertEquals(6L, file.getSubmission().getId());
        assertEquals(3L, file.getStudent().getId());

        User user = modelMapper.map(UserDTO.builder().name("Name").role(UserRole.STUDENT).build(), User.class);
        assertEquals("Name", user.getName());
        assertEquals(UserRole.STUDENT, user.getRole());
    }
}
