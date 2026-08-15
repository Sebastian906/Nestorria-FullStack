package com.nestorria.server.modules.booking.dto;

import java.util.List;

/**
 * Respuesta de métricas del dashboard.
 * 
 * Incluye:
 * - Resumen total
 * - Desglose mensual (para gráficas)
 * - Array de prefix-sum (para queries de rango O(1))
 */
public record DashboardMetricsResponse(
    int totalBookings,
    long totalRevenue,
    int totalNights,
    double averageBookingValue,
    List<MonthlyMetrics> monthlyMetrics,
    long[] revenuePrefixSum
) {
    
    /**
     * Obtiene el revenue total usando prefix-sum.
     * 
     * Complejidad: O(1)
     */
    public long getRevenueInRange(int startIndex, int endIndex) {
        if (startIndex < 0 || endIndex >= revenuePrefixSum.length || startIndex > endIndex) {
            throw new IllegalArgumentException("Invalid range");
        }
        return revenuePrefixSum[endIndex + 1] - revenuePrefixSum[startIndex];
    }
    
    /**
     * Obtiene métricas de un mes específico.
     * 
     * @param index - índice del mes (0 = primero)
     * @return métricas del mes, o null si el índice es inválido
     */
    public MonthlyMetrics getMonthAt(int index) {
        if (index < 0 || index >= monthlyMetrics.size()) {
            return null;
        }
        return monthlyMetrics.get(index);
    }
    
    /**
     * Obtiene el total de meses con datos.
     */
    public int getMonthCount() {
        return monthlyMetrics.size();
    }
    
    /**
     * Verifica si hay datos disponibles.
     */
    public boolean hasData() {
        return totalBookings > 0;
    }
}
