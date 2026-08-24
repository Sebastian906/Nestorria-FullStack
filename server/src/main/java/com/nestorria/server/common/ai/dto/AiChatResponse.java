package com.nestorria.server.common.ai.dto;

import java.util.List;

public record AiChatResponse(
    String response,
    List<String> sources,
    String conversationId
) {}
