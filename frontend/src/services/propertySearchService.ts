import axios from "axios";
import { useAuth } from "@clerk/react";
import type { Property, NearbySearchRequest } from "../assets/data";
import { mapApiProperty } from "./propertyListingService";

const API_BASE = import.meta.env.VITE_BACKEND_URL;

// Hook para obtener el token de Clerk
function useAuthToken() {
    const { getToken } = useAuth();
    return getToken;
}

/**
 * Servicio para búsquedas espaciales de propiedades.
 * 
 * Endpoints del backend:
 * - GET /api/properties/nearby?lat=...&lng=...&radius=...  (público)
 * - GET /api/properties/search?lat=...&lng=...&radius=...  (autenticado)
 */

export function usePropertySearchService() {
    const getToken = useAuthToken();

    /**
     * Busca propiedades cercanas a unas coordenadas.
     * Endpoint público, no requiere autenticación.
     */
    async function findNearby(params: NearbySearchRequest): Promise<Property[]> {
        const searchParams = new URLSearchParams();
        searchParams.set("lat", String(params.lat));
        searchParams.set("lng", String(params.lng));
        if (params.radiusKm) searchParams.set("radius", String(params.radiusKm));
        if (params.city) searchParams.set("city", params.city);
        if (params.minPrice) searchParams.set("minPrice", String(params.minPrice));
        if (params.maxPrice) searchParams.set("maxPrice", String(params.maxPrice));
        if (params.propertyType) searchParams.set("propertyType", params.propertyType);

        const response = await axios.get(
            `${API_BASE}/api/properties/nearby?${searchParams.toString()}`
        );
        return (response.data as any[]).map(mapApiProperty);
    }

    /**
     * Búsqueda combinada: filtros tradicionales + ubicación.
     * Endpoint autenticado.
     */
    async function searchWithFilters(params: NearbySearchRequest): Promise<Property[]> {
        const token = await getToken();
        const searchParams = new URLSearchParams();
        searchParams.set("lat", String(params.lat));
        searchParams.set("lng", String(params.lng));
        if (params.radiusKm) searchParams.set("radius", String(params.radiusKm));
        if (params.city) searchParams.set("city", params.city);
        if (params.minPrice) searchParams.set("minPrice", String(params.minPrice));
        if (params.maxPrice) searchParams.set("maxPrice", String(params.maxPrice));
        if (params.propertyType) searchParams.set("propertyType", params.propertyType);

        const response = await axios.get(
            `${API_BASE}/api/properties/search?${searchParams.toString()}`,
            { headers: { Authorization: `Bearer ${token}` } }
        );
        return (response.data as any[]).map(mapApiProperty);
    }

    return { findNearby, searchWithFilters };
}