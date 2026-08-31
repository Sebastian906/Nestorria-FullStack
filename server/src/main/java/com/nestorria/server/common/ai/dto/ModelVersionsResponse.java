package com.nestorria.server.common.ai.dto;

import java.util.List;

public record ModelVersionsResponse(String model, List<VersionInfoResponse> versions) {
    public ModelVersionsResponse { }
}
