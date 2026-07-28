package com.nestorria.server.modules.favorite;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nestorria.server.modules.favorite.dto.FavoriteResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/favorites")
@Tag(name = "Favorites", description = "Gestión de propiedades favoritas")
public class FavoriteController {

    private final FavoriteService favoriteService;

    public FavoriteController(FavoriteService favoriteService) {
        this.favoriteService = favoriteService;
    }

    @Operation(summary = "Obtener las propiedades favoritas del usuario autenticado")
    @ApiResponse(responseCode = "200", description = "Lista de favoritos obtenida exitosamente")
    @GetMapping("/me")
    public List<FavoriteResponse> getMyFavorites(@AuthenticationPrincipal Jwt jwt) {
        return favoriteService.getUserFavorites(jwt.getSubject());
    }

    @Operation(summary = "Eliminar un favorito por ID")
    @ApiResponse(responseCode = "204", description = "Favorito eliminado exitosamente")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> removeFavorite(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String id) {
        favoriteService.removeFavorite(id, jwt.getSubject());
        return ResponseEntity.noContent().build();
    }
}
