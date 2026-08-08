package com.nestorria.server.config;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

@SpringBootTest
class ScheduledTaskConfigTest {

    @Autowired
    private Environment environment;

    @Test
    void schedulerPoolSize_IsConfiguredViaProperties() {
        assertThat(environment.getProperty("spring.task.scheduling.pool.size"))
            .isEqualTo("2");
    }

    @Test
    void schedulerThreadPrefix_IsConfiguredViaProperties() {
        assertThat(environment.getProperty("spring.task.scheduling.thread-name-prefix"))
            .isEqualTo("sched-");
    }
}
