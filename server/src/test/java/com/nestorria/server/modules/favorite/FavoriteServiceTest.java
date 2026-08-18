package com.nestorria.server.modules.favorite;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import com.nestorria.server.common.exception.ResourceNotFoundException;
import com.nestorria.server.modules.agency.Agency;
import com.nestorria.server.modules.properties.Property;
import com.nestorria.server.modules.properties.PropertyRepository;
import com.nestorria.server.modules.properties.PropertyType;
import com.nestorria.server.modules.properties.embeddable.FacilityDetails;
import com.nestorria.server.modules.properties.embeddable.PriceDetails;
import com.nestorria.server.modules.properties.embeddable.PropertyLocation;
import com.nestorria.server.modules.user.User;
import com.nestorria.server.modules.user.UserRepository;

@ExtendWith(MockitoExtension.class)
class FavoriteServiceTest {

    @Mock
    private FavoriteRepository favoriteRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PropertyRepository propertyRepository;

    private FavoriteService favoriteService;

    private static final String USER_ID = "u1";
    private static final String PROPERTY_ID = "p1";

    @BeforeEach
    void setUp() {
        favoriteService = new FavoriteService(favoriteRepository, userRepository, propertyRepository);
    }

    private User user() {
        return new User(USER_ID, "User", "u@example.com", "img");
    }

    private Property property() {
        Agency agency = new Agency("Agency", "Addr", "Contact", "a@example.com", "City", user());
        return new Property(agency, "Title", "Desc", "City", "Country", "Addr",
            100, PropertyType.APARTMENT, new PriceDetails(), new FacilityDetails(),
            List.of(), new PropertyLocation(null, null, null, null));
    }

    @Test
    void noExistingFavorite_savesAndReturnsTrue() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
        when(propertyRepository.findById(PROPERTY_ID)).thenReturn(Optional.of(property()));
        when(favoriteRepository.findByUserIdAndPropertyId(USER_ID, PROPERTY_ID)).thenReturn(Optional.empty());

        boolean favorited = favoriteService.toggleFavorite(USER_ID, PROPERTY_ID);

        assertTrue(favorited);
        verify(favoriteRepository).save(org.mockito.ArgumentMatchers.any(Favorite.class));
        verify(favoriteRepository, never()).deleteByUserIdAndPropertyId(USER_ID, PROPERTY_ID);
    }

    @Test
    void existingFavorite_deletesAndReturnsFalse() {
        Favorite existing = new Favorite(user(), property());
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
        when(propertyRepository.findById(PROPERTY_ID)).thenReturn(Optional.of(property()));
        when(favoriteRepository.findByUserIdAndPropertyId(USER_ID, PROPERTY_ID))
            .thenReturn(Optional.of(existing));

        boolean favorited = favoriteService.toggleFavorite(USER_ID, PROPERTY_ID);

        assertFalse(favorited);
        verify(favoriteRepository).delete(existing);
        verify(favoriteRepository, never()).save(org.mockito.ArgumentMatchers.any(Favorite.class));
    }

    @Test
    void concurrentInsert_lostRaceCompletesToggle() {
        // Dos requests simultáneos: ambos vieron "no existe" y ambos insertaron.
        // El unique constraint (user_id, property_id) dispara la excepción en el segundo.
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
        when(propertyRepository.findById(PROPERTY_ID)).thenReturn(Optional.of(property()));
        when(favoriteRepository.findByUserIdAndPropertyId(USER_ID, PROPERTY_ID)).thenReturn(Optional.empty());
        when(favoriteRepository.save(org.mockito.ArgumentMatchers.any(Favorite.class)))
            .thenThrow(new DataIntegrityViolationException("duplicate key"));

        boolean favorited = favoriteService.toggleFavorite(USER_ID, PROPERTY_ID);

        assertFalse(favorited);
        verify(favoriteRepository).deleteByUserIdAndPropertyId(USER_ID, PROPERTY_ID);
    }

    @Test
    void userNotFound_throwsResourceNotFound() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
            () -> favoriteService.toggleFavorite(USER_ID, PROPERTY_ID));
    }

    @Test
    void propertyNotFound_throwsResourceNotFound() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
        when(propertyRepository.findById(PROPERTY_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
            () -> favoriteService.toggleFavorite(USER_ID, PROPERTY_ID));
    }
}
