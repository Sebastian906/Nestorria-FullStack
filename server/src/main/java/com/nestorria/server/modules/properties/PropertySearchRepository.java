package com.nestorria.server.modules.properties;

import java.util.List;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nestorria.server.common.datasource.ReadFromReplica;

public interface PropertySearchRepository extends JpaRepository<Property, String> {

    /**
     * Búsqueda por radio: propiedades dentro de radiusMeters de la coordenada (lat, lng).
     * Usa PostGIS ST_DWithin sobre geografías (metros) para precisión.
     * Construye la geometría on-the-fly desde las columnas location_latitude / location_longitude.
     * ORDER BY distancia ascendente.
     */
    @Query(value = """
        SELECT p.* FROM properties p
        WHERE p.is_available = true
          AND p.location_latitude IS NOT NULL
          AND p.location_longitude IS NOT NULL
          AND ST_DWithin(
                ST_SetSRID(ST_MakePoint(p.location_longitude, p.location_latitude), 4326)::geography,
                ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography,
                :radiusMeters
              )
        ORDER BY ST_Distance(
                   ST_SetSRID(ST_MakePoint(p.location_longitude, p.location_latitude), 4326)::geography,
                   ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography
                 )
        """, nativeQuery = true)

    @ReadFromReplica
    List<Property> findNearby(@Param("lat") double lat,
                              @Param("lng") double lng,
                              @Param("radiusMeters") double radiusMeters);

    /**
     * Búsqueda por radio + filtros tradicionales (city, propertyType, precio).
     * Parámetros opcionales: si son null se ignoran.
     */
    @Query(value = """
        SELECT p.* FROM properties p
        WHERE p.is_available = true
          AND p.location_latitude IS NOT NULL
          AND p.location_longitude IS NOT NULL
          AND ST_DWithin(
                ST_SetSRID(ST_MakePoint(p.location_longitude, p.location_latitude), 4326)::geography,
                ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography,
                :radiusMeters
              )
          AND (:categoryIds IS NULL OR p.category_id IN (:categoryIds))
          AND (:city IS NULL OR unaccent(LOWER(p.city)) LIKE '%' || unaccent(LOWER(:city)) || '%')
          AND (:propertyType IS NULL OR p.property_type = CAST(:propertyType AS text))
          AND (:minPrice IS NULL
               OR (p.price_sale IS NOT NULL AND p.price_sale >= :minPrice)
               OR (p.price_rent IS NOT NULL AND p.price_rent >= :minPrice))
          AND (:maxPrice IS NULL
               OR (p.price_sale IS NOT NULL AND p.price_sale <= :maxPrice)
               OR (p.price_rent IS NOT NULL AND p.price_rent <= :maxPrice))
        ORDER BY ST_Distance(
                   ST_SetSRID(ST_MakePoint(p.location_longitude, p.location_latitude), 4326)::geography,
                   ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography
                 )
        """, nativeQuery = true)

    @ReadFromReplica
    List<Property> findNearbyWithFilters(
            @Param("lat") double lat,
            @Param("lng") double lng,
            @Param("radiusMeters") double radiusMeters,
            @Param("city") String city,
            @Param("propertyType") String propertyType,
            @Param("minPrice") Integer minPrice,
            @Param("maxPrice") Integer maxPrice,
            @Param("categoryIds") Set<Long> categoryIds);

    // Búsqueda solo con filtros tradicionales (sin componente espacial).
    @Query(value = """
        SELECT p.* FROM properties p
        WHERE p.is_available = true
          AND (:categoryIds IS NULL OR p.category_id IN (:categoryIds))
          AND (:city IS NULL OR unaccent(LOWER(p.city)) LIKE '%' || unaccent(LOWER(:city)) || '%')
          AND (:propertyType IS NULL OR p.property_type = CAST(:propertyType AS text))
          AND (:minPrice IS NULL
               OR (p.price_sale IS NOT NULL AND p.price_sale >= :minPrice)
               OR (p.price_rent IS NOT NULL AND p.price_rent >= :minPrice))
          AND (:maxPrice IS NULL
               OR (p.price_sale IS NOT NULL AND p.price_sale <= :maxPrice)
               OR (p.price_rent IS NOT NULL AND p.price_rent <= :maxPrice))
        """, nativeQuery = true)

    @ReadFromReplica
    List<Property> findByFilters(
            @Param("city") String city,
            @Param("propertyType") String propertyType,
            @Param("minPrice") Integer minPrice,
            @Param("maxPrice") Integer maxPrice,
            @Param("categoryIds") Set<Long> categoryIds);
}
