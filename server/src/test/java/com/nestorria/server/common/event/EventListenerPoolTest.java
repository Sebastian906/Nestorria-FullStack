package com.nestorria.server.common.event;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@SpringBootTest
class EventListenerPoolTest {

    @Autowired
    private NotificationEventListener notificationListener;

    @Autowired
    private InvoiceIssuedEventListener invoiceIssuedListener;

    @Autowired
    private InvoicePaidEventListener invoicePaidListener;

    @Autowired
    @Qualifier("emailTaskExecutor")
    private ThreadPoolTaskExecutor emailExecutor;

    @Autowired
    @Qualifier("notificationTaskExecutor")
    private ThreadPoolTaskExecutor notificationExecutor;

    @Test
    void notificationListener_Exists() {
        assertThat(notificationListener).isNotNull();
    }

    @Test
    void invoiceIssuedListener_Exists() {
        assertThat(invoiceIssuedListener).isNotNull();
    }

    @Test
    void invoicePaidListener_Exists() {
        assertThat(invoicePaidListener).isNotNull();
    }

    @Test
    void emailExecutor_IsDifferentFromNotificationExecutor() {
        assertThat(emailExecutor).isNotSameAs(notificationExecutor);
    }

    @Test
    void emailExecutor_PrefixIsEmail() {
        assertThat(emailExecutor.getThreadNamePrefix()).isEqualTo("email-");
    }

    @Test
    void notificationExecutor_PrefixIsNotif() {
        assertThat(notificationExecutor.getThreadNamePrefix()).isEqualTo("notif-");
    }
}
