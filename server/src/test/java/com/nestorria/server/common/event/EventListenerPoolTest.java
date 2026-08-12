package com.nestorria.server.common.event;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.nestorria.server.common.outbox.handler.NotificationEventHandler;
import com.nestorria.server.common.outbox.handler.InvoiceIssuedEventHandler;
import com.nestorria.server.common.outbox.handler.InvoicePaidEventHandler;

@SpringBootTest
class EventListenerPoolTest {

    @Autowired
    private NotificationEventHandler notificationHandler;

    @Autowired
    private InvoiceIssuedEventHandler invoiceIssuedHandler;

    @Autowired
    private InvoicePaidEventHandler invoicePaidHandler;

    @Autowired
    @Qualifier("emailTaskExecutor")
    private ThreadPoolTaskExecutor emailExecutor;

    @Autowired
    @Qualifier("notificationTaskExecutor")
    private ThreadPoolTaskExecutor notificationExecutor;

    @Test
    void notificationHandler_Exists() {
        assertThat(notificationHandler).isNotNull();
    }

    @Test
    void invoiceIssuedHandler_Exists() {
        assertThat(invoiceIssuedHandler).isNotNull();
    }

    @Test
    void invoicePaidHandler_Exists() {
        assertThat(invoicePaidHandler).isNotNull();
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
