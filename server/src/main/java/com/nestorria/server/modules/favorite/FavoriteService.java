package com.nestorria.server.modules.favorite;

import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nestorria.server.common.exception.ResourceNotFoundException;
import com.nestorria.server.modules.favorite.dto.FavoriteResponse;
import com.nestorria.server.modules.properties.Property;
import com.nestorria.server.modules.properties.PropertyRepository;
import com.nestorria.server.modules.user.User;
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

    @Transactional
public boolean toggleFavorite(String userId, String propertyId) {
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado: " + userId));

    Property property = propertyRepository.findById(propertyId)
        .orElseThrow(() -> new ResourceNotFoundException("Propiedad no encontrada: " + propertyId));

    return favoriteRepository.findByUserIdAndPropertyId(userId, propertyId)
        .map(existing -> {
            favoriteRepository.delete(existing);
            return false;
        })
        .orElseGet(() -> {
            try {
                favoriteRepository.save(new Favorite(user, property));
                return true;
            } catch (DataIntegrityViolationException e) {
                favoriteRepository.deleteByUserIdAndPropertyId(userId, propertyId);
                return false;
            }
        });
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
