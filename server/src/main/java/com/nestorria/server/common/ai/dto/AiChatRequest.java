package com.nestorria.server.common.ai.dto;

import jakarta.validation.constraints.NotBlank;

public record AiChatRequest(
    @NotBlank String message,
    String userId,
    String conversationId
) {}
