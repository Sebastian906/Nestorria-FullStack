package com.nestorria.server.common.ai.dto;

import java.util.List;

public record AdminModelsResponse(List<ModelInfo> models) {
    public AdminModelsResponse { }
}
