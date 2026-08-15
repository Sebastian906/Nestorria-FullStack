package com.nestorria.server.modules.report.dto;

import java.util.List;

/**
 * Datos para el reporte de bookings.
 * 
 * Incluye:
 * - Lista de filas con datos de cada booking
 * - Estadísticas acumuladas (totales, promedios)
 * 
 * Las estadísticas se calculan en una sola pasada (DP de acumulación)
 * durante la generación del reporte.
 */
public record BookingsReportData(
    List<BookingRow> rows,
    int totalBookings,
    long totalRevenue,
    int totalNights,
    double averageBookingValue
) {
    
    /**
     * Fila de datos de un booking en el reporte.
     */
    public record BookingRow(
        String bookingId,
        String createdAt,
        String clientEmail,
        String propertyTitle,
        String contractId,
        String checkInDate,
        String checkOutDate,
        int nights,
        long totalPrice,
        String status,
        boolean isPaid
    ) {}
    
    /**
     * Calcula el revenue total de las filas.
     * Complejidad: O(n)
     */
    public static long calculateTotalRevenue(List<BookingRow> rows) {
        return rows.stream().mapToLong(BookingRow::totalPrice).sum();
    }
    
    /**
     * Calcula el total de noches.
     * Complejidad: O(n)
     */
    public static int calculateTotalNights(List<BookingRow> rows) {
        return rows.stream().mapToInt(BookingRow::nights).sum();
    }
    
    /**
     * Filtra bookings pagados.
     * Complejidad: O(n)
     */
    public List<BookingRow> getPaidBookings() {
        return rows.stream()
            .filter(BookingRow::isPaid)
            .toList();
    }
    
    /**
     * Filtra bookings pendientes.
     * Complejidad: O(n)
     */
    public List<BookingRow> getPendingBookings() {
        return rows.stream()
            .filter(r -> !r.isPaid())
            .toList();
    }
}
