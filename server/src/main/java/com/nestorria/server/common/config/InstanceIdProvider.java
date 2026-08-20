package com.nestorria.server.common.config;

import java.util.UUID;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Identidad de instancia, resuelta UNA sola vez por instancia de aplicación.
 * Lee la propiedad {@code app.instance-id} (que codifica la precedencia
 * APP_INSTANCE_ID > HOSTNAME > random UUID) a través del Environment, de modo
 * que el valor es idéntico en health checks, logs (MDC) y executors — a
 * diferencia de inyectar {@code ${random.uuid}} con @Value en varios beans,
 * que genera un UUID distinto por cada inyección.
 */
@Component
public class InstanceIdProvider {

    private final String instanceId;

    public InstanceIdProvider(Environment environment) {
        String configured = environment.getProperty("app.instance-id");
        this.instanceId = (configured == null || configured.isBlank())
            ? UUID.randomUUID().toString()
            : configured;
    }

    public String get() {
        return instanceId;
    }
}