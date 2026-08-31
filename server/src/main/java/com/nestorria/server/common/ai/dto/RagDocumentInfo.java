package com.nestorria.server.common.ai.dto;

public record RagDocumentInfo(
    String id,
    String name,
    int chunks,
    String version
) { }

