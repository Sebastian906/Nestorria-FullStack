package com.nestorria.server.modules.booking;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.nestorria.server.modules.agency.Agency;
import com.nestorria.server.modules.properties.Property;
import com.nestorria.server.modules.properties.PropertyRepository;
import com.nestorria.server.modules.properties.PropertyType;
import com.nestorria.server.modules.properties.embeddable.FacilityDetails;
import com.nestorria.server.modules.properties.embeddable.PriceDetails;
import com.nestorria.server.modules.user.User;
import com.nestorria.server.modules.user.UserRepository;

@ExtendWith(MockitoExtension.class)
class UserSimilarityGraphServiceTest {

    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private PropertyRepository propertyRepository;
    @Mock
    private UserRepository userRepository;

    private UserSimilarityGraphService service;

    @BeforeEach
    void setUp() {
        service = new UserSimilarityGraphService(
            bookingRepository, propertyRepository, userRepository);
    }

    private User buildUser(String id) {
        return new User(id, "user" + id, id + "@test.com", "https://img.test.com/avatar.jpg");
    }

    private Property buildProperty(String id) {
        Agency agency = new Agency("Agency", "123 St", "555-0000", "a@test.com", "Bogota", null);
        PriceDetails price = new PriceDetails(null, 200000);
        FacilityDetails facilities = new FacilityDetails(2, 1, 1);
        return new Property(agency, "Title " + id, "Desc", "Bogota", "Colombia",
            "Addr", 100, PropertyType.APARTMENT, price, facilities, List.of(), null);
    }

    private Booking buildBooking(User user, Property property) {
        return new Booking(user, property, property.getAgency(),
            java.time.LocalDate.now(), java.time.LocalDate.now().plusDays(3),
            300, 2);
    }

    @Test
    void findSimilarUsers_noBookings_returnsEmpty() {
        when(propertyRepository.findByIsAvailableTrue()).thenReturn(List.of());
        when(bookingRepository.findAllConfirmed()).thenReturn(List.of());

        List<String> similar = service.findSimilarUsers("user1", 10);
        assertTrue(similar.isEmpty());
    }

    @Test
    void getCollaborativeRecommendations_noSimilarUsers_returnsEmpty() {
        when(propertyRepository.findByIsAvailableTrue()).thenReturn(List.of());
        when(bookingRepository.findAllConfirmed()).thenReturn(List.of());
        when(bookingRepository.findByUserId("user1")).thenReturn(List.of());

        var recs = service.getCollaborativeRecommendations("user1", 10);
        assertTrue(recs.isEmpty());
    }
}
