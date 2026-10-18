package org.una.programmingIII.UTEMP_Project.service;

import org.mockito.Spy;
import org.una.programmingIII.UTEMP_Project.transformers.mappers.GenericMapperFactory;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageImpl;
import org.una.programmingIII.UTEMP_Project.dtos.AssignmentDTO;
import org.una.programmingIII.UTEMP_Project.dtos.GradeDTO;
import org.una.programmingIII.UTEMP_Project.dtos.SubmissionDTO;
import org.una.programmingIII.UTEMP_Project.dtos.UserDTO;
import org.una.programmingIII.UTEMP_Project.exceptions.ResourceNotFoundException;
import org.una.programmingIII.UTEMP_Project.models.Assignment;
import org.una.programmingIII.UTEMP_Project.models.Course;
import org.una.programmingIII.UTEMP_Project.models.Grade;
import org.una.programmingIII.UTEMP_Project.models.Submission;
import org.una.programmingIII.UTEMP_Project.models.User;
import org.una.programmingIII.UTEMP_Project.repositories.*;
import org.una.programmingIII.UTEMP_Project.services.notification.NotificationService;
import org.una.programmingIII.UTEMP_Project.services.submission.SubmissionServiceImplementation;
import org.una.programmingIII.UTEMP_Project.transformers.mappers.GenericMapper;

import java.util.List;
import java.util.Optional;

public class SubmissionServiceImplementationTest {

    @Spy
    private GenericMapperFactory mapperFactory = new MockMapperFactory(this);

    @InjectMocks
    private SubmissionServiceImplementation submissionService;

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private AssignmentRepository assignmentRepository;

    @Mock
    private GradeRepository gradeRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private GenericMapper<Submission, SubmissionDTO> submissionMapper;

    @Mock
    private GenericMapper<Grade, GradeDTO> gradeMapper;

