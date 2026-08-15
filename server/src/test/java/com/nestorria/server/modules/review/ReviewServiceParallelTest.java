package com.nestorria.server.modules.review;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.nestorria.server.modules.review.ReviewService.RatingAggregate;

@ExtendWith(MockitoExtension.class)
class ReviewServiceParallelTest {

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private com.nestorria.server.modules.user.UserRepository userRepository;

    @Mock
    private com.nestorria.server.modules.properties.PropertyRepository propertyRepository;

    @Mock
    private Executor notificationTaskExecutor;

    @InjectMocks
    private ReviewService reviewService;

    @Test
    void getAverageRatings_emptyList_returnsEmptyMap() {
        Map<String, RatingAggregate> result = reviewService.getAverageRatings(List.of());
        assertTrue(result.isEmpty());
    }

    @Test
    void getAverageRatings_nullList_returnsEmptyMap() {
        Map<String, RatingAggregate> result = reviewService.getAverageRatings(null);
        assertTrue(result.isEmpty());
    }

    @Test
    void getAverageRatings_smallList_usesSequential() {
        // Simular 50 propertyIds (menos de 100 → usa secuencial)
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            ids.add("property-" + i);
        }

        // Mock: la query SQL retorna resultados
        Object[][] mockData = {
            {"property-0", 4.5, 10L},
            {"property-1", 3.8, 5L}
        };
        org.mockito.Mockito.when(reviewRepository.findRatingAggregatesByPropertyIds(ids))
            .thenReturn(List.of(mockData));

        Map<String, RatingAggregate> result = reviewService.getAverageRatings(ids);

        assertNotNull(result);
        // Verificar que procesó los resultados del mock
        assertEquals(2, result.size());
    }

    @Test
    void getAverageRatings_largeList_usesParallel() {
        // Simular 200 propertyIds (más de 100 → usa paralelo)
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            ids.add("property-" + i);
        }

        // Mock: la query SQL retorna resultados para cada chunk
        // Dividirá en 2 chunks de 100
        Object[][] mockData1 = new Object[100][3];
        Object[][] mockData2 = new Object[100][3];
        for (int i = 0; i < 100; i++) {
            mockData1[i] = new Object[]{"property-" + i, 4.0 + (i % 5), (long) (i + 1)};
            mockData2[i] = new Object[]{"property-" + (i + 100), 3.0 + (i % 5), (long) (i + 1)};
        }

        // El mock debe funcionar para cualquier sublista
        org.mockito.Mockito.when(reviewRepository.findRatingAggregatesByPropertyIds(
            org.mockito.ArgumentMatchers.anyList()))
            .thenReturn(List.of(mockData1))
            .thenReturn(List.of(mockData2));

        Map<String, RatingAggregate> result = reviewService.getAverageRatings(ids);

        assertNotNull(result);
        assertFalse(result.isEmpty());
    }
}
