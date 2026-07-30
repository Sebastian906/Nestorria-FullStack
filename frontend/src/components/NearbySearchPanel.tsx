import { useState, useCallback } from "react";
import { usePropertySearchService } from "../services/propertySearchService";
import type { Property, NearbySearchRequest } from "../assets/data";

interface NearbySearchPanelProps {
    onResults: (properties: Property[]) => void;
    onError: (message: string) => void;
}

export default function NearbySearchPanel({ onResults, onError }: NearbySearchPanelProps) {
    const { findNearby } = usePropertySearchService();
    const [isLoading, setIsLoading] = useState(false);
    const [radius, setRadius] = useState<number>(5);
    const [userLocation, setUserLocation] = useState<{ lat: number; lng: number } | null>(null);
    const [locationError, setLocationError] = useState<string | null>(null);

    const getCurrentLocation = useCallback(() => {
        if (!navigator.geolocation) {
            setLocationError("Your web browser does not support geolocation");
            return;
        }

        setIsLoading(true);
        setLocationError(null);

        navigator.geolocation.getCurrentPosition(
            (position) => {
                setUserLocation({
                    lat: position.coords.latitude,
                    lng: position.coords.longitude,
                });
                setIsLoading(false);
            },
            (error) => {
                setIsLoading(false);
                switch (error.code) {
                    case error.PERMISSION_DENIED:
                        setLocationError("Location permission denied. Enable location in your web browser.");
                        break;
                    case error.POSITION_UNAVAILABLE:
                        setLocationError("Location not available");
                        break;
                    case error.TIMEOUT:
                        setLocationError("Timeout exceeded");
                        break;
                    default:
                        setLocationError("Error obtaining location");
                }
            },
            { enableHighAccuracy: true, timeout: 10000, maximumAge: 300000 }
        );
    }, []);

    const handleSearch = useCallback(async () => {
        if (!userLocation) {
            onError("First get your location using the button above.");
            return;
        }

        setIsLoading(true);
        try {
            const params: NearbySearchRequest = {
                lat: userLocation.lat,
                lng: userLocation.lng,
                radiusKm: radius,
            };
            const results = await findNearby(params);
            onResults(results);
        } catch (error: any) {
            const message = error.response?.data?.message || "Error looking for close locations";
            onError(message);
        } finally {
            setIsLoading(false);
        }
    }, [userLocation, radius, findNearby, onResults, onError]);

    return (
        <div className="bg-white rounded-xl p-4 shadow-sm border border-gray-100">
            <h3 className="font-semibold text-lg mb-3">Search near me</h3>

            {/* Botón de ubicación */}
            <div className="mb-4">
                {!userLocation ? (
                    <button
                        onClick={getCurrentLocation}
                        disabled={isLoading}
                        className="w-full flex items-center justify-center gap-2 bg-secondary/10 text-secondary px-4 py-3 rounded-lg hover:bg-secondary/20 transition disabled:opacity-50"
                    >
                        <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M17.657 16.657L13.414 20.9a1.998 1.998 0 01-2.827 0l-4.244-4.243a8 8 0 1111.314 0z" />
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 11a3 3 0 11-6 0 3 3 0 016 0z" />
                        </svg>
                        {isLoading ? "Getting location..." : "Use my location"}
                    </button>
                ) : (
                    <div className="flex items-center gap-2 text-sm text-gray-600 bg-gray-50 px-3 py-2 rounded-lg">
                        <svg className="w-4 h-4 text-secondary" fill="currentColor" viewBox="0 0 20 20">
                            <path fillRule="evenodd" d="M5.05 4.05a7 7 0 119.9 9.9L10 18.9l-4.95-4.95a7 7 0 010-9.9zM10 11a2 2 0 100-4 2 2 0 000 4z" clipRule="evenodd" />
                        </svg>
                        <span>Location: {userLocation.lat.toFixed(4)}, {userLocation.lng.toFixed(4)}</span>
                        <button
                            onClick={() => setUserLocation(null)}
                            className="ml-auto text-gray-400 hover:text-gray-600"
                        >
                            ×
                        </button>
                    </div>
                )}
                {locationError && (
                    <p className="text-red-500 text-xs mt-1">{locationError}</p>
                )}
            </div>

            {/* Radio de búsqueda */}
            <div className="mb-4">
                <label className="block text-sm font-medium text-gray-700 mb-1">
                    Radius: {radius} km
                </label>
                <input
                    type="range"
                    min="1"
                    max="50"
                    value={radius}
                    onChange={(e) => setRadius(Number(e.target.value))}
                    className="w-full h-2 bg-gray-200 rounded-lg appearance-none cursor-pointer accent-secondary"
                />
                <div className="flex justify-between text-xs text-gray-400 mt-1">
                    <span>1 km</span>
                    <span>50 km</span>
                </div>
            </div>

            {/* Botón de búsqueda */}
            <button
                onClick={handleSearch}
                disabled={isLoading || !userLocation}
                className="w-full bg-secondary text-white px-4 py-3 rounded-lg hover:bg-secondary/90 transition font-medium disabled:opacity-50 disabled:cursor-not-allowed"
            >
                {isLoading ? (
                    <span className="flex items-center justify-center gap-2">
                        <svg className="animate-spin h-4 w-4" viewBox="0 0 24 24">
                            <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" fill="none" />
                            <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z" />
                        </svg>
                        Searching...
                    </span>
                ) : (
                    "Buscar propiedades cercanas"
                )}
            </button>
        </div>
    );
}