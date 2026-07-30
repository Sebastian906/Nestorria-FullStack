package com.nestorria.server.modules.notification;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;

import com.nestorria.server.common.event.NotificationEvent;
import com.nestorria.server.common.exception.ResourceNotFoundException;
import com.nestorria.server.modules.notification.dto.NotificationResponse;
import com.nestorria.server.modules.user.User;
import com.nestorria.server.modules.user.UserRepository;

import java.util.List;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private NotificationService notificationService;

    private User testUser;
    private Notification testNotification;

    @BeforeEach
    void setUp() {
        testUser = new User("user-123", "Test User", "test@example.com", "https://example.com/img.jpg");

        testNotification = new Notification(
            testUser,
            NotificationType.BOOKING_CONFIRMED,
            "Reserva confirmada",
            "Tu reserva ha sido confirmada",
            "booking",
            "booking-456"
        );
        testNotification.setId(UUID.randomUUID().toString());
    }

    @Test
    void getUserNotifications_ReturnsPaginatedNotifications() {
        // Arrange
        String userId = "user-123";
        PageRequest pageable = PageRequest.of(0, 10);
        Page<Notification> notificationPage = new PageImpl<>(List.of(testNotification), pageable, 1);

        when(notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable))
            .thenReturn(notificationPage);

        // Act
        Page<NotificationResponse> result = notificationService.getUserNotifications(userId, pageable);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(NotificationType.BOOKING_CONFIRMED, result.getContent().getFirst().type());
        assertEquals("Reserva confirmada", result.getContent().getFirst().title());
        verify(notificationRepository).findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    @Test
    void getUnreadCount_ReturnsCorrectCount() {
        // Arrange
        String userId = "user-123";
        when(notificationRepository.countByUserIdAndIsReadFalse(userId)).thenReturn(5L);

        // Act
        long count = notificationService.getUnreadCount(userId);

        // Assert
        assertEquals(5L, count);
        verify(notificationRepository).countByUserIdAndIsReadFalse(userId);
    }

    @Test
    void markAsRead_WhenNotificationExistsAndBelongsToUser_MarksAsRead() {
        // Arrange
        String notificationId = testNotification.getId();
        String userId = "user-123";

        when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(testNotification));
        when(notificationRepository.save(any(Notification.class))).thenReturn(testNotification);

        // Act
        notificationService.markAsRead(notificationId, userId);

        // Assert
        assertTrue(testNotification.isRead());
        assertNotNull(testNotification.getReadAt());
        verify(notificationRepository).save(testNotification);
    }

    @Test
    void markAsRead_WhenNotificationNotFound_ThrowsResourceNotFoundException() {
        // Arrange
        String notificationId = "non-existent-id";
        String userId = "user-123";

        when(notificationRepository.findById(notificationId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class,
            () -> notificationService.markAsRead(notificationId, userId));
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void markAsRead_WhenNotificationBelongsToDifferentUser_ThrowsAccessDeniedException() {
        // Arrange
        String notificationId = testNotification.getId();
        String differentUserId = "user-999";

        when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(testNotification));

        // Act & Assert
        assertThrows(AccessDeniedException.class,
            () -> notificationService.markAsRead(notificationId, differentUserId));
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void markAsRead_WhenNotificationAlreadyRead_DoesNotSaveAgain() {
        // Arrange
        testNotification.markAsRead();
        String notificationId = testNotification.getId();
        String userId = "user-123";

        when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(testNotification));

        // Act
        notificationService.markAsRead(notificationId, userId);

        // Assert
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void markAllAsRead_CallsRepository() {
        // Arrange
        String userId = "user-123";
        when(notificationRepository.markAllAsRead(userId)).thenReturn(3);

        // Act
        notificationService.markAllAsRead(userId);

        // Assert
        verify(notificationRepository).markAllAsRead(userId);
    }

    @Test
    void createNotification_WhenUserExists_PersistsNotification() {
        // Arrange
        NotificationEvent event = new NotificationEvent(
            "user-123",
            NotificationType.BOOKING_CONFIRMED,
            "Reserva confirmada",
            "Tu reserva ha sido confirmada",
            "booking",
            "booking-456"
        );

        when(userRepository.findById("user-123")).thenReturn(Optional.of(testUser));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification n = invocation.getArgument(0);
            n.setId(UUID.randomUUID().toString());
            return n;
        });

        // Act
        notificationService.createNotification(event);

        // Assert
        verify(userRepository).findById("user-123");
        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    void createNotification_WhenUserNotFound_ThrowsResourceNotFoundException() {
        // Arrange
        NotificationEvent event = new NotificationEvent(
            "non-existent-user",
            NotificationType.BOOKING_CONFIRMED,
            "Reserva confirmada",
            "Tu reserva ha sido confirmada",
            "booking",
            "booking-456"
        );

        when(userRepository.findById("non-existent-user")).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class,
            () -> notificationService.createNotification(event));
        verify(notificationRepository, never()).save(any());
    }
}
