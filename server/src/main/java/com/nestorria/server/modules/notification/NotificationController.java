package com.nestorria.server.modules.notification;

import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nestorria.server.modules.notification.dto.NotificationResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/notifications")
@Tag(name = "Notifications", description = "Gestión de notificaciones in-app")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Operation(summary = "Obtener las notificaciones del usuario autenticado (paginado)")
    @ApiResponse(responseCode = "200", description = "Lista paginada de notificaciones")
    @GetMapping("/me")
    public Page<NotificationResponse> getMyNotifications(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return notificationService.getUserNotifications(
            jwt.getSubject(),
            PageRequest.of(page, size)
        );
    }

    @Operation(summary = "Obtener el conteo de notificaciones no leídas")
    @ApiResponse(responseCode = "200", description = "Conteo de notificaciones no leídas")
    @GetMapping("/me/unread-count")
    public Map<String, Long> getUnreadCount(@AuthenticationPrincipal Jwt jwt) {
        return Map.of("count", notificationService.getUnreadCount(jwt.getSubject()));
    }

    @Operation(summary = "Marcar una notificación como leída")
    @ApiResponse(responseCode = "200", description = "Notificación marcada como leída")
    @ApiResponse(responseCode = "404", description = "Notificación no encontrada")
    @ApiResponse(responseCode = "403", description = "La notificación pertenece a otro usuario")
    @PatchMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String id) {
        notificationService.markAsRead(id, jwt.getSubject());
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Marcar todas las notificaciones como leídas")
    @ApiResponse(responseCode = "204", description = "Todas las notificaciones marcadas como leídas")
    @PostMapping("/read-all")
    public ResponseEntity<Void> markAllAsRead(@AuthenticationPrincipal Jwt jwt) {
        notificationService.markAllAsRead(jwt.getSubject());
        return ResponseEntity.noContent().build();
    }
}
