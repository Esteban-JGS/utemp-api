package org.una.programmingIII.UTEMP_Project.service;

import org.mockito.Spy;
import org.una.programmingIII.UTEMP_Project.transformers.mappers.GenericMapperFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.una.programmingIII.UTEMP_Project.dtos.AssignmentDTO;
import org.una.programmingIII.UTEMP_Project.dtos.SubmissionDTO;
import org.una.programmingIII.UTEMP_Project.exceptions.ResourceNotFoundException;
import org.una.programmingIII.UTEMP_Project.models.Assignment;
import org.una.programmingIII.UTEMP_Project.models.Course;
import org.una.programmingIII.UTEMP_Project.models.Submission;
import org.una.programmingIII.UTEMP_Project.models.User;
import org.una.programmingIII.UTEMP_Project.repositories.AssignmentRepository;
import org.una.programmingIII.UTEMP_Project.repositories.CourseRepository;
import org.una.programmingIII.UTEMP_Project.repositories.SubmissionRepository;
import org.una.programmingIII.UTEMP_Project.services.assignment.AssignmentServiceImplementation;
import org.una.programmingIII.UTEMP_Project.services.autoReview.AutoReviewService;
import org.una.programmingIII.UTEMP_Project.services.notification.NotificationService;
import org.una.programmingIII.UTEMP_Project.transformers.mappers.GenericMapper;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AssignmentServiceImplementationTest {

    @Spy
    private GenericMapperFactory mapperFactory = new MockMapperFactory(this);

    @InjectMocks
    private AssignmentServiceImplementation assignmentService;

    @Mock
    private AssignmentRepository assignmentRepository;

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private AutoReviewService autoReviewService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private GenericMapper<Assignment, AssignmentDTO> assignmentMapper;

    @Mock
    private GenericMapper<Submission, SubmissionDTO> submissionMapper;


    private Assignment assignment;
    private AssignmentDTO assignmentDTO;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        assignment = new Assignment();
        assignment.setId(1L);
        assignment.setTitle("Test Assignment");
        assignment.setLastUpdate(LocalDateTime.now());

        assignmentDTO = new AssignmentDTO();
        assignmentDTO.setId(1L);
        assignmentDTO.setTitle("Test Assignment DTO");
        assignmentDTO.getCourse().setId(10L);

        when(assignmentMapper.convertToDTO(any(Assignment.class))).thenReturn(assignmentDTO);
        when(assignmentMapper.convertToEntity(any(AssignmentDTO.class))).thenReturn(assignment);
    }

    @Test
    void getAllAssignments_shouldReturnAssignmentsPage() {
        Page<Assignment> assignments = new PageImpl<>(Collections.singletonList(assignment));
        when(assignmentRepository.findAll(any(PageRequest.class))).thenReturn(assignments);

        Page<AssignmentDTO> result = assignmentService.getAllAssignments(PageRequest.of(0, 10));

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        verify(assignmentRepository, times(1)).findAll(any(PageRequest.class));
    }

    @Test
    void getAssignmentById_shouldReturnAssignment() {
        when(assignmentRepository.findById(1L)).thenReturn(Optional.of(assignment));

        Optional<AssignmentDTO> result = assignmentService.getAssignmentById(1L);

        assertTrue(result.isPresent());
        assertEquals("Test Assignment DTO", result.get().getTitle());
        verify(assignmentRepository, times(1)).findById(1L);
    }

    @Test
    void getAssignmentById_shouldThrowResourceNotFoundException() {
        when(assignmentRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> assignmentService.getAssignmentById(1L));
        verify(assignmentRepository, times(1)).findById(1L);
    }

    @Test
    void createAssignment_shouldSaveAndReturnAssignmentDTO() {
        Course course = new Course();
        course.setId(10L);
        when(courseRepository.findById(10L)).thenReturn(Optional.of(course));
        when(assignmentRepository.save(any(Assignment.class))).thenReturn(assignment);

        AssignmentDTO result = assignmentService.createAssignment(assignmentDTO);

        assertNotNull(result);
        assertEquals("Test Assignment DTO", result.getTitle());
        assertSame(course, assignment.getCourse());
        verify(assignmentRepository, times(1)).save(any(Assignment.class));
    }

    @Test
    void createAssignment_shouldThrowResourceNotFoundException() {
        when(courseRepository.findById(anyLong())).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> assignmentService.createAssignment(assignmentDTO));
        verify(assignmentRepository, never()).save(any());
    }

    @Test
    void deleteAssignment_shouldDeleteAssignment() {
        when(assignmentRepository.findById(1L)).thenReturn(Optional.of(assignment));

        assertDoesNotThrow(() -> assignmentService.deleteAssignment(1L));
        verify(assignmentRepository, times(1)).delete(any(Assignment.class));
    }

    @Test
    void deleteAssignment_shouldThrowResourceNotFoundException() {
        when(assignmentRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> assignmentService.deleteAssignment(1L));
        verify(assignmentRepository, never()).delete(any());
    }

    @Test
    void addSubmissionToAssignment_shouldAddSubmissionAndReturnDTO() {
        User teacher = new User();
        teacher.setId(5L);
        teacher.setEmail("teacher@example.com");
        Course course = new Course();
        course.setTeacher(teacher);
        assignment.setCourse(course);

        User student = new User();
        student.setName("Student");
        Submission submission = new Submission();
        submission.setId(1L);
        submission.setStudent(student);
        SubmissionDTO submissionDTO = new SubmissionDTO();
        submissionDTO.setId(1L);

        when(assignmentRepository.findById(1L)).thenReturn(Optional.of(assignment));
        when(submissionMapper.convertToEntity(submissionDTO)).thenReturn(submission);
        when(submissionRepository.save(any(Submission.class))).thenReturn(submission);
        when(submissionMapper.convertToDTO(any(Submission.class))).thenReturn(submissionDTO);

        SubmissionDTO result = assignmentService.addSubmissionToAssignment(1L, submissionDTO);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        verify(submissionRepository, times(1)).save(any(Submission.class));
        verify(autoReviewService, times(1)).autoReviewSubmission(1L);
        assertSame(assignment, submission.getAssignment());
        assertTrue(assignment.getSubmissions().contains(submission));
        verify(notificationService, times(1)).sendNotificationToUser(eq(5L), anyString());
    }
}
