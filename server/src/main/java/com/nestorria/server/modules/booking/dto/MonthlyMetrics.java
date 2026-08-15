package com.nestorria.server.modules.booking.dto;

import java.time.YearMonth;

/**
 * Métricas mensuales de bookings.
 * 
 * Implementa el patrón de acumulación DP:
 * - Cada booking se procesa en O(1)
 * - Las métricas se acumulan incrementalmente
 * - No se requiere re-procesar datos anteriores
 */
public record MonthlyMetrics(
    YearMonth month,
    int totalBookings,
    long totalRevenue,
    int totalNights,
    double averageBookingValue
) {
    
    /**
     * Crea métricas vacías para un mes dado.
     * Punto base para la acumulación DP.
     */
    public static MonthlyMetrics empty(YearMonth month) {
        return new MonthlyMetrics(month, 0, 0, 0, 0.0);
    }
    
    /**
     * Agrega un booking a las métricas (acumulación DP).
     * 
     * Complejidad: O(1)
     * 
     * @param booking - booking a agregar
     * @return nuevas métricas acumuladas
     */
    public MonthlyMetrics addBooking(com.nestorria.server.modules.booking.Booking booking) {
        int newTotalBookings = this.totalBookings + 1;
        long newTotalRevenue = this.totalRevenue + booking.getTotalPrice();
        int newTotalNights = this.totalNights + calculateNights(booking);
        double newAverage = newTotalBookings > 0 
            ? (double) newTotalRevenue / newTotalBookings 
            : 0.0;
        
        return new MonthlyMetrics(
            this.month,
            newTotalBookings,
            newTotalRevenue,
            newTotalNights,
            newAverage
        );
    }
    
    /**
     * Agrega datos pre-calculados a las métricas.
     * Útil cuando el booking cruza múltiples meses.
     * 
     * @param bookings - número de bookings a agregar
     * @param revenue - revenue a agregar
     * @param nights - noches a agregar
     * @return nuevas métricas acumuladas
     */
    public MonthlyMetrics addAccumulated(int bookings, long revenue, int nights) {
        int newTotalBookings = this.totalBookings + bookings;
        long newTotalRevenue = this.totalRevenue + revenue;
        int newTotalNights = this.totalNights + nights;
        double newAverage = newTotalBookings > 0 
            ? (double) newTotalRevenue / newTotalBookings 
            : 0.0;
        
        return new MonthlyMetrics(
            this.month,
            newTotalBookings,
            newTotalRevenue,
            newTotalNights,
            newAverage
        );
    }
    
    /**
     * Calcula el número de noches de un booking.
     */
    private int calculateNights(com.nestorria.server.modules.booking.Booking booking) {
        return (int) java.time.temporal.ChronoUnit.DAYS.between(
            booking.getCheckInDate(), 
            booking.getCheckOutDate()
        );
    }
}
