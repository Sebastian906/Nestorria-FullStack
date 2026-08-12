package com.nestorria.server.common.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.annotation.Async;
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

    @Test
    void notificationHandler_UsesNotificationTaskExecutor() throws Exception {
        Method method = NotificationEventHandler.class.getMethod(
            "handleNotificationEvent", NotificationEvent.class);
        Async async = method.getAnnotation(Async.class);
        assertThat(async).isNotNull();
        assertThat(async.value()).isEqualTo("notificationTaskExecutor");
    }

    @Test
    void invoiceIssuedHandler_UsesEmailTaskExecutor() throws Exception {
        Method method = InvoiceIssuedEventHandler.class.getMethod(
            "handleInvoiceIssued", InvoiceIssuedEvent.class);
        Async async = method.getAnnotation(Async.class);
        assertThat(async).isNotNull();
        assertThat(async.value()).isEqualTo("emailTaskExecutor");
    }

    @Test
    void invoicePaidHandler_UsesEmailTaskExecutor() throws Exception {
        Method method = InvoicePaidEventHandler.class.getMethod(
            "handleInvoicePaid", InvoicePaidEvent.class);
        Async async = method.getAnnotation(Async.class);
        assertThat(async).isNotNull();
        assertThat(async.value()).isEqualTo("emailTaskExecutor");
    }
}
