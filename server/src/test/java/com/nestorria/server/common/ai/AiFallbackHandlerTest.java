package com.nestorria.server.common.ai;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nestorria.server.common.ai.dto.AiHealthResponse;
import com.nestorria.server.common.ai.dto.AiPredictionRequest;
import com.nestorria.server.modules.properties.PropertyRecommendationService;
import com.nestorria.server.modules.properties.dto.PropertySummaryResponse;

class AiFallbackHandlerTest {

    private AiFallbackHandler handler;
    private PropertyRecommendationService recommendationService;

    @BeforeEach
    void setUp() {
        recommendationService = mock(PropertyRecommendationService.class);
        handler = new AiFallbackHandler(recommendationService);
    }

    @Test
    void healthFallback_returnsDegraded() {
        AiHealthResponse response = handler.healthFallback();
        assertEquals("degraded", response.status());
        assertEquals("unavailable", response.aiService());
    }

    @Test
    void pricePredictionFallback_throwsException() {
        AiPredictionRequest request = AiPredictionRequest.forPrice("p1", Map.of());
        assertThrows(AiServiceException.class,
            () -> handler.pricePredictionFallback(request));
    }

    @Test
    void recommendationsFallback_delegatesToService() {
        List<PropertySummaryResponse> expected = List.of();
        when(recommendationService.getRecommendations("user1", 10)).thenReturn(expected);

        List<PropertySummaryResponse> result = handler.recommendationsFallback("user1", 10);

        assertEquals(expected, result);
        verify(recommendationService).getRecommendations("user1", 10);
    }
}
