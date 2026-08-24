package com.nestorria.server.common.ai.dto;

import java.math.BigDecimal;

public record AiPredictionResponse(
    BigDecimal prediction,
    BigDecimal confidence,
    String model,
    String risk
) {}
