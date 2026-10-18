package org.una.programmingIII.UTEMP_Project.service;

import org.hibernate.service.spi.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.una.programmingIII.UTEMP_Project.dtos.NotificationDTO;
import org.una.programmingIII.UTEMP_Project.dtos.UserDTO;
import org.una.programmingIII.UTEMP_Project.exceptions.ResourceNotFoundException;
import org.una.programmingIII.UTEMP_Project.models.Notification;
import org.una.programmingIII.UTEMP_Project.models.NotificationStatus;
import org.una.programmingIII.UTEMP_Project.models.User;
import org.una.programmingIII.UTEMP_Project.repositories.NotificationRepository;
import org.una.programmingIII.UTEMP_Project.repositories.UserRepository;
import org.una.programmingIII.UTEMP_Project.services.notification.NotificationServiceImplementation;
import org.una.programmingIII.UTEMP_Project.transformers.mappers.GenericMapper;
import org.una.programmingIII.UTEMP_Project.transformers.mappers.GenericMapperFactory;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
public class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private GenericMapper<Notification, NotificationDTO> notificationMapper;

    @Mock
    private GenericMapper<User, UserDTO> userMapper;

    private NotificationServiceImplementation notificationService;

    private Notification notification;
    private NotificationDTO notificationDTO;
    private User user;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        notificationService = new NotificationServiceImplementation(notificationRepository, userRepository, new MockMapperFactory(this));

        user = new User();
        user.setId(1L);
        notification = new Notification();
        notification.setId(1L);
        notification.setUser(user);
        notification.setMessage("Test Message");
        notification.setStatus(NotificationStatus.UNREAD);

        notificationDTO = new NotificationDTO();
        notificationDTO.setId(1L);
        notificationDTO.setMessage("Test Message");
        notificationDTO.setStatus(NotificationStatus.UNREAD);
        UserDTO userDTO = new UserDTO();
        userDTO.setId(1L);
        notificationDTO.setUser(userDTO);
    }

    @Test
    public void testGetAllNotifications() {
        Page<Notification> notificationPage = new PageImpl<>(java.util.Collections.singletonList(notification));
        when(notificationRepository.findAll(any(Pageable.class))).thenReturn(notificationPage);
        when(notificationMapper.convertToDTO(any(Notification.class))).thenReturn(notificationDTO);

        Page<NotificationDTO> result = notificationService.getAllNotifications(Pageable.unpaged());

        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        assertEquals("Test Message", result.getContent().getFirst().getMessage());
    }

    @Test
    public void testGetNotificationById() {
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(notification));
        when(notificationMapper.convertToDTO(notification)).thenReturn(notificationDTO);

        Optional<NotificationDTO> result = notificationService.getNotificationById(1L);

        assertTrue(result.isPresent());
        assertEquals("Test Message", result.get().getMessage());
    }

    @Test
    public void testCreateNotification() {
        when(notificationMapper.convertToEntity(notificationDTO)).thenReturn(notification);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(notificationRepository.save(notification)).thenReturn(notification);
        when(notificationMapper.convertToDTO(notification)).thenReturn(notificationDTO);

        NotificationDTO result = notificationService.createNotification(notificationDTO);

        assertNotNull(result);
        assertEquals("Test Message", result.getMessage());
        assertEquals(NotificationStatus.UNREAD, result.getStatus());
    }

    @Test
    public void testUpdateNotification() {
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(notification));
        when(notificationMapper.convertToDTO(notification)).thenReturn(notificationDTO);
        when(notificationRepository.save(notification)).thenReturn(notification);

        Optional<NotificationDTO> result = notificationService.updateNotification(1L, notificationDTO);

        assertTrue(result.isPresent());
        assertEquals("Test Message", result.get().getMessage());
    }

    @Test
    public void testDeleteNotification() {
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(notification));
        doNothing().when(notificationRepository).delete(notification);

        notificationService.deleteNotification(1L);

        verify(notificationRepository, times(1)).delete(notification);
    }

    @Test
    public void testGetNotificationsByUserId() {
        Page<Notification> notificationPage = new PageImpl<>(java.util.Collections.singletonList(notification));
        when(notificationRepository.findByUserId(1L, Pageable.unpaged())).thenReturn(notificationPage);
        when(notificationMapper.convertToDTO(any(Notification.class))).thenReturn(notificationDTO);

        Page<NotificationDTO> result = notificationService.getNotificationsByUserId(1L, Pageable.unpaged());

        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        assertEquals("Test Message", result.getContent().getFirst().getMessage());
    }

    @Test
    public void testAddNotificationToUser() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(notificationMapper.convertToEntity(notificationDTO)).thenReturn(notification);
        when(notificationRepository.save(notification)).thenReturn(notification);

        notificationService.addNotificationToUser(1L, notificationDTO);

        verify(notificationRepository, times(1)).save(notification);
    }

    @Test
    public void testRemoveNotificationFromUser() {
        user.getNotifications().add(notification);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(notification));
        doNothing().when(notificationRepository).delete(notification);

        notificationService.removeNotificationFromUser(1L, 1L);

        verify(notificationRepository, times(1)).delete(notification);
        assertTrue(user.getNotifications().isEmpty());
    }

    @Test
    public void testMarkAsRead() {
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(notification));

        notificationService.markAsRead(1L);

        assertEquals(NotificationStatus.READ, notification.getStatus());
    }

    @Test
    public void testSendNotificationToUser() {
        UserDTO userDTO = notificationDTO.getUser();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userMapper.convertToDTO(user)).thenReturn(userDTO);
        when(notificationMapper.convertToEntity(any(NotificationDTO.class))).thenReturn(notification);
        when(notificationRepository.save(any(Notification.class))).thenReturn(notification);

        notificationService.sendNotificationToUser(1L, "Test Message");

        ArgumentCaptor<NotificationDTO> dtoCaptor = ArgumentCaptor.forClass(NotificationDTO.class);
        verify(notificationMapper).convertToEntity(dtoCaptor.capture());
        assertEquals("Test Message", dtoCaptor.getValue().getMessage());
        assertEquals(NotificationStatus.UNREAD, dtoCaptor.getValue().getStatus());
        assertSame(userDTO, dtoCaptor.getValue().getUser());
        verify(notificationRepository, times(1)).save(any(Notification.class));
    }

    @Test
    public void testGetNotificationByIdNotFound() {
        when(notificationRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> notificationService.getNotificationById(1L));
    }

    @Test
    public void testCreateNotificationWithException() {
        DataAccessException databaseError = new DataAccessException("Database error") {};
        when(notificationMapper.convertToEntity(notificationDTO)).thenReturn(notification);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(notificationRepository.save(any(Notification.class))).thenThrow(databaseError);

        ServiceException thrown = assertThrows(ServiceException.class, () -> notificationService.createNotification(notificationDTO));

        assertEquals("Error creating notification", thrown.getMessage());
        assertSame(databaseError, thrown.getCause());
    }

    @Test
    public void testDeleteNotificationNotFound() {
        when(notificationRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> notificationService.deleteNotification(1L));
    }
}
