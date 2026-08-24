package com.nestorria.server.common.ai;

/**
 * Excepción lanzada cuando ai-service falla y no hay fallback disponible.
 * Se diferencia de las excepciones de infraestructura (timeout, connection)
 * para que el circuit breaker pueda distinguir entre errores de negocio
 * (no retry) y errores de infraestructura (retry).
 */
public class AiServiceException extends RuntimeException {
    public AiServiceException(String message) {
        super(message);
    }

    public AiServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
