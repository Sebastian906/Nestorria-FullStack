package com.nestorria.server.modules.favorite;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nestorria.server.common.exception.ResourceNotFoundException;
import com.nestorria.server.modules.favorite.dto.FavoriteResponse;
import com.nestorria.server.modules.properties.PropertyRepository;
import com.nestorria.server.modules.user.UserRepository;

@Service
public class FavoriteService {

    private final FavoriteRepository favoriteRepository;
    private final UserRepository userRepository;
    private final PropertyRepository propertyRepository;

    public FavoriteService(
            FavoriteRepository favoriteRepository,
            UserRepository userRepository,
            PropertyRepository propertyRepository) {
        this.favoriteRepository = favoriteRepository;
        this.userRepository = userRepository;
        this.propertyRepository = propertyRepository;
    }

    /**
     * Toggle de favorito: no existía → insert (true); existía → delete (false).
     * Los 2 lookups iniciales son deliberados: preservan el 404 cuando el
     * recurso no existe. No es posible reducirlos a "1 query" sin sacrificar
     * esa semántica (criterio de aceptación revisado).
     * El write es 1 sola operación atómica (INSERT ... ON CONFLICT DO NOTHING):
     * - 1 fila afectada → insertamos → true.
     * - 0 filas → el favorito ya existe o un request concurrente lo creó
     *   justo ahora → completar el toggle eliminándolo → false.
     * El upsert no lanza excepción, por lo que el DELETE posterior corre en
     * una transacción sana (nada de rollback-only).
     */
    @Transactional
    public boolean toggleFavorite(String userId, String propertyId) {
        userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado: " + userId));

        propertyRepository.findById(propertyId)
            .orElseThrow(() -> new ResourceNotFoundException("Propiedad no encontrada: " + propertyId));

        boolean inserted = favoriteRepository.insertIfAbsent(
            UUID.randomUUID().toString(), userId, propertyId, Instant.now()) == 1;

        if (inserted) {
            return true;
        }
        favoriteRepository.deleteByUserIdAndPropertyId(userId, propertyId);
        return false;
    }

    @Transactional(readOnly = true)
    public List<FavoriteResponse> getUserFavorites(String userId) {
        return favoriteRepository.findByUserIdOrderByCreatedAtDesc(userId)
            .stream()
            .map(FavoriteResponse::fromEntity)
            .toList();
    }

    @Transactional
    public void removeFavorite(String favoriteId, String userId) {
        Favorite favorite = favoriteRepository.findById(favoriteId)
            .orElseThrow(() -> new ResourceNotFoundException("Favorito no encontrado: " + favoriteId));

        if (!favorite.getUser().getId().equals(userId)) {
            throw new org.springframework.security.access.AccessDeniedException(
                "No tienes permiso para eliminar este favorito"
            );
        }

        favoriteRepository.delete(favorite);
    }
}
