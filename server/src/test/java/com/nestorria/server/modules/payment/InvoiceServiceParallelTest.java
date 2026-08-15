package com.nestorria.server.modules.payment;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.nestorria.server.common.config.AppProperties;
import com.nestorria.server.common.mail.EmailService;
import com.nestorria.server.common.outbox.OutboxEventService;
import com.nestorria.server.modules.booking.Booking;
import com.nestorria.server.modules.properties.Property;
import com.nestorria.server.modules.user.User;

@ExtendWith(MockitoExtension.class)
class InvoiceServiceParallelTest {

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private InvoiceSequenceRepository invoiceSequenceRepository;

    @Mock
    private AppProperties appProperties;

    @Mock
    private EmailService emailService;

    @Mock
    private OutboxEventService outboxEventService;

    @Mock
    private Executor outboxTaskExecutor;

    @Mock
    private InvoiceTransactionWorker invoiceWorker;

    @InjectMocks
    private InvoiceService invoiceService;

    @Test
    void markAsOverdue_noInvoices_doesNothing() {
        when(invoiceRepository.findOverdueInvoicesWithBooking(
            any(InvoiceStatus.class), any(LocalDate.class)))
            .thenReturn(List.of());

        assertDoesNotThrow(() -> invoiceService.markAsOverdue());

        verify(invoiceRepository).findOverdueInvoicesWithBooking(
            any(InvoiceStatus.class), any(LocalDate.class));
        verify(invoiceWorker, never()).processOverdueInvoice(anyString());
    }

    @Test
    void markAsOverdue_withInvoices_delegatesToWorker() {
        Booking booking = createMockBooking();
        Invoice invoice1 = new Invoice(booking, "INV-001", LocalDate.now(),
            LocalDate.now().plusDays(5), 10000L, 1000L, 11000L, "USD");
        invoice1.setId("inv-1");
        Invoice invoice2 = new Invoice(booking, "INV-002", LocalDate.now(),
            LocalDate.now().plusDays(5), 20000L, 2000L, 22000L, "USD");
        invoice2.setId("inv-2");

        when(invoiceRepository.findOverdueInvoicesWithBooking(
            any(InvoiceStatus.class), any(LocalDate.class)))
            .thenReturn(List.of(invoice1, invoice2));

        assertDoesNotThrow(() -> invoiceService.markAsOverdue());

        verify(invoiceWorker).processOverdueInvoice("inv-1");
        verify(invoiceWorker).processOverdueInvoice("inv-2");
    }

    @Test
    void sendDueDateReminders_noInvoices_doesNothing() {
        when(invoiceRepository.findInvoicesDueOnDateWithBooking(
            any(InvoiceStatus.class), any(LocalDate.class)))
            .thenReturn(List.of());

        assertDoesNotThrow(() -> invoiceService.sendDueDateReminders());

        verify(invoiceRepository).findInvoicesDueOnDateWithBooking(
            any(InvoiceStatus.class), any(LocalDate.class));
        verify(invoiceWorker, never()).processReminder(anyString());
    }

    @Test
    void sendDueDateReminders_withInvoices_delegatesToWorker() {
        Booking booking = createMockBooking();
        Invoice invoice = new Invoice(booking, "INV-003", LocalDate.now(),
            LocalDate.now().plusDays(1), 15000L, 1500L, 16500L, "USD");
        invoice.setId("inv-3");

        when(invoiceRepository.findInvoicesDueOnDateWithBooking(
            any(InvoiceStatus.class), any(LocalDate.class)))
            .thenReturn(List.of(invoice));

        assertDoesNotThrow(() -> invoiceService.sendDueDateReminders());

        verify(invoiceWorker).processReminder("inv-3");
    }

    @Test
    void calculateLateFee_computesCorrectly() {
        when(appProperties.invoice()).thenReturn(
            new AppProperties.InvoiceProperties(0.0, 0, 0.05));

        Booking booking = createMockBooking();
        Invoice invoice = new Invoice(
            booking, "INV-2026-00001",
            LocalDate.now(), LocalDate.now().plusDays(5),
            10000L, 1000L, 11000L, "USD"
        );

        long lateFee = invoiceService.calculateLateFee(invoice);

        assertEquals(550L, lateFee);
    }

    @Test
    void buildInvoiceEmailData_buildsCorrectly() {
        Booking booking = createMockBooking();
        Invoice invoice = new Invoice(
            booking, "INV-2026-00001",
            LocalDate.now(), LocalDate.now().plusDays(5),
            10000L, 1000L, 11000L, "USD"
        );
        invoice.setStatus(InvoiceStatus.PENDING);

        var emailData = invoiceService.buildInvoiceEmailData(invoice);

        assertEquals("INV-2026-00001", emailData.invoiceNumber());
        assertEquals(10000L, emailData.subtotal());
        assertEquals(1000L, emailData.tax());
        assertEquals(11000L, emailData.total());
        assertEquals("USD", emailData.currency());
    }

    private Booking createMockBooking() {
        User user = mock(User.class);
        when(user.getId()).thenReturn("user-1");
        when(user.getEmail()).thenReturn("test@example.com");

        Property property = mock(Property.class);
        when(property.getAddress()).thenReturn("123 Main St");

        com.nestorria.server.modules.agency.Agency agency = 
            mock(com.nestorria.server.modules.agency.Agency.class);
        when(agency.getName()).thenReturn("Test Agency");
        when(agency.getOwner()).thenReturn(user);
        when(property.getAgency()).thenReturn(agency);

        Booking booking = mock(Booking.class);
        when(booking.getUser()).thenReturn(user);
        when(booking.getProperty()).thenReturn(property);
        when(booking.getAgency()).thenReturn(agency);
        when(booking.getTotalPrice()).thenReturn(10000L);
        return booking;
    }
}
