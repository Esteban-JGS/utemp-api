package org.una.programmingIII.UTEMP_Project.service;

import org.mockito.Spy;
import org.una.programmingIII.UTEMP_Project.transformers.mappers.GenericMapperFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.una.programmingIII.UTEMP_Project.dtos.CourseDTO;
import org.una.programmingIII.UTEMP_Project.dtos.EnrollmentDTO;
import org.una.programmingIII.UTEMP_Project.dtos.NotificationDTO;
import org.una.programmingIII.UTEMP_Project.dtos.SubmissionDTO;
import org.una.programmingIII.UTEMP_Project.dtos.UserDTO;
import org.una.programmingIII.UTEMP_Project.exceptions.ResourceAlreadyExistsException;
import org.una.programmingIII.UTEMP_Project.exceptions.ResourceNotFoundException;
import org.una.programmingIII.UTEMP_Project.models.Course;
import org.una.programmingIII.UTEMP_Project.models.Enrollment;
import org.una.programmingIII.UTEMP_Project.models.Notification;
import org.una.programmingIII.UTEMP_Project.models.Submission;
import org.una.programmingIII.UTEMP_Project.models.User;
import org.una.programmingIII.UTEMP_Project.repositories.UserRepository;
import org.una.programmingIII.UTEMP_Project.services.UserServiceImplementation;
import org.una.programmingIII.UTEMP_Project.services.notification.NotificationService;
import org.una.programmingIII.UTEMP_Project.services.passwordEncryption.PasswordEncryptionService;
import org.una.programmingIII.UTEMP_Project.transformers.mappers.GenericMapper;
import org.una.programmingIII.UTEMP_Project.validators.UserValidator;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.una.programmingIII.UTEMP_Project.models.UserRole.STUDENT;

public class UserServiceImplementationTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncryptionService passwordEncryptionService;
    @Mock
    private UserValidator userValidator;
    @Mock
    private NotificationService notificationService;
    @Mock
    private Pageable pageable;
    @Mock
    private GenericMapper<User, UserDTO> userMapper;
    @Mock
    private GenericMapper<Notification, NotificationDTO> notificationMapper;
    @Mock
    private GenericMapper<Enrollment, EnrollmentDTO> enrollmentMapper;
    @Mock
    private GenericMapper<Course, CourseDTO> courseMapper;
    @Mock
    private GenericMapper<Submission, SubmissionDTO> submissionMapper;

    @Spy
    private GenericMapperFactory mapperFactory = new MockMapperFactory(this);

    @InjectMocks
    private UserServiceImplementation userService;

    private UserDTO userDTO;
    private User user;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        userDTO = new UserDTO();
        userDTO.setId(1L);
        userDTO.setName("John Doe");
        userDTO.setEmail("johndoe@example.com");
        userDTO.setIdentificationNumber("123456");
        userDTO.setPassword("password");
        userDTO.setRole(STUDENT);

        user = new User();
        user.setId(1L);
        user.setName("John Doe");
        user.setEmail("johndoe@example.com");
        user.setIdentificationNumber("123456");
    }

    @Test
    public void testCreateUserSuccess() {
        when(userRepository.existsByIdentificationNumber(userDTO.getIdentificationNumber())).thenReturn(false);
        when(passwordEncryptionService.encryptPassword(userDTO.getPassword())).thenReturn("encryptedPassword");
        when(userMapper.convertToEntity(userDTO)).thenReturn(user);
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(userMapper.convertToDTO(user)).thenReturn(userDTO);

        UserDTO result = userService.createUser(userDTO);

        assertNotNull(result);
        assertEquals(userDTO.getName(), result.getName());
        assertEquals("encryptedPassword", user.getPassword());
        verify(userValidator, times(1)).validate(userDTO);
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    public void testCreateUserAlreadyExists() {
        when(userRepository.existsByIdentificationNumber(userDTO.getIdentificationNumber())).thenReturn(true);

        assertThrows(ResourceAlreadyExistsException.class, () -> userService.createUser(userDTO));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    public void testGetUserByIdSuccess() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userMapper.convertToDTO(user)).thenReturn(userDTO);

        Optional<UserDTO> result = userService.getUserById(1L);

        assertTrue(result.isPresent());
        assertEquals(userDTO.getName(), result.get().getName());
    }

    @Test
    public void testGetUserByIdNotFound() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        ResourceNotFoundException exception =
                assertThrows(ResourceNotFoundException.class, () -> userService.getUserById(1L));
        assertEquals("User not found with id: 1", exception.getMessage());
    }

    @Test
    public void testGetAllUsersSuccess() {
        Page<User> userPage = new PageImpl<>(List.of(user));
        when(userRepository.findAll(pageable)).thenReturn(userPage);
        when(userMapper.convertToDTO(user)).thenReturn(userDTO);

        Page<UserDTO> result = userService.getAllUsers(pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(userDTO.getName(), result.getContent().getFirst().getName());
    }

    @Test
    public void testUpdateUserSuccess() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(userMapper.convertToDTO(user)).thenAnswer(invocation -> {
            UserDTO dto = new UserDTO();
            dto.setName(invocation.<User>getArgument(0).getName());
            return dto;
        });

        userDTO.setName("Updated Name");
        Optional<UserDTO> result = userService.updateUser(1L, userDTO);

        assertTrue(result.isPresent());
        assertEquals("Updated Name", result.get().getName());
        assertEquals("Updated Name", user.getName());
        verify(userValidator, times(1)).validate(userDTO);
    }

    @Test
    public void testDeleteUserSuccess() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        boolean result = userService.deleteUser(1L);

        assertTrue(result);
        verify(userRepository, times(1)).delete(user);
    }

    @Test
    public void testDeleteUserNotFound() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> userService.deleteUser(1L));
        verify(userRepository, never()).delete(any(User.class));
    }
}
