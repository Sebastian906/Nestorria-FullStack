package com.nestorria.server.common.config;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;
import org.springframework.stereotype.Component;

/**
 * Propaga el instanceId al MDC de los hilos de executors y del scheduler.
 * El filtro de requests solo cubre el hilo HTTP; sin este decorator, los logs
 * de tareas asíncronas (outbox, email, notificaciones, imágenes, @Scheduled)
 * mostrarían el MDC vacío.
 */
@Component
public class MdcTaskDecorator implements TaskDecorator {

    private final InstanceIdProvider instanceIdProvider;

    public MdcTaskDecorator(InstanceIdProvider instanceIdProvider) {
        this.instanceIdProvider = instanceIdProvider;
    }

    @Override
    public Runnable decorate(Runnable runnable) {
        return () -> {
            MDC.put("instanceId", instanceIdProvider.get());
            try {
                runnable.run();
            } finally {
                MDC.remove("instanceId");
            }
        };
    }
}