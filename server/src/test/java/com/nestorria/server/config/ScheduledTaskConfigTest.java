package com.nestorria.server.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@SpringBootTest
class ScheduledTaskConfigTest {

    @Autowired
    private TaskScheduler taskScheduler;

    @Test
    void scheduler_IsThreadPoolTaskScheduler() {
        assertThat(taskScheduler).isInstanceOf(ThreadPoolTaskScheduler.class);
    }

    @Test
    void scheduler_PoolSizeIs2() {
        ThreadPoolTaskScheduler scheduler = (ThreadPoolTaskScheduler) taskScheduler;
        assertThat(scheduler.getPoolSize()).isEqualTo(2);
    }

    @Test
    void scheduler_ThreadPrefixIsSched() {
        ThreadPoolTaskScheduler scheduler = (ThreadPoolTaskScheduler) taskScheduler;
        assertThat(scheduler.getThreadNamePrefix()).isEqualTo("sched-");
    }
}