    private Submission submission;
    private SubmissionDTO submissionDTO;
    private GradeDTO gradeDTO;
    private User student;
    private Assignment assignment;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);

        User teacher = new User();
        teacher.setId(3L);
        teacher.setEmail("teacher@example.com");
        Course course = new Course();
        course.setTeacher(teacher);
        assignment = new Assignment();
        assignment.setId(1L);
        assignment.setTitle("Homework");
        assignment.setCourse(course);
        student = new User();
        student.setId(2L);
        student.setName("Student");

        submission = new Submission();
        submission.setId(1L);
        submission.setFileName("testFile");
        submission.setStudent(student);
        submission.setAssignment(assignment);

        UserDTO studentDTO = new UserDTO();
        studentDTO.setId(2L);
        AssignmentDTO assignmentDTO = new AssignmentDTO();
        assignmentDTO.setId(1L);
        submissionDTO = new SubmissionDTO();
        submissionDTO.setId(1L);
        submissionDTO.setFileName("testFile");
        submissionDTO.setStudent(studentDTO);
        submissionDTO.setAssignment(assignmentDTO);

        gradeDTO = new GradeDTO();
        gradeDTO.setId(1L);
        gradeDTO.setGrade(90.0);
    }

    @Test
    public void testGetAllSubmissions() {
        Pageable pageable = PageRequest.of(0, 10);
        when(submissionRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(submission)));
        when(submissionMapper.convertToDTO(submission)).thenReturn(submissionDTO);

        Page<SubmissionDTO> result = submissionService.getAllSubmissions(pageable);

        assertEquals(List.of(submissionDTO), result.getContent());
        verify(submissionRepository, times(1)).findAll(pageable);
    }

    @Test
    public void testGetSubmissionById() {
        when(submissionRepository.findById(1L)).thenReturn(Optional.of(submission));
        when(submissionMapper.convertToDTO(submission)).thenReturn(submissionDTO);

        Optional<SubmissionDTO> result = submissionService.getSubmissionById(1L);

        assertTrue(result.isPresent());
        assertEquals("testFile", result.get().getFileName());
    }

    @Test
    public void testGetSubmissionByIdNotFound() {
        when(submissionRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> submissionService.getSubmissionById(1L));
    }

    @Test
    public void testCreateSubmission() {
        when(submissionMapper.convertToEntity(submissionDTO)).thenReturn(submission);
        when(userRepository.findById(2L)).thenReturn(Optional.of(student));
        when(assignmentRepository.findById(1L)).thenReturn(Optional.of(assignment));
        when(submissionRepository.save(submission)).thenReturn(submission);
        when(submissionMapper.convertToDTO(submission)).thenReturn(submissionDTO);

        SubmissionDTO result = submissionService.createSubmission(submissionDTO);

        assertNotNull(result);
        verify(submissionRepository, times(1)).save(submission);
    }

    @Test
    public void testCreateSubmissionAssignmentNotFound() {
        when(submissionMapper.convertToEntity(submissionDTO)).thenReturn(submission);
        when(userRepository.findById(2L)).thenReturn(Optional.of(student));
        when(assignmentRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> submissionService.createSubmission(submissionDTO));
        verify(submissionRepository, never()).save(any());
    }

    @Test
    public void testUpdateSubmission() {
        when(submissionRepository.findById(1L)).thenReturn(Optional.of(submission));
        when(submissionMapper.convertToEntity(submissionDTO)).thenReturn(submission);
        when(submissionRepository.save(submission)).thenReturn(submission);
        when(submissionMapper.convertToDTO(submission)).thenReturn(submissionDTO);

        Optional<SubmissionDTO> result = submissionService.updateSubmission(1L, submissionDTO);

        assertTrue(result.isPresent());
        assertEquals("testFile", result.get().getFileName());
        verify(submissionRepository, times(1)).save(submission);
    }

    @Test
    public void testUpdateSubmissionNotFound() {
        when(submissionRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> submissionService.updateSubmission(1L, submissionDTO));
    }

    @Test
    public void testDeleteSubmission() {
        when(submissionRepository.findById(1L)).thenReturn(Optional.of(submission));

        submissionService.deleteSubmission(1L);

        verify(submissionRepository, times(1)).delete(submission);
    }

    @Test
    public void testDeleteSubmissionNotFound() {
        when(submissionRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> submissionService.deleteSubmission(1L));
    }

    @Test
    public void testAddGradeToSubmission() {
        when(submissionRepository.findById(1L)).thenReturn(Optional.of(submission));
        when(gradeMapper.convertToEntity(gradeDTO)).thenReturn(new Grade());
        when(gradeRepository.save(any(Grade.class))).thenReturn(new Grade());
        when(gradeMapper.convertToDTO(any(Grade.class))).thenReturn(gradeDTO);

        GradeDTO result = submissionService.addGradeToSubmission(1L, gradeDTO);

        assertNotNull(result);
        verify(gradeRepository, times(1)).save(any(Grade.class));
    }

    @Test
    public void testRemoveGradeFromSubmission() {
        Grade grade = new Grade();
        submission.getGrades().add(grade);
        when(submissionRepository.findById(1L)).thenReturn(Optional.of(submission));
        when(gradeRepository.findById(1L)).thenReturn(Optional.of(grade));

        submissionService.removeGradeFromSubmission(1L, 1L);

        verify(gradeRepository, times(1)).delete(any(Grade.class));
    }

    @Test
    public void testGetSubmissionsByAssignmentId() {
        Pageable pageable = PageRequest.of(0, 10);
        when(assignmentRepository.existsById(1L)).thenReturn(true);
        Page<Submission> submissionPage = mock(Page.class);
        when(submissionRepository.findByAssignmentId(1L, pageable)).thenReturn(submissionPage);

        submissionService.getSubmissionsByAssignmentId(1L, pageable);

        verify(submissionRepository, times(1)).findByAssignmentId(1L, pageable);
    }
}
