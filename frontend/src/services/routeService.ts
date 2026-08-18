import axios from "axios";

const API_BASE = import.meta.env.VITE_BACKEND_URL;

export interface RoutePropertyNode {
    id: string;
    title: string;
    city: string;
    address: string;
    price: { rent: number | null; sale: number | null };
    latitude: number | null;
    longitude: number | null;
}

export interface PropertyRoute {
    route: RoutePropertyNode[];
    totalDistanceKm: number;
}

// Servicio para calcular rutas entre propiedades usando Dijkstra en el backend.
export async function findPropertyRoute(
    fromId: string,
    toId: string
): Promise<PropertyRoute | null> {
    try {
        const response = await axios.get(
            `${API_BASE}/api/properties/route?from=${fromId}&to=${toId}`
        );
        return response.data;
    } catch (error) {
        console.warn("Error fetching route:", error);
        return null;
    }
}