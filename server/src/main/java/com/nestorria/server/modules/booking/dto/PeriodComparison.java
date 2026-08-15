package com.nestorria.server.modules.booking.dto;

/**
 * Comparación entre dos períodos de tiempo.
 * 
 * Usado para:
 * - Mes actual vs mes anterior
 * - Año actual vs año anterior
 * - Cualquier rango personalizado
 * 
 * Calcula:
 * - Diferencias absolutas (revenue, bookings, nights)
 * - Porcentajes de cambio
 * - Tendencia (positiva/negativa)
 */
public record PeriodComparison(
    DashboardMetricsResponse period1,
    DashboardMetricsResponse period2,
    long revenueDifference,
    int bookingsDifference,
    int nightsDifference,
    double revenueChangePercent,
    double bookingsChangePercent
) {
    
    /**
     * Verifica si el revenue creció en el segundo período.
     */
    public boolean isRevenueGrowing() {
        return revenueDifference > 0;
    }
    
    /**
     * Verifica si el número de bookings creció.
     */
    public boolean isBookingsGrowing() {
        return bookingsDifference > 0;
    }
    
    /**
     * Obtiene una descripción legible del cambio de revenue.
     * 
     * @return "Crecimiento del X%" o "Decrecimiento del X%"
     */
    public String getRevenueTrendDescription() {
        if (revenueChangePercent > 0) {
            return String.format("Crecimiento del %.1f%%", revenueChangePercent);
        } else if (revenueChangePercent < 0) {
            return String.format("Decrecimiento del %.1f%%", Math.abs(revenueChangePercent));
        } else {
            return "Sin cambio";
        }
    }
    
    /**
     * Obtiene una descripción legible del cambio de bookings.
     */
    public String getBookingsTrendDescription() {
        if (bookingsChangePercent > 0) {
            return String.format("Crecimiento del %.1f%%", bookingsChangePercent);
        } else if (bookingsChangePercent < 0) {
            return String.format("Decrecimiento del %.1f%%", Math.abs(bookingsChangePercent));
        } else {
            return "Sin cambio";
        }
    }
    
    /**
     * Calcula el revenue promedio por booking en cada período.
     */
    public double getAverageBookingValuePeriod1() {
        return period1.totalBookings() > 0 
            ? (double) period1.totalRevenue() / period1.totalBookings() 
            : 0.0;
    }
    
    public double getAverageBookingValuePeriod2() {
        return period2.totalBookings() > 0 
            ? (double) period2.totalRevenue() / period2.totalBookings() 
            : 0.0;
    }
}
