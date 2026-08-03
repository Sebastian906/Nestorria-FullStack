package com.nestorria.server.modules.notification;

public enum NotificationType {

    BOOKING_CONFIRMED("Booking confirmed"),
    BOOKING_CANCELLED("Booking cancelled"),
    PAYMENT_RECEIVED("Payment received"),
    CONTRACT_SIGNED("Contract signed"),
    CONTRACT_EXPIRED("Contract expired"),
    REVIEW_RECEIVED("Review received"),
    PROPERTY_INQUIRY("Property inquiry"),
    INVOICE_ISSUED("Invoice issued"),
    INVOICE_OVERDUE("Invoice overdue"),
    INVOICE_PAID("Invoice paid");

    private final String defaultTitle;

    NotificationType(String defaultTitle) {
        this.defaultTitle = defaultTitle;
    }

    public String defaultTitle() {
        return defaultTitle;
    }
}
