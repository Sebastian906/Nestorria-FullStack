package com.nestorria.server.modules.notification;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nestorria.server.common.event.NotificationEvent;
import com.nestorria.server.common.exception.ResourceNotFoundException;
import com.nestorria.server.modules.notification.dto.NotificationResponse;
import com.nestorria.server.modules.user.User;
import com.nestorria.server.modules.user.UserRepository;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public NotificationService(
            NotificationRepository notificationRepository,
            UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public Page<NotificationResponse> getUserNotifications(String userId, Pageable pageable) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
            .map(NotificationResponse::fromEntity);
    }

    @Cacheable(cacheNames = "unreadCount", key = "#userId")
    @Transactional(readOnly = true)
    public long getUnreadCount(String userId) {
        return notificationRepository.countByUserIdAndIsReadFalse(userId);
    }

    @CacheEvict(cacheNames = "unreadCount", key = "#userId")
    @Transactional
    public void markAsRead(String notificationId, String userId) {
        Notification notification = notificationRepository.findById(notificationId)
            .orElseThrow(() -> new ResourceNotFoundException("Notificación no encontrada: " + notificationId));

        if (!notification.getUser().getId().equals(userId)) {
            throw new org.springframework.security.access.AccessDeniedException(
                "No tienes permiso para marcar esta notificación como leída"
            );
        }

        if (!notification.isRead()) {
            notification.markAsRead();
            notificationRepository.save(notification);
        }
    }

    @CacheEvict(cacheNames = "unreadCount", key = "#userId")
    @Transactional
    public void markAllAsRead(String userId) {
        notificationRepository.markAllAsRead(userId);
    }

    @CacheEvict(cacheNames = "unreadCount", key = "#event.userId")
    @Transactional
    public Notification createNotification(NotificationEvent event) {
        User user = userRepository.findById(event.userId())
            .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado: " + event.userId()));

        Notification notification = new Notification(
            user,
            event.type(),
            event.title(),
            event.message(),
            event.referenceType(),
            event.referenceId()
        );

        notificationRepository.save(notification);
        return notification;
    }
}
