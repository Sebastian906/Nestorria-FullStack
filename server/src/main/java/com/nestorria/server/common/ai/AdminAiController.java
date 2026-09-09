package com.nestorria.server.common.ai;

import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.nestorria.server.common.ai.dto.AdminAiStatusResponse;
import com.nestorria.server.common.ai.dto.AdminChatMetricsResponse;
import com.nestorria.server.common.ai.dto.AdminModelsResponse;
import com.nestorria.server.common.ai.dto.AdminRagDocumentsResponse;
import com.nestorria.server.common.ai.dto.CompareVersionsResponse;
import com.nestorria.server.common.ai.dto.ModelVersionsResponse;
import com.nestorria.server.common.ai.dto.PromoteRollbackResponse;
import com.nestorria.server.common.ai.dto.TrainingResponse;
import com.nestorria.server.common.ai.dto.VersionInfoResponse;
import com.nestorria.server.modules.user.UserRepository;
import com.nestorria.server.modules.user.UserRole;

@RestController
@RequestMapping("/api/ai/admin")
public class AdminAiController {

    private final AdminAiService adminAiService;
    private final UserRepository userRepository;

    public AdminAiController(AdminAiService adminAiService, UserRepository userRepository) {
        this.adminAiService = adminAiService;
        this.userRepository = userRepository;
    }

    private static final Set<UserRole> READ_ROLES = Set.of(
        UserRole.AGENCY_OWNER, UserRole.ADMINISTRATOR);

    private static final Set<UserRole> WRITE_ROLES = Set.of(
            UserRole.ADMINISTRATOR);

    // Verifica que el usuario autenticado tenga el rol correspondiente.
    private void requireRole(Set<UserRole> allowed) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No autenticado");
        }
        String userId = jwt.getSubject();
        var user = userRepository.findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Usuario no encontrado"));
        if (!allowed.contains(user.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acceso denegado");
        }
    }

    @GetMapping("/models")
    public ResponseEntity<AdminModelsResponse> getModels() {
        requireRole(READ_ROLES);
        return ResponseEntity.ok(adminAiService.getModels());
    }

    @PostMapping("/models/{modelName}/train")
    public ResponseEntity<TrainingResponse> triggerTraining(@PathVariable String modelName) {
        requireRole(WRITE_ROLES);
        return ResponseEntity.ok(adminAiService.triggerTraining(modelName));
    }

    @GetMapping("/rag/documents")
    public ResponseEntity<AdminRagDocumentsResponse> getDocuments() {
        requireRole(READ_ROLES);
        return ResponseEntity.ok(adminAiService.getDocuments());
    }

    @DeleteMapping("/rag/documents/{documentId}")
    public ResponseEntity<Void> deleteDocument(@PathVariable String documentId) {
        requireRole(WRITE_ROLES);
        adminAiService.deleteDocument(documentId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/chat/metrics")
    public ResponseEntity<AdminChatMetricsResponse> getChatMetrics() {
        requireRole(READ_ROLES);
        return ResponseEntity.ok(adminAiService.getChatMetrics());
    }

    @GetMapping("/status")
    public ResponseEntity<AdminAiStatusResponse> getStatus() {
        requireRole(READ_ROLES);
        return ResponseEntity.ok(adminAiService.getStatus());
    }

    @GetMapping("/models/{modelName}/versions")
    public ResponseEntity<ModelVersionsResponse> getModelVersions(@PathVariable String modelName) {
        requireRole(WRITE_ROLES);
        return ResponseEntity.ok(adminAiService.getModelVersions(modelName));
    }

    @GetMapping("/models/{modelName}/active")
    public ResponseEntity<VersionInfoResponse> getActiveVersion(@PathVariable String modelName) {
        requireRole(WRITE_ROLES);
        return ResponseEntity.ok(adminAiService.getActiveVersion(modelName));
    }

    @PostMapping("/models/{modelName}/promote/{version}")
    public ResponseEntity<PromoteRollbackResponse> promoteModel(
            @PathVariable String modelName, @PathVariable String version) {
        requireRole(WRITE_ROLES);
        return ResponseEntity.ok(adminAiService.promoteModel(modelName, version));
    }

    @PostMapping("/models/{modelName}/rollback/{version}")
    public ResponseEntity<PromoteRollbackResponse> rollbackModel(
            @PathVariable String modelName, @PathVariable String version) {
        requireRole(WRITE_ROLES);
        return ResponseEntity.ok(adminAiService.rollbackModel(modelName, version));
    }

    @GetMapping("/models/{modelName}/compare")
    public ResponseEntity<CompareVersionsResponse> compareVersions(
            @PathVariable String modelName,
            @RequestParam String v1,
            @RequestParam String v2) {
        requireRole(WRITE_ROLES);
        return ResponseEntity.ok(adminAiService.compareVersions(modelName, v1, v2));
    }
}
