package com.nestorria.server.modules.payment;

import java.time.LocalDate;
import java.time.Year;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nestorria.server.common.config.AppProperties;
import com.nestorria.server.common.event.InvoiceIssuedEvent;
import com.nestorria.server.common.event.NotificationEvent;
import com.nestorria.server.common.exception.BadRequestException;
import com.nestorria.server.common.exception.ResourceNotFoundException;
import com.nestorria.server.common.mail.EmailService;
import com.nestorria.server.common.mail.InvoiceEmailData;
import com.nestorria.server.common.outbox.OutboxEventService;
import com.nestorria.server.modules.booking.Booking;
import com.nestorria.server.modules.notification.NotificationType;
import com.nestorria.server.modules.payment.dto.InvoiceResponse;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final InvoiceSequenceRepository invoiceSequenceRepository;
    private final AppProperties appProperties;
    private final EmailService emailService;
    private final OutboxEventService outboxEventService;

    public InvoiceService(InvoiceRepository invoiceRepository,
                          InvoiceSequenceRepository invoiceSequenceRepository,
                          AppProperties appProperties,
                          EmailService emailService,
                          OutboxEventService outboxEventService) {
        this.invoiceRepository = invoiceRepository;
        this.invoiceSequenceRepository = invoiceSequenceRepository;
        this.appProperties = appProperties;
        this.emailService = emailService;
        this.outboxEventService = outboxEventService;
    }

    @Transactional
    public Invoice createBookingInvoice(Booking booking) {
        long subtotal = booking.getTotalPrice();
        long tax = (long) (subtotal * appProperties.invoice().taxRate());
        long total = subtotal + tax;

        String invoiceNumber = generateInvoiceNumber();
        LocalDate issueDate = LocalDate.now();
        LocalDate dueDate = issueDate.plusDays(appProperties.invoice().dueDays());

        Invoice invoice = new Invoice(
                booking, invoiceNumber, issueDate, dueDate,
                subtotal, tax, total, "USD");

        Invoice savedInvoice = invoiceRepository.save(invoice);

        // Evento de dominio via outbox: se persiste en la misma transacción
        outboxEventService.publish(
                new InvoiceIssuedEvent(
                    savedInvoice.getId(),
                    booking.getUser().getId()),
                "Invoice",
                savedInvoice.getId());

        log.info("Factura creada para reserva {}: {} (total: {} cents)",
                booking.getId(), invoiceNumber, total);

        return savedInvoice;
    }

    @Transactional(readOnly = true)
    public Invoice findByBookingId(String bookingId) {
        return invoiceRepository.findByBookingId(bookingId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Factura no encontrada para reserva: " + bookingId));
    }

    @Transactional(readOnly = true)
    public InvoiceResponse getInvoice(String invoiceId, String userId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Factura no encontrada: " + invoiceId));

        if (!invoice.isParty(userId)) {
            throw new org.springframework.security.access.AccessDeniedException(
                "No tienes acceso a esta factura");
        }

        return InvoiceResponse.fromEntity(invoice);
    }

    @Transactional(readOnly = true)
    public List<InvoiceResponse> getUserInvoices(String userId) {
        return invoiceRepository.findByUserIdOrderByCreatedAtDesc(userId)
            .stream()
            .map(InvoiceResponse::fromEntity)
            .toList();
    }

    @Transactional
    public InvoiceResponse cancelInvoice(String invoiceId, String userId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Factura no encontrada: " + invoiceId));

        if (!invoice.isParty(userId)) {
            throw new org.springframework.security.access.AccessDeniedException(
                "No tienes acceso a esta factura");
        }

        if (invoice.getStatus() != InvoiceStatus.PENDING) {
            throw new BadRequestException(
                "Solo se pueden cancelar facturas pendientes. Estado actual: "
                + invoice.getStatus());
        }

        invoice.setStatus(InvoiceStatus.CANCELLED);
        Invoice savedInvoice = invoiceRepository.save(invoice);

        log.info("Factura cancelada: {} por usuario {}", invoiceId, userId);

        return InvoiceResponse.fromEntity(savedInvoice);
    }

    public long calculateLateFee(Invoice invoice) {
        double lateFeePercentage = appProperties.invoice().lateFeePercentage();
        return (long) (invoice.getTotal() * lateFeePercentage);
    }

    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional
    public void markAsOverdue() {
        LocalDate today = LocalDate.now();
        List<Invoice> overdueInvoices = invoiceRepository
            .findOverdueInvoicesWithBooking(InvoiceStatus.PENDING, today);

        for (Invoice invoice : overdueInvoices) {
            long lateFee = calculateLateFee(invoice);
            invoice.setStatus(InvoiceStatus.OVERDUE);
            invoice.setLateFee(lateFee);
            invoiceRepository.save(invoice);

            emailService.sendInvoiceOverdueEmail(buildInvoiceEmailData(invoice));

            outboxEventService.publish(
            new NotificationEvent(
                invoice.getBooking().getUser().getId(),
                NotificationType.INVOICE_OVERDUE,
                NotificationType.INVOICE_OVERDUE.defaultTitle(),
                "La factura %s ha vencido. Se ha aplicado un cargo por mora de %s.".formatted(
                    invoice.getInvoiceNumber(), EmailService.formatAmount(lateFee, invoice.getCurrency())),
                "invoice",
                invoice.getId()
            ),
            "Invoice",
            invoice.getId());

            log.info("Factura marcada como vencida: {} (lateFee: {} cents)",
                invoice.getInvoiceNumber(), lateFee);
        }

        if (!overdueInvoices.isEmpty()) {
            log.info("Total facturas vencidas procesadas: {}", overdueInvoices.size());
        }
    }

    @Scheduled(cron = "0 0 8 * * ?")
    @Transactional
    public void sendDueDateReminders() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        List<Invoice> invoicesDueTomorrow = invoiceRepository
            .findInvoicesDueOnDateWithBooking(InvoiceStatus.PENDING, tomorrow);

        for (Invoice invoice : invoicesDueTomorrow) {
            emailService.sendInvoiceReminderEmail(buildInvoiceEmailData(invoice));

            outboxEventService.publish(
            new NotificationEvent(
                invoice.getBooking().getUser().getId(),
                NotificationType.INVOICE_ISSUED,
                "Recordatorio de factura",
                "La factura %s vence mañana. Total a pagar: %s.".formatted(
                    invoice.getInvoiceNumber(), EmailService.formatAmount(invoice.getTotal(), invoice.getCurrency())),
                "invoice",
                invoice.getId()
            ),
            "Invoice",
            invoice.getId());

            log.info("Recordatorio enviado para factura: {}", invoice.getInvoiceNumber());
        }

        if (!invoicesDueTomorrow.isEmpty()) {
            log.info("Total recordatorios enviados: {}", invoicesDueTomorrow.size());
        }
    }

    private String generateInvoiceNumber() {
        int year = Year.now().getValue();

        InvoiceSequence sequence;
        try {
            sequence = invoiceSequenceRepository.findByYearForUpdate(year)
                .orElseGet(() -> invoiceSequenceRepository.save(new InvoiceSequence(year)));
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // Race condition: otro request creó la secuencia primero → recargar con lock
            sequence = invoiceSequenceRepository.findByYearForUpdate(year)
                .orElseThrow(() -> new IllegalStateException(
                    "No se pudo crear ni cargar la secuencia de facturas para el año " + year));
        }

        long assigned = sequence.getNextValue();
        sequence.setNextValue(assigned + 1);
        invoiceSequenceRepository.save(sequence);

        return "INV-%d-%05d".formatted(year, assigned);
    }

    public InvoiceEmailData buildInvoiceEmailData(Invoice invoice) {
        return new InvoiceEmailData(
            invoice.getId(),
            invoice.getInvoiceNumber(),
            invoice.getBooking().getUser().getEmail(),
            invoice.getBooking().getProperty().getAddress(),
            invoice.getSubtotal(),
            invoice.getTax(),
            invoice.getTotal(),
            invoice.getLateFee(),
            invoice.getAmountDue(),
            invoice.getCurrency(),
            invoice.getIssueDate(),
            invoice.getDueDate(),
            invoice.getStatus().name()
        );
    }
}
