package com.nestorria.server.modules.properties.dto;

import java.util.List;

import com.nestorria.server.modules.properties.embeddable.PriceDetails;

/**
 * Respuesta que representa una ruta entre propiedades.
 * Incluye la lista ordenada de nodos (propiedades) y la distancia total.
 */
public record PropertyRouteResponse(
    List<PropertyNode> route,
    double totalDistanceKm
) {
    // Nodo de la ruta: una propiedad con sus coordenadas y datos básicos.
    public record PropertyNode(
        String id,
        String title,
        String city,
        String address,
        PriceDetails price,
        Double latitude,
        Double longitude
    ) {}
}
