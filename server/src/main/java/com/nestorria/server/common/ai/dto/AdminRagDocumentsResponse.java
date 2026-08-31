package com.nestorria.server.common.ai.dto;

import java.util.List;

public record AdminRagDocumentsResponse(List<RagDocumentInfo> documents) {
    public AdminRagDocumentsResponse { }
}
