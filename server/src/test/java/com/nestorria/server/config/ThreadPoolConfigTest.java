package com.nestorria.server.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ThreadPoolConfigTest {

    @Autowired
    @Qualifier("emailTaskExecutor")
    private Executor emailExecutor;

    @Autowired
    @Qualifier("notificationTaskExecutor")
    private Executor notificationExecutor;

    @Test
    void emailExecutor_IsThreadPoolTaskExecutor() {
        assertThat(emailExecutor)
            .isInstanceOf(org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor.class);
    }

    @Test
    void emailExecutor_HasCorrectPrefix() {
        org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor tpte =
            (org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor) emailExecutor;
        assertThat(tpte.getThreadNamePrefix()).isEqualTo("email-");
    }

    @Test
    void emailExecutor_HasCallerRunsPolicy() {
        org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor tpte =
            (org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor) emailExecutor;
        assertThat(tpte.getThreadPoolExecutor().getRejectedExecutionHandler())
            .isInstanceOf(ThreadPoolExecutor.CallerRunsPolicy.class);
    }

    @Test
    void emailExecutor_CorePoolSizeIs2() {
        org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor tpte =
            (org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor) emailExecutor;
        assertThat(tpte.getCorePoolSize()).isEqualTo(2);
    }

    @Test
    void notificationExecutor_IsThreadPoolTaskExecutor() {
        assertThat(notificationExecutor)
            .isInstanceOf(org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor.class);
    }

    @Test
    void notificationExecutor_HasCorrectPrefix() {
        org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor tpte =
            (org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor) notificationExecutor;
        assertThat(tpte.getThreadNamePrefix()).isEqualTo("notif-");
    }

    @Test
    void notificationExecutor_HasCallerRunsPolicy() {
        org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor tpte =
            (org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor) notificationExecutor;
        assertThat(tpte.getThreadPoolExecutor().getRejectedExecutionHandler())
            .isInstanceOf(ThreadPoolExecutor.CallerRunsPolicy.class);
    }

    @Test
    void emailAndNotificationExecutors_AreDifferentInstances() {
        assertThat(emailExecutor).isNotSameAs(notificationExecutor);
    }
}
