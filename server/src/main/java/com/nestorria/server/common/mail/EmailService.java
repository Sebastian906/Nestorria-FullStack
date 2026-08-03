package com.nestorria.server.common.mail;

import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;

import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.nestorria.server.common.config.AppProperties;

import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;
    private final AppProperties appProperties;

    public EmailService(JavaMailSender mailSender, AppProperties appProperties) {
        this.mailSender = mailSender;
        this.appProperties = appProperties;
    }

    @Async
    public void sendBookingConfirmation(BookingEmailData data) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "utf-8");

            helper.setFrom(appProperties.mail().sender());
            helper.setTo(data.userEmail());
            helper.setSubject("Confirmación de reserva - Nestorria");
            helper.setText(buildBookingHtml(data), true);

            mailSender.send(message);
            log.info("Correo de confirmación enviado (bookingId={})", data.bookingId());
        } catch (Exception e) {
            // El correo falla silenciosamente: la reserva ya fue guardada
            log.error("Error al enviar correo de confirmación (bookingId={}): {}", data.bookingId(), e.getMessage());
        }
    }

    @Async
    public void sendInvoiceIssuedEmail(InvoiceEmailData data) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "utf-8");

            helper.setFrom(appProperties.mail().sender());
            helper.setTo(data.userEmail());
            helper.setSubject("Factura emitida - %s - Nestorria".formatted(data.invoiceNumber()));
            helper.setText(buildInvoiceIssuedHtml(data), true);

            mailSender.send(message);
            log.info("Correo de factura emitida enviado (invoiceId={})", data.invoiceId());
        } catch (Exception e) {
            log.error("Error al enviar correo de factura emitida (invoiceId={}): {}", data.invoiceId(), e.getMessage());
        }
    }

    @Async
    public void sendInvoiceReminderEmail(InvoiceEmailData data) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "utf-8");

            helper.setFrom(appProperties.mail().sender());
            helper.setTo(data.userEmail());
            helper.setSubject("Recordatorio: factura vence mañana - %s - Nestorria".formatted(data.invoiceNumber()));
            helper.setText(buildInvoiceReminderHtml(data), true);

            mailSender.send(message);
            log.info("Correo de recordatorio de factura enviado (invoiceId={})", data.invoiceId());
        } catch (Exception e) {
            log.error("Error al enviar correo de recordatorio (invoiceId={}): {}", data.invoiceId(), e.getMessage());
        }
    }

    @Async
    public void sendInvoiceOverdueEmail(InvoiceEmailData data) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "utf-8");

            helper.setFrom(appProperties.mail().sender());
            helper.setTo(data.userEmail());
            helper.setSubject("Factura vencida - %s - Nestorria".formatted(data.invoiceNumber()));
            helper.setText(buildInvoiceOverdueHtml(data), true);

            mailSender.send(message);
            log.info("Correo de factura vencida enviado (invoiceId={})", data.invoiceId());
        } catch (Exception e) {
            log.error("Error al enviar correo de factura vencida (invoiceId={}): {}", data.invoiceId(), e.getMessage());
        }
    }

    @Async
    public void sendInvoicePaidEmail(InvoiceEmailData data) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "utf-8");

            helper.setFrom(appProperties.mail().sender());
            helper.setTo(data.userEmail());
            helper.setSubject("Pago confirmado - %s - Nestorria".formatted(data.invoiceNumber()));
            helper.setText(buildInvoicePaidHtml(data), true);

            mailSender.send(message);
            log.info("Correo de pago confirmado enviado (invoiceId={})", data.invoiceId());
        } catch (Exception e) {
            log.error("Error al enviar correo de pago confirmado (invoiceId={}): {}", data.invoiceId(), e.getMessage());
        }
    }

    @SuppressWarnings("deprecation")
    private static final DateTimeFormatter DATE_FORMATTER =
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(new Locale("es", "ES"));

    private String formatAmount(long cents, String currency) {
        boolean negative = cents < 0;
        long abs = Math.abs(cents);
        return "%s%s%d.%02d".formatted(currency, negative ? " -" : " ", abs / 100, abs % 100);
    }

    private String buildBookingHtml(BookingEmailData data) {
        return """
            <h2>Detalles de tu reserva</h2>
            <p>¡Gracias por tu reserva! Aquí están los detalles:</p>
            <ul>
                <li><strong>ID de reserva:</strong> %s</li>
                <li><strong>Agencia:</strong> %s</li>
                <li><strong>Ubicación:</strong> %s</li>
                <li><strong>Check-in:</strong> %s</li>
                <li><strong>Check-out:</strong> %s</li>
                <li><strong>Total:</strong> %s (%d noches)</li>
                <li><strong>Huéspedes:</strong> %d</li>
            </ul>
            <p>Estamos emocionados de recibirte pronto.</p>
            <p>¿Necesitas cambiar algo? Contáctanos.</p>
        """.formatted(
            data.bookingId(),
            data.agencyName(),
            data.propertyAddress(),
            data.checkInDate().format(DATE_FORMATTER),
            data.checkOutDate().format(DATE_FORMATTER),
            formatAmount(data.totalPrice(), appProperties.currency()),
            data.nights(),
            data.guests()
        );
    }

    private String buildInvoiceIssuedHtml(InvoiceEmailData data) {
        return """
            <h2>Factura emitida</h2>
            <p>Se ha generado una factura para tu reserva:</p>
            <ul>
                <li><strong>Número de factura:</strong> %s</li>
                <li><strong>Propiedad:</strong> %s</li>
                <li><strong>Subtotal:</strong> %s</li>
                <li><strong>Impuestos:</strong> %s</li>
                <li><strong>Total:</strong> %s</li>
                <li><strong>Fecha de emisión:</strong> %s</li>
                <li><strong>Fecha de vencimiento:</strong> %s</li>
            </ul>
            <p>Para realizar el pago, ingresa a tu panel de facturas en la plataforma.</p>
        """.formatted(
            data.invoiceNumber(),
            data.propertyAddress(),
            formatAmount(data.subtotal(), data.currency()),
            formatAmount(data.tax(), data.currency()),
            formatAmount(data.total(), data.currency()),
            data.issueDate().format(DATE_FORMATTER),
            data.dueDate().format(DATE_FORMATTER)
        );
    }

    private String buildInvoiceReminderHtml(InvoiceEmailData data) {
        return """
            <h2>Recordatorio de factura</h2>
            <p style="color: #d97706; font-weight: bold;">Tu factura vence mañana.</p>
            <ul>
                <li><strong>Número de factura:</strong> %s</li>
                <li><strong>Propiedad:</strong> %s</li>
                <li><strong>Total a pagar:</strong> %s</li>
                <li><strong>Fecha de vencimiento:</strong> %s</li>
            </ul>
            <p>Realiza tu pago antes de la fecha de vencimiento para evitar cargos adicionales por mora.</p>
            <p>Ingresa a tu panel de facturas en la plataforma para pagar.</p>
        """.formatted(
            data.invoiceNumber(),
            data.propertyAddress(),
            formatAmount(data.total(), data.currency()),
            data.dueDate().format(DATE_FORMATTER)
        );
    }

    private String buildInvoiceOverdueHtml(InvoiceEmailData data) {
        return """
            <h2>Factura vencida</h2>
            <p style="color: #dc2626; font-weight: bold;">Tu factura ha vencido. Se ha aplicado un cargo por mora.</p>
            <ul>
                <li><strong>Número de factura:</strong> %s</li>
                <li><strong>Propiedad:</strong> %s</li>
                <li><strong>Subtotal:</strong> %s</li>
                <li><strong>Impuestos:</strong> %s</li>
                <li><strong>Cargo por mora:</strong> %s</li>
                <li><strong>Total a pagar:</strong> %s</li>
            </ul>
            <p>Realiza tu pago lo antes posible para evitar cargos adicionales.</p>
            <p>Ingresa a tu panel de facturas en la plataforma para pagar.</p>
        """.formatted(
            data.invoiceNumber(),
            data.propertyAddress(),
            formatAmount(data.subtotal(), data.currency()),
            formatAmount(data.tax(), data.currency()),
            formatAmount(data.lateFee(), data.currency()),
            formatAmount(data.amountDue(), data.currency())
        );
    }

    private String buildInvoicePaidHtml(InvoiceEmailData data) {
        return """
            <h2>Pago confirmado</h2>
            <p style="color: #16a34a; font-weight: bold;">Tu pago ha sido procesado exitosamente.</p>
            <ul>
                <li><strong>Número de factura:</strong> %s</li>
                <li><strong>Propiedad:</strong> %s</li>
                <li><strong>Monto pagado:</strong> %s</li>
                <li><strong>Estado:</strong> Pagada</li>
            </ul>
            <p>Gracias por tu pago. Si tienes alguna pregunta, contáctanos.</p>
        """.formatted(
            data.invoiceNumber(),
            data.propertyAddress(),
            formatAmount(data.amountDue(), data.currency())
        );
    }
}
