package com.nestorria.server.modules.notification;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.github.benmanes.caffeine.cache.Cache;
import com.nestorria.server.common.event.NotificationEvent;
import com.nestorria.server.modules.user.User;
import com.nestorria.server.modules.user.UserRepository;

@SpringBootTest
class NotificationCacheTest {

    @Autowired private NotificationService notificationService;
    @Autowired private CacheManager cacheManager;

    @MockitoBean private NotificationRepository notificationRepository;
    @MockitoBean private UserRepository userRepository;

    private static final String USER_ID = "user-1";

    private record CacheStats(long hitCount, long missCount) {}

    private CacheStats snapshotStats(String cacheName) {
        CaffeineCache caffeineCache = (CaffeineCache) cacheManager.getCache(cacheName);
        Cache<Object, Object> nativeCache = caffeineCache.getNativeCache();
        return new CacheStats(nativeCache.stats().hitCount(), nativeCache.stats().missCount());
    }

    private void clearCache(String cacheName) {
        cacheManager.getCache(cacheName).clear();
    }

    private User testUser;

    @BeforeEach
    void setUp() {
        clearCache("unreadCount");
        testUser = new User(USER_ID, "Test User", "test@test.com", "img.jpg");
    }

    // ==================== getUnreadCount ====================

    @Test
    void getUnreadCount_CacheMiss_ThenCacheHit() {
        when(notificationRepository.countByUserIdAndIsReadFalse(USER_ID)).thenReturn(3L);

        CacheStats before = snapshotStats("unreadCount");
        long count1 = notificationService.getUnreadCount(USER_ID);
        CacheStats afterFirst = snapshotStats("unreadCount");
        long count2 = notificationService.getUnreadCount(USER_ID);
        CacheStats afterSecond = snapshotStats("unreadCount");

        assertEquals(3L, count1);
        assertEquals(3L, count2);
        assertEquals(before.missCount() + 1, afterFirst.missCount());
        assertEquals(before.hitCount(), afterFirst.hitCount());
        assertEquals(afterFirst.missCount(), afterSecond.missCount());
        assertEquals(afterFirst.hitCount() + 1, afterSecond.hitCount());
    }

    // ==================== markAsRead evicts ====================

    @Test
    void markAsRead_EvictsUnreadCountCache() {
        when(notificationRepository.countByUserIdAndIsReadFalse(USER_ID)).thenReturn(3L);

        notificationService.getUnreadCount(USER_ID);
        CacheStats afterPopulate = snapshotStats("unreadCount");

        Notification notification = new Notification(
            testUser, NotificationType.BOOKING_CONFIRMED, "Title", "Msg", "booking", "b-1"
        );
        notification.setId(UUID.randomUUID().toString());
        when(notificationRepository.findById(notification.getId())).thenReturn(Optional.of(notification));
        when(notificationRepository.save(any(Notification.class))).thenReturn(notification);

        notificationService.markAsRead(notification.getId(), USER_ID);

        notificationService.getUnreadCount(USER_ID);
        CacheStats afterEvict = snapshotStats("unreadCount");

        assertEquals(afterPopulate.missCount() + 1, afterEvict.missCount());
    }

    // ==================== markAllAsRead evicts ====================

    @Test
    void markAllAsRead_EvictsUnreadCountCache() {
        when(notificationRepository.countByUserIdAndIsReadFalse(USER_ID)).thenReturn(3L);

        notificationService.getUnreadCount(USER_ID);
        CacheStats afterPopulate = snapshotStats("unreadCount");

        notificationService.markAllAsRead(USER_ID);

        notificationService.getUnreadCount(USER_ID);
        CacheStats afterEvict = snapshotStats("unreadCount");

        assertEquals(afterPopulate.missCount() + 1, afterEvict.missCount());
    }

    // ==================== createNotification evicts ====================

    @Test
    void createNotification_EvictsUnreadCountCache() {
        when(notificationRepository.countByUserIdAndIsReadFalse(USER_ID)).thenReturn(3L);

        notificationService.getUnreadCount(USER_ID);
        CacheStats afterPopulate = snapshotStats("unreadCount");

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(testUser));
        NotificationEvent event = new NotificationEvent(
            USER_ID, NotificationType.REVIEW_RECEIVED, "New msg", "Body", "message", "m-1"
        );
        notificationService.createNotification(event);

        notificationService.getUnreadCount(USER_ID);
        CacheStats afterEvict = snapshotStats("unreadCount");

        assertEquals(afterPopulate.missCount() + 1, afterEvict.missCount());
    }
}
