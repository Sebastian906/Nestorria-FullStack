import axios from "axios";
import type { Property } from "../assets/data";

// Forma serializada de Spring Data Page<PropertySummaryResponse>.
export interface PropertyPage {
    content: Property[];
    totalElements: number;
    totalPages: number;
    number: number;   // página actual (0-based)
    size: number;
    first: boolean;
    last: boolean;
}

export interface ListingParams {
    page: number;
    size: number;
    sortBy?: "PRICE" | "DATE" | "AREA" | "RATING";
    direction?: "ASC" | "DESC";
    types?: string[];
    priceRanges?: string[];
    q?: string;
    favoriteIds?: string[];
}

// Normaliza la propiedad del backend (id -> _id, agency.owner.image, rating).
export function mapApiProperty(prop: any): Property {
    return {
        ...prop,
        _id: prop.id,
        agency: prop.agency
            ? {
                  ...prop.agency,
                  owner: {
                      image: prop.agency.ownerImage || "https://images.unsplash.com/photo-1560250097-0b93528c311a",
                  },
              }
            : null,
        averageRating: prop.averageRating ?? null,
        reviewCount: prop.reviewCount ?? 0,
    };
}

export async function fetchListingPage(params: ListingParams): Promise<PropertyPage> {
    const { data } = await axios.get("/api/properties/me", { params });
    return { ...data, content: data.content.map(mapApiProperty) };
}