package com.nestorria.server.modules.notification;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import com.nestorria.server.common.exception.ResourceNotFoundException;
import com.nestorria.server.modules.notification.dto.NotificationResponse;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = true)
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationService notificationService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private static final String TEST_USER_ID = "user-123";
    private static final String TEST_TOKEN = "test-jwt-token";

    private Jwt createMockJwt() {
        return Jwt.withTokenValue(TEST_TOKEN)
            .header("alg", "RS256")
            .subject(TEST_USER_ID)
            .claim("iss", "https://top-opossum-75.clerk.accounts.dev")
            .claim("exp", Instant.now().plusSeconds(3600))
            .claim("iat", Instant.now())
            .build();
    }

    @BeforeEach
    void setUp() {
        when(jwtDecoder.decode(TEST_TOKEN)).thenReturn(createMockJwt());
    }

    // ==================== GET /api/notifications/me ====================

    @Test
    void getMyNotifications_ReturnsPaginatedNotifications() throws Exception {
        NotificationResponse response = new NotificationResponse(
            "notif-1",
            NotificationType.BOOKING_CONFIRMED,
            "Booking Confirmed",
            "Your booking has been confirmed",
            "booking",
            "booking-456",
            false,
            Instant.now()
        );

        when(notificationService.getUserNotifications(eq(TEST_USER_ID), any(PageRequest.class)))
            .thenReturn(new PageImpl<>(List.of(response), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/notifications/me")
                .header("Authorization", "Bearer " + TEST_TOKEN)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(1)))
            .andExpect(jsonPath("$.content[0].id").value("notif-1"))
            .andExpect(jsonPath("$.content[0].type").value("BOOKING_CONFIRMED"))
            .andExpect(jsonPath("$.content[0].title").value("Booking Confirmed"))
            .andExpect(jsonPath("$.content[0].isRead").value(false));
    }

    @Test
    void getMyNotifications_WithCustomPagination_ReturnsCorrectPage() throws Exception {
        when(notificationService.getUserNotifications(eq(TEST_USER_ID), any(PageRequest.class)))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(1, 10), 0));

        mockMvc.perform(get("/api/notifications/me")
                .header("Authorization", "Bearer " + TEST_TOKEN)
                .param("page", "1")
                .param("size", "10")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    void getMyNotifications_WithoutAuth_Returns401() throws Exception {
        mockMvc.perform(get("/api/notifications/me")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isUnauthorized());
    }

    // ==================== GET /api/notifications/me/unread-count ====================

    @Test
    void getUnreadCount_ReturnsCount() throws Exception {
        when(notificationService.getUnreadCount(TEST_USER_ID)).thenReturn(5L);

        mockMvc.perform(get("/api/notifications/me/unread-count")
                .header("Authorization", "Bearer " + TEST_TOKEN)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.count").value(5));
    }

    @Test
    void getUnreadCount_WhenZero_ReturnsZero() throws Exception {
        when(notificationService.getUnreadCount(TEST_USER_ID)).thenReturn(0L);

        mockMvc.perform(get("/api/notifications/me/unread-count")
                .header("Authorization", "Bearer " + TEST_TOKEN)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.count").value(0));
    }

    // ==================== PATCH /api/notifications/{id}/read ====================

    @Test
    void markAsRead_WhenNotificationExists_Returns200() throws Exception {
        doNothing().when(notificationService).markAsRead("notif-1", TEST_USER_ID);

        mockMvc.perform(patch("/api/notifications/notif-1/read")
                .header("Authorization", "Bearer " + TEST_TOKEN)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk());
    }

    @Test
    void markAsRead_WhenNotificationNotFound_Returns404() throws Exception {
        doThrow(new ResourceNotFoundException("Notification not found: non-existent"))
            .when(notificationService).markAsRead("non-existent", TEST_USER_ID);

        mockMvc.perform(patch("/api/notifications/non-existent/read")
                .header("Authorization", "Bearer " + TEST_TOKEN)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound());
    }

    @Test
    void markAsRead_WhenBelongsToDifferentUser_Returns403() throws Exception {
        doThrow(new AccessDeniedException("Access denied"))
            .when(notificationService).markAsRead("notif-1", TEST_USER_ID);

        mockMvc.perform(patch("/api/notifications/notif-1/read")
                .header("Authorization", "Bearer " + TEST_TOKEN)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isForbidden());
    }

    // ==================== POST /api/notifications/read-all ====================

    @Test
    void markAllAsRead_Returns204() throws Exception {
        doNothing().when(notificationService).markAllAsRead(TEST_USER_ID);

        mockMvc.perform(post("/api/notifications/read-all")
                .header("Authorization", "Bearer " + TEST_TOKEN)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());
    }

    @Test
    void markAllAsRead_WithoutAuth_Returns401() throws Exception {
        mockMvc.perform(post("/api/notifications/read-all")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isUnauthorized());
    }
}
