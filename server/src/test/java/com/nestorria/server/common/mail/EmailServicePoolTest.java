package com.nestorria.server.common.mail;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@SpringBootTest
class EmailServicePoolTest {

    @Autowired
    @Qualifier("emailTaskExecutor")
    private ThreadPoolTaskExecutor emailExecutor;

    @Test
    void emailExecutor_IsInjectedIntoContext() {
        assertThat(emailExecutor).isNotNull();
        assertThat(emailExecutor.getThreadPoolExecutor()).isNotNull();
    }

    @Test
    void emailExecutor_ThreadNamePrefix_IsEmail() {
        assertThat(emailExecutor.getThreadNamePrefix()).isEqualTo("email-");
    }

    @Test
    void emailExecutor_MaxPoolSizeIs4() {
        assertThat(emailExecutor.getMaxPoolSize()).isEqualTo(4);
    }

    @Test
    void emailExecutor_QueueCapacityIs100() {
        var queue = emailExecutor.getThreadPoolExecutor().getQueue();
        assertThat(queue.size() + queue.remainingCapacity()).isEqualTo(100);
    }
}
