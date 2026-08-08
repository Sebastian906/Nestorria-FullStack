package com.nestorria.server.modules.properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.nestorria.server.modules.agency.Agency;
import com.nestorria.server.modules.agency.AgencyRepository;
import com.nestorria.server.modules.properties.dto.PropertySummaryResponse;
import com.nestorria.server.modules.review.ReviewService;
import com.nestorria.server.modules.review.ReviewService.RatingAggregate;
import com.nestorria.server.modules.user.User;

@ExtendWith(MockitoExtension.class)
class PropertyServiceCacheTest {

    @Mock
    private PropertyRepository propertyRepository;

    @Mock
    private AgencyRepository agencyRepository;

    @Mock
    private com.cloudinary.Cloudinary cloudinary;

    @Mock
    private PropertyPersistenceService persistenceService;

    @Mock
    private ReviewService reviewService;

    @InjectMocks
    private PropertyService propertyService;

    private Property testProperty;

    @BeforeEach
    void setUp() {
        User owner = mock(User.class);
        when(owner.getId()).thenReturn("owner-1");
        when(owner.getImage()).thenReturn("http://img.url/avatar.jpg");

        Agency agency = mock(Agency.class);
        when(agency.getId()).thenReturn("agency-1");
        when(agency.getName()).thenReturn("Test Agency");
        when(agency.getAddress()).thenReturn("Calle Agency 1");
        when(agency.getContact()).thenReturn("123456");
        when(agency.getEmail()).thenReturn("agency@test.com");
        when(agency.getCity()).thenReturn("Madrid");
        when(agency.getOwner()).thenReturn(owner);

        testProperty = new Property(
            agency, "Test Property", "Description", "Madrid", "Spain",
            "Calle Test 123", 100,
            PropertyType.APARTMENT,
            new com.nestorria.server.modules.properties.embeddable.PriceDetails(1000, 200000),
            new com.nestorria.server.modules.properties.embeddable.FacilityDetails(2, 1, 1),
            List.of("pool"),
            new com.nestorria.server.modules.properties.embeddable.PropertyLocation(40.4, -3.7, "Centro", "28001")
        );
        testProperty.setId("prop-1");
        testProperty.setImages(List.of("http://img.url/photo1.jpg"));
    }

    @Test
    void getAllAvailable_CallsRepository() {
        when(propertyRepository.findByIsAvailableTrue()).thenReturn(List.of(testProperty));
        when(reviewService.getAverageRatings(any())).thenReturn(Map.of());

        List<PropertySummaryResponse> result = propertyService.getAllAvailable();

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(propertyRepository).findByIsAvailableTrue();
        verify(reviewService).getAverageRatings(any());
    }

    @Test
    void getAllAvailable_IncludesRatingsInResponse() {
        when(propertyRepository.findByIsAvailableTrue()).thenReturn(List.of(testProperty));
        when(reviewService.getAverageRatings(any()))
            .thenReturn(Map.of("prop-1", new RatingAggregate(4.5, 10)));

        List<PropertySummaryResponse> result = propertyService.getAllAvailable();

        assertEquals(4.5, result.getFirst().averageRating());
        assertEquals(10, result.getFirst().reviewCount());
    }

    @Test
    void getAllAvailable_HandlesNullRatings() {
        when(propertyRepository.findByIsAvailableTrue()).thenReturn(List.of(testProperty));
        when(reviewService.getAverageRatings(any())).thenReturn(Map.of());

        List<PropertySummaryResponse> result = propertyService.getAllAvailable();

        assertNull(result.getFirst().averageRating());
        assertEquals(0, result.getFirst().reviewCount());
    }
}
