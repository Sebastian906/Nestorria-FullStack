package com.nestorria.server.common.ai;

import java.util.List;

/**
 * Evento SSE enviado al frontend durante streaming de chat.
 * Tipos: start (inicio de conversación), token (fragmento de respuesta),
 * end (fin de respuesta con fuentes), error (error controlado).
 */
public record AiChatStreamEvent(
    String type,
    String content,
    String conversationId,
    List<String> sources
) {
    public static AiChatStreamEvent start(String conversationId) {
        return new AiChatStreamEvent("start", null, conversationId, null);
    }

    public static AiChatStreamEvent token(String content) {
        return new AiChatStreamEvent("token", content, null, null);
    }

    public static AiChatStreamEvent end(String conversationId, List<String> sources) {
        return new AiChatStreamEvent("end", null, conversationId, sources);
    }

    public static AiChatStreamEvent error(String message) {
        return new AiChatStreamEvent("error", message, null, null);
    }
}
