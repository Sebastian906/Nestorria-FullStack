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
            helper.setText(buildBookingHtml(data), true); // true = isHtml

            mailSender.send(message);
            log.info("Correo de confirmación enviado (bookingId={})", data.bookingId());
        } catch (Exception e) {
            // El correo falla silenciosamente: la reserva ya fue guardada
            log.error("Error al enviar correo de confirmación (bookingId={}): {}", data.bookingId(), e.getMessage());
        }
    }

    @SuppressWarnings("deprecation")
    private static final DateTimeFormatter DATE_FORMATTER =
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(new Locale("es", "ES"));

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
                <li><strong>Total:</strong> %s%s (%d noches)</li>
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
            appProperties.currency(),
            data.totalPrice(),
            data.nights(),
            data.guests()
        );
    }
}
