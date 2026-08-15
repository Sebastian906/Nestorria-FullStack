package com.nestorria.server.modules.report.dto;

import java.util.List;

/**
 * Datos para el reporte de propiedades.
 * Incluye:
 * - Lista de filas con datos de cada propiedad
 * - Estadísticas acumuladas
 * - Información de contratos asociados
 * Las estadísticas se calculan usando DP de acumulación:
 * - Revenue por propiedad (suma de rentas de contratos)
 * - Total de contratos por propiedad
 */
public record PropertiesReportData(
    List<PropertyRow> rows
) {
    
    // Fila de datos de una propiedad en el reporte.
    public record PropertyRow(
        String propertyId,
        String title,
        String city,
        String country,
        String type,
        int rentPrice,
        int salePrice,
        int totalContracts,
        long totalRevenue,
        boolean isAvailable
    ) {}
    
    /**
     * Calcula el revenue total de todas las propiedades.
     * Complejidad: O(n) - una sola pasada
     */
    public long getTotalRevenue() {
        return rows.stream().mapToLong(PropertyRow::totalRevenue).sum();
    }
    
    /**
     * Calcula el total de contratos.
     * Complejidad: O(n)
     */
    public int getTotalContracts() {
        return rows.stream().mapToInt(PropertyRow::totalContracts).sum();
    }
    
    // Filtra propiedades disponibles.
    public List<PropertyRow> getAvailableProperties() {
        return rows.stream()
            .filter(PropertyRow::isAvailable)
            .toList();
    }
    
    // Filtra propiedades por ciudad.
    public List<PropertyRow> getPropertiesByCity(String city) {
        return rows.stream()
            .filter(r -> java.util.Objects.equals(r.city(), city))
            .toList();
    }
    
    // Calcula el revenue promedio por propiedad.
    public double getAverageRevenuePerProperty() {
        return rows.isEmpty() ? 0.0 
            : (double) getTotalRevenue() / rows.size();
    }
    
    // Calcula el revenue promedio por contrato.
    public double getAverageRevenuePerContract() {
        int totalContracts = getTotalContracts();
        return totalContracts == 0 ? 0.0 
            : (double) getTotalRevenue() / totalContracts;
    }
    
    /**
     * Agrupa propiedades por tipo.
     * Complejidad: O(n)
     */
    public java.util.Map<String, Long> countByType() {
        return rows.stream()
            .collect(java.util.stream.Collectors.groupingBy(
                PropertyRow::type,
                java.util.stream.Collectors.counting()
            ));
    }
    
    /**
     * Agrupa propiedades por ciudad.
     * Complejidad: O(n)
     */
    public java.util.Map<String, Long> countByCity() {
        return rows.stream()
            .collect(java.util.stream.Collectors.groupingBy(
                r -> java.util.Objects.toString(r.city(), "Sin ciudad"),
                java.util.stream.Collectors.counting()
            ));
    }
}
