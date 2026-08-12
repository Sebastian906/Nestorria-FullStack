package com.nestorria.server.common.outbox;

/**
 * Interfaz para handlers que procesan eventos del outbox.
 * Cada implementación maneja un tipo específico de evento.
 */
public interface EventHandler<T> {

    /** Nombre del tipo de evento que este handler procesa (debe coincidir con OutboxEvent.eventType) */
    String getEventType();

    /** Clase del payload para deserialización */
    Class<T> getPayloadClass();

    /** Procesa el evento */
    void handle(T event);
}
