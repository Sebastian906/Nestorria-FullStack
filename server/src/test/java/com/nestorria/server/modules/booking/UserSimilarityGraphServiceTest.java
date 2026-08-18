package com.nestorria.server.modules.booking;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.nestorria.server.modules.agency.Agency;
import com.nestorria.server.modules.properties.Property;
import com.nestorria.server.modules.properties.PropertyRepository;
import com.nestorria.server.modules.properties.PropertyType;
import com.nestorria.server.modules.properties.embeddable.FacilityDetails;
import com.nestorria.server.modules.properties.embeddable.PriceDetails;
import com.nestorria.server.modules.user.User;

@ExtendWith(MockitoExtension.class)
class UserSimilarityGraphServiceTest {

    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private PropertyRepository propertyRepository;

    private UserSimilarityGraphService service;

    @BeforeEach
    void setUp() {
        // self = misma instancia: en tests directos no hay proxy Spring ni caché
        service = new UserSimilarityGraphService(
            bookingRepository, propertyRepository, service);
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

        var recs = service.getCollaborativeRecommendations("user1", 10);
        assertTrue(recs.isEmpty());
    }

    @Test
    void findSimilarUsers_sharedProperty_returnsOtherUser() {
        User u1 = buildUser("user_1");
        User u2 = buildUser("user_2");
        Property p = buildProperty("prop_1");
        ReflectionTestUtils.setField(p, "id", "prop_1");
        Booking b1 = buildBooking(u1, p);
        Booking b2 = buildBooking(u2, p);

        when(propertyRepository.findByIsAvailableTrue()).thenReturn(List.of(p));
        when(bookingRepository.findAllConfirmed()).thenReturn(List.of(b1, b2));

        List<String> similar = service.findSimilarUsers("user_1", 10);

        assertTrue(similar.contains("user_2"));
    }
}
