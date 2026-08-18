package com.nestorria.server.modules.properties;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.nestorria.server.modules.agency.Agency;
import com.nestorria.server.modules.booking.BookingRepository;
import com.nestorria.server.modules.favorite.FavoriteRepository;
import com.nestorria.server.modules.properties.embeddable.FacilityDetails;
import com.nestorria.server.modules.properties.embeddable.PriceDetails;

@ExtendWith(MockitoExtension.class)
class PropertyRecommendationServiceTest {

    @Mock
    private PropertyRepository propertyRepository;
    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private FavoriteRepository favoriteRepository;

    private PropertyRecommendationService service;

    @BeforeEach
    void setUp() {
        service = new PropertyRecommendationService(
            propertyRepository, bookingRepository, favoriteRepository);
    }

    private Property buildProperty(
            String id, String city, PropertyType type,
            Integer salePrice, Integer rentPrice, List<String> amenities) {

        Agency agency = new Agency("Test Agency", "123 Main St",
            "555-0100", "agency@test.com", city, null);

        PriceDetails price = new PriceDetails(rentPrice, salePrice);
        FacilityDetails facilities = new FacilityDetails(2, 1, 1);

        return new Property(
            agency, "Title " + id, "Description",
            city, "Country", "Address",
            100, type, price, facilities,
            amenities != null ? amenities : List.of(), null
        );
    }

    // --- calculateSimilarity tests ---

    @Test
    void calculateSimilarity_sameCityAndType_returnsHighWeight() {
        Property a = buildProperty("1", "Madrid", PropertyType.APARTMENT,
            200000, null, List.of("pool", "gym"));
        Property b = buildProperty("2", "Madrid", PropertyType.APARTMENT,
            250000, null, List.of("pool", "parking"));

        double similarity = service.calculateSimilarity(a, b);

        // city=3 + type=2 + price=1 (30000/225000 < 0.3) + 1 amenity shared = 7
        assertTrue(similarity >= 6.0);
    }

    @Test
    void calculateSimilarity_differentCityAndType_returnsLowWeight() {
        Property a = buildProperty("1", "Madrid", PropertyType.APARTMENT,
            200000, null, List.of("pool"));
        Property b = buildProperty("2", "Barcelona", PropertyType.VILLA,
            500000, null, List.of("garden"));

        double similarity = service.calculateSimilarity(a, b);

        // No city match, no type match, no price match, no shared amenities = 0
        assertEquals(0.0, similarity);
    }

    @Test
    void calculateSimilarity_sameCityDifferentType() {
        Property a = buildProperty("1", "Madrid", PropertyType.APARTMENT,
            200000, null, List.of());
        Property b = buildProperty("2", "Madrid", PropertyType.VILLA,
            200000, null, List.of());

        double similarity = service.calculateSimilarity(a, b);

        // city=3 + type=0 + price=1 (same price) + 0 amenities = 4
        assertEquals(4.0, similarity);
    }

    @Test
    void calculateSimilarity_sharedAmenities() {
        Property a = buildProperty("1", "Madrid", PropertyType.APARTMENT,
            200000, null, List.of("pool", "gym", "parking"));
        Property b = buildProperty("2", "Madrid", PropertyType.APARTMENT,
            200000, null, List.of("pool", "gym", "garden"));

        double similarity = service.calculateSimilarity(a, b);

        // city=3 + type=2 + price=1 + 2 shared amenities = 8
        assertEquals(8.0, similarity);
    }

    @Test
    void calculateSimilarity_priceOutOfRange() {
        Property a = buildProperty("1", "Madrid", PropertyType.APARTMENT,
            100000, null, List.of());
        Property b = buildProperty("2", "Madrid", PropertyType.APARTMENT,
            500000, null, List.of());

        double similarity = service.calculateSimilarity(a, b);

        // city=3 + type=2 + price=0 (too different) = 5
        assertEquals(5.0, similarity);
    }

    @Test
    void calculateSimilarity_nullPrices() {
        Property a = buildProperty("1", "Madrid", PropertyType.APARTMENT,
            null, null, List.of());
        Property b = buildProperty("2", "Madrid", PropertyType.APARTMENT,
            null, null, List.of());

        double similarity = service.calculateSimilarity(a, b);

        // city=3 + type=2 + price=0 (null) = 5
        assertEquals(5.0, similarity);
    }

    @Test
    void calculateSimilarity_nullAmenities() {
        Property a = buildProperty("1", "Madrid", PropertyType.APARTMENT,
            200000, null, null);
        Property b = buildProperty("2", "Madrid", PropertyType.APARTMENT,
            200000, null, null);

        double similarity = service.calculateSimilarity(a, b);

        // city=3 + type=2 + price=1 + amenities=0 (null) = 6
        assertEquals(6.0, similarity);
    }

    @Test
    void calculateSimilarity_identicalProperties() {
        Property a = buildProperty("1", "Madrid", PropertyType.APARTMENT,
            200000, null, List.of("pool", "gym"));

        double similarity = service.calculateSimilarity(a, a);

        // city=3 + type=2 + price=1 + 2 shared = 8
        assertEquals(8.0, similarity);
    }

    @Test
    void calculateSimilarity_rentPriceUsedWhenSaleIsNull() {
        Property a = buildProperty("1", "Madrid", PropertyType.APARTMENT,
            null, 1500, List.of());
        Property b = buildProperty("2", "Madrid", PropertyType.APARTMENT,
            null, 1600, List.of());

        double similarity = service.calculateSimilarity(a, b);

        // city=3 + type=2 + price=1 (100/1550 < 0.3) = 6
        assertEquals(6.0, similarity);
    }
}
