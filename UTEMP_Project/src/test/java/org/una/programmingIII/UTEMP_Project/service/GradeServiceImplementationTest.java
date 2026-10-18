package org.una.programmingIII.UTEMP_Project.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.dao.DataAccessResourceFailureException;
import org.una.programmingIII.UTEMP_Project.dtos.GradeDTO;
import org.una.programmingIII.UTEMP_Project.exceptions.InvalidDataException;
import org.una.programmingIII.UTEMP_Project.exceptions.ResourceNotFoundException;
import org.una.programmingIII.UTEMP_Project.models.Grade;
import org.una.programmingIII.UTEMP_Project.models.Submission;
import org.una.programmingIII.UTEMP_Project.repositories.GradeRepository;
import org.una.programmingIII.UTEMP_Project.repositories.SubmissionRepository;
import org.una.programmingIII.UTEMP_Project.services.grade.GradeServiceImplementation;
import org.una.programmingIII.UTEMP_Project.transformers.mappers.GenericMapper;
import org.una.programmingIII.UTEMP_Project.transformers.mappers.GenericMapperFactory;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class GradeServiceImplementationTest {

    @Mock
    private GradeRepository gradeRepository;

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private GenericMapper<Grade, GradeDTO> gradeMapper;

    private GradeServiceImplementation gradeService;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        gradeService = new GradeServiceImplementation(gradeRepository, submissionRepository, new MockMapperFactory(this));
    }

    @Test
    public void testGetAllGrades() {
        Pageable pageable = PageRequest.of(0, 10);
        Grade grade = new Grade();
        GradeDTO gradeDTO = new GradeDTO();
        gradeDTO.setGrade(90.0);

        when(gradeRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(grade)));
        when(gradeMapper.convertToDTO(any(Grade.class))).thenReturn(gradeDTO);

        Page<GradeDTO> result = gradeService.getAllGrades(pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(90, result.getContent().getFirst().getGrade());
    }

    @Test
    public void testGetGradeById() {
        Long gradeId = 1L;
        Grade grade = new Grade();
        GradeDTO gradeDTO = new GradeDTO();
        gradeDTO.setGrade(95.0);

        when(gradeRepository.findById(gradeId)).thenReturn(Optional.of(grade));
        when(gradeMapper.convertToDTO(any(Grade.class))).thenReturn(gradeDTO);

        Optional<GradeDTO> result = gradeService.getGradeById(gradeId);

        assertTrue(result.isPresent());
        assertEquals(95, result.get().getGrade());
    }

    @Test
    public void testCreateGrade() {
        GradeDTO gradeDTO = new GradeDTO();
        gradeDTO.setGrade(85.0);
        gradeDTO.setComments("Good work");
        gradeDTO.getSubmission().setId(1L);

        Grade grade = new Grade();
        Submission submission = new Submission();
        submission.setId(1L);
        when(gradeMapper.convertToEntity(gradeDTO)).thenReturn(grade);
        when(submissionRepository.findById(1L)).thenReturn(Optional.of(submission));
        when(gradeRepository.save(grade)).thenReturn(grade);
        when(gradeMapper.convertToDTO(grade)).thenReturn(gradeDTO);

        GradeDTO result = gradeService.createGrade(gradeDTO);

        assertNotNull(result);
        assertEquals(85, result.getGrade());
        assertSame(submission, grade.getSubmission());
    }

    @Test
    public void testUpdateGrade() {
        Long gradeId = 1L;
        GradeDTO gradeDTO = new GradeDTO();
        gradeDTO.setGrade(92.0);
        gradeDTO.setComments("Excellent");

        Grade existingGrade = new Grade();
        existingGrade.setGrade(85.0);
        existingGrade.setComments("Good");

        when(gradeRepository.findById(gradeId)).thenReturn(Optional.of(existingGrade));
        when(gradeRepository.save(existingGrade)).thenReturn(existingGrade);
        when(gradeMapper.convertToDTO(existingGrade)).thenReturn(gradeDTO);

        Optional<GradeDTO> result = gradeService.updateGrade(gradeId, gradeDTO);

        assertTrue(result.isPresent());
        assertEquals(92, result.get().getGrade());
        assertEquals("Excellent", result.get().getComments());
    }

    @Test
    public void testDeleteGrade() {
        Long gradeId = 1L;
        Grade grade = new Grade();

        when(gradeRepository.findById(gradeId)).thenReturn(Optional.of(grade));

        gradeService.deleteGrade(gradeId);

        verify(gradeRepository, times(1)).delete(grade);
    }

    @Test
    public void testGetGradesBySubmissionId() {
        Long submissionId = 1L;
        Pageable pageable = PageRequest.of(0, 10);
        Grade grade = new Grade();
        GradeDTO gradeDTO = new GradeDTO();
        gradeDTO.setGrade(75.0);

        when(gradeRepository.findBySubmissionsId(submissionId, pageable)).thenReturn(new PageImpl<>(List.of(grade)));
        when(gradeMapper.convertToDTO(any(Grade.class))).thenReturn(gradeDTO);

        Page<GradeDTO> result = gradeService.getGradesBySubmissionId(submissionId, pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(75, result.getContent().getFirst().getGrade());
    }

    @Test
    public void testGetGradeByIdNotFound() {
        Long gradeId = 1L;
        when(gradeRepository.findById(gradeId)).thenReturn(Optional.empty());

        ResourceNotFoundException thrown = assertThrows(ResourceNotFoundException.class, () -> gradeService.getGradeById(gradeId));
        assertEquals("Grade not found with id: 1", thrown.getMessage());
    }

    @Test
    public void testGetAllGradesWithDatabaseError() {
        Pageable pageable = PageRequest.of(0, 10);

        when(gradeRepository.findAll(pageable)).thenThrow(new DataAccessResourceFailureException("Database unavailable"));

        InvalidDataException thrown = assertThrows(InvalidDataException.class, () -> gradeService.getAllGrades(pageable));
        assertEquals("Invalid data: Error fetching grades from the database", thrown.getMessage());
    }
}
