package com.nestorria.server.common.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AiChatRequest(
    @NotBlank @Size(max = 2000) String message,
    @Size(max = 64) String userId,
    @Size(max = 64) String conversationId
) {}
