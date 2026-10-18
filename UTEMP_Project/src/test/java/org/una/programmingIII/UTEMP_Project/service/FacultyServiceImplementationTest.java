package org.una.programmingIII.UTEMP_Project.service;

import org.mockito.Spy;
import org.una.programmingIII.UTEMP_Project.transformers.mappers.GenericMapperFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.una.programmingIII.UTEMP_Project.dtos.DepartmentDTO;
import org.una.programmingIII.UTEMP_Project.dtos.FacultyDTO;
import org.una.programmingIII.UTEMP_Project.exceptions.ResourceNotFoundException;
import org.una.programmingIII.UTEMP_Project.models.Department;
import org.una.programmingIII.UTEMP_Project.models.Faculty;
import org.una.programmingIII.UTEMP_Project.models.University;
import org.una.programmingIII.UTEMP_Project.repositories.DepartmentRepository;
import org.una.programmingIII.UTEMP_Project.repositories.FacultyRepository;
import org.una.programmingIII.UTEMP_Project.repositories.UniversityRepository;
import org.una.programmingIII.UTEMP_Project.services.faculty.FacultyServiceImplementation;
import org.una.programmingIII.UTEMP_Project.transformers.mappers.GenericMapper;

import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class FacultyServiceImplementationTest {

    @Mock
    private FacultyRepository facultyRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private UniversityRepository universityRepository;

    @Mock
    private GenericMapper<Faculty, FacultyDTO> facultyMapper;

    @Mock
    private GenericMapper<Department, DepartmentDTO> departmentMapper;

    @Spy
    private GenericMapperFactory mapperFactory = new MockMapperFactory(this);

    @InjectMocks
    private FacultyServiceImplementation facultyService;

    private Faculty faculty;
    private FacultyDTO facultyDTO;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        faculty = new Faculty();
        faculty.setId(1L);
        faculty.setName("Engineering");

        facultyDTO = new FacultyDTO();
        facultyDTO.setId(1L);
        facultyDTO.setName("Engineering");

        when(facultyMapper.convertToDTO(any(Faculty.class))).thenReturn(facultyDTO);
        when(facultyMapper.convertToEntity(any(FacultyDTO.class))).thenReturn(faculty);
    }

    @Test
    void testGetFacultyById_Success() {
        when(facultyRepository.findById(1L)).thenReturn(Optional.of(faculty));

        Optional<FacultyDTO> result = facultyService.getFacultyById(1L);

        assertTrue(result.isPresent());
        assertEquals("Engineering", result.get().getName());
        verify(facultyRepository, times(1)).findById(1L);
    }

    @Test
    void testCreateFaculty_Success() {
        facultyDTO.getUniversity().setId(1L);
        when(facultyRepository.save(any(Faculty.class))).thenReturn(faculty);
        when(universityRepository.findById(anyLong())).thenReturn(Optional.of(mock(University.class)));

        FacultyDTO createdFaculty = facultyService.createFaculty(facultyDTO);

        assertNotNull(createdFaculty);
        assertEquals("Engineering", createdFaculty.getName());
        verify(facultyRepository, times(1)).save(any(Faculty.class));
    }

    @Test
    void testUpdateFaculty_Success() {
        when(facultyRepository.findById(1L)).thenReturn(Optional.of(faculty));
        when(facultyRepository.save(any(Faculty.class))).thenReturn(faculty);
        when(universityRepository.findById(1L)).thenReturn(Optional.of(mock(University.class)));
        when(facultyMapper.convertToDTO(any(Faculty.class))).thenAnswer(invocation -> {
            FacultyDTO dto = new FacultyDTO();
            dto.setName(invocation.<Faculty>getArgument(0).getName());
            return dto;
        });

        FacultyDTO updatedFacultyDTO = new FacultyDTO();
        updatedFacultyDTO.setName("New Engineering");
        updatedFacultyDTO.getUniversity().setId(1L);

        Optional<FacultyDTO> result = facultyService.updateFaculty(1L, updatedFacultyDTO);

        assertTrue(result.isPresent());
        assertEquals("New Engineering", result.get().getName());
        verify(facultyRepository, times(1)).save(any(Faculty.class));
    }

    @Test
    void testDeleteFaculty_Success() {
        when(facultyRepository.findById(1L)).thenReturn(Optional.of(faculty));

        facultyService.deleteFaculty(1L);

        verify(facultyRepository, times(1)).delete(faculty);
    }

    @Test
    void testGetAllFaculties_Success() {
        Page<Faculty> facultyPage = new PageImpl<>(java.util.Collections.singletonList(faculty));
        when(facultyRepository.findAll(any(Pageable.class))).thenReturn(facultyPage);

        Page<FacultyDTO> result = facultyService.getAllFaculties(Pageable.unpaged());

        assertEquals(1, result.getTotalElements());
        verify(facultyRepository, times(1)).findAll(any(Pageable.class));
    }

    @Test
    void testAddDepartmentToFaculty_Success() {
        Department department = new Department();
        when(facultyRepository.findById(1L)).thenReturn(Optional.of(faculty));
        when(departmentMapper.convertToEntity(any(DepartmentDTO.class))).thenReturn(department);
        when(departmentRepository.save(any(Department.class))).thenReturn(department);

        facultyService.addDepartmentToFaculty(1L, mock(DepartmentDTO.class));

        assertSame(faculty, department.getFaculty());
        assertTrue(faculty.getDepartments().contains(department));
        verify(departmentRepository, times(1)).save(department);
    }

    @Test
    void testRemoveDepartmentFromFaculty_Success() {
        Department department = mock(Department.class);
        faculty.getDepartments().add(department);

        when(facultyRepository.findById(1L)).thenReturn(Optional.of(faculty));
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(department));

        facultyService.removeDepartmentFromFaculty(1L, 1L);

        verify(departmentRepository, times(1)).delete(department);
    }

    @Test
    void testGetFacultyById_NotFound() {
        when(facultyRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> facultyService.getFacultyById(1L));
    }
}
