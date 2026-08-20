package com.nestorria.server.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import com.nestorria.server.common.config.MdcTaskDecorator;

@Configuration
public class ThreadPoolConfig {

    /**
     * Pool para emails: I/O-bound, lento (SMTP), tolerante a cola.
     * CallerRunsPolicy: si la cola se llena, el caller ejecuta
     * (aceptable porque el caller es un listener async, no un HTTP thread).
     */
    @Bean("emailTaskExecutor")
    public Executor emailTaskExecutor(MdcTaskDecorator mdcTaskDecorator) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(200);  // ← 100 → 200 para reintentos
        executor.setThreadNamePrefix("email-");
        executor.setTaskDecorator(mdcTaskDecorator);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }

    /**
     * Pool para notificaciones: DB-bound, rápido, muchas tareas.
     * CallerRunsPolicy: preferible bloquear momentáneamente a perder notificaciones.
     */
    @Bean("notificationTaskExecutor")
    public Executor notificationTaskExecutor(MdcTaskDecorator mdcTaskDecorator) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(400);  // ← 200 → 400 para reintentos
        executor.setThreadNamePrefix("notif-");
        executor.setTaskDecorator(mdcTaskDecorator);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    /**
     * Pool para subidas de imágenes a Cloudinary: I/O-bound, HTTP calls.
     * AbortPolicy: si la cola se llena, lanza excepción — el caller (HTTP thread)
     * puede manejar el error y notificar al usuario. CallerRunsPolicy bloquearía
     * el HTTP thread, que es exactamente lo que queremos evitar.
     */
    @Bean("imageUploadTaskExecutor")
    public Executor imageUploadTaskExecutor(MdcTaskDecorator mdcTaskDecorator) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(3);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("img-");
        executor.setTaskDecorator(mdcTaskDecorator);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }

    /**
     * Pool para procesamiento de eventos outbox: I/O-bound (DB + email + WebSocket).
     * CallerRunsPolicy: si la cola se llena, el scheduler thread procesa el evento
     * (fallback seguro — degrada a secuencial, same que antes).
     */
    @Bean("outboxTaskExecutor")
    public Executor outboxTaskExecutor(MdcTaskDecorator mdcTaskDecorator) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("outbox-");
        executor.setTaskDecorator(mdcTaskDecorator);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }

    /**
     * Scheduler para @Scheduled (outbox poll, lecturas, etc.).
     * Reemplaza el autoconfig de Boot con el mismo pool (2, prefijo "sched-"),
     * pero decorado para propagar el instanceId al MDC de los threads del scheduler.
     */
    @Bean
    public TaskScheduler taskScheduler(MdcTaskDecorator mdcTaskDecorator) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("sched-");
        scheduler.setTaskDecorator(mdcTaskDecorator);
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);
        scheduler.initialize();
        return scheduler;
    }
}
