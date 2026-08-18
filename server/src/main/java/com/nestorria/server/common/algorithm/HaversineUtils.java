package com.nestorria.server.common.algorithm;

/**
 * Utilidades de distancia geodésica usando la fórmula de Haversine.
 * Calcula la distancia en kilómetros entre dos puntos sobre la superficie de la Tierra.
 * Complejidad: O(1) por cálculo.
 * Precisión: ~0.3% de error (suficiente para distancias urbanas/regionales).
 * NOTA: Para distancias > 1000 km o precisiones centimétricas,
 * usar Vincenty o cálculos geodésicos completos.
 */
public final class HaversineUtils {

    private static final double EARTH_RADIUS_KM = 6371.0;

    private HaversineUtils() {}

    /**
     * Calcula la distancia en km entre dos puntos geográficos.
     * @param lat1 latitud del punto 1 (grados decimales)
     * @param lng1 longitud del punto 1 (grados decimales)
     * @param lat2 latitud del punto 2 (grados decimales)
     * @param lng2 longitud del punto 2 (grados decimales)
     * @return distancia en kilómetros
     */
    public static double distanceKm(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                 + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                 * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }

    // Convierte kilómetros a metros.
    public static double kmToMeters(double km) {
        return km * 1000;
    }
}
