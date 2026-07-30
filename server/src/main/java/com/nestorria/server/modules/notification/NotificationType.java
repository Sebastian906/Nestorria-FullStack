package com.nestorria.server.modules.notification;

public enum NotificationType {

    BOOKING_CONFIRMED("Reserva confirmada"),
    BOOKING_CANCELLED("Reserva cancelada"),
    PAYMENT_RECEIVED("Pago recibido"),
    CONTRACT_SIGNED("Contrato firmado"),
    CONTRACT_EXPIRED("Contrato vencido"),
    REVIEW_RECEIVED("Reseña recibida"),
    PROPERTY_INQUIRY("Consulta sobre propiedad");

    private final String defaultTitle;

    NotificationType(String defaultTitle) {
        this.defaultTitle = defaultTitle;
    }

    public String defaultTitle() {
        return defaultTitle;
    }
}
