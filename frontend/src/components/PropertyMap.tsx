import { MapContainer, TileLayer, Marker, Popup, useMap, Polyline } from "react-leaflet";
import { findPropertyRoute, type PropertyRoute } from "../services/routeService";
import { Link } from "react-router-dom";
import type { Property } from "../assets/data";
import "leaflet/dist/leaflet.css";
import L from "leaflet";
import { useEffect, useState } from "react";

// Fix para iconos de Leaflet con bundlers modernos
delete (L.Icon.Default.prototype as any)._getIconUrl;
L.Icon.Default.mergeOptions({
    iconRetinaUrl: "https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-icon-2x.png",
    iconUrl: "https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-icon.png",
    shadowUrl: "https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-shadow.png",
});

interface PropertyMapProps {
    properties: Property[];
    center?: [number, number]; // [lat, lng]
    zoom?: number;
    height?: string;
    onPropertyClick?: (property: Property) => void;
    showRoute?: boolean;          // activar modo ruta
    routeFrom?: string;           // ID propiedad origen
    routeTo?: string;             // ID propiedad destino
}

// Componente interno para cambiar el centro del mapa programáticamente
function MapUpdater({ center }: { center?: [number, number] }) {
    const map = useMap();
    useEffect(() => {
        if (center) {
            map.setView(center, map.getZoom());
        }
    }, [center, map]);
    return null;
}

// Componente que dibuja la polyline de la ruta y los marcadores numerados
function RoutePolyline({ route }: { route: PropertyRoute }) {
    const positions: [number, number][] = route.route
        .filter(node => node.latitude != null && node.longitude != null)
        .map(node => [node.latitude!, node.longitude!]);

    if (positions.length < 2) return null;

    return (
        <>
            <Polyline
                positions={positions}
                pathOptions={{
                    color: "#3b82f6",
                    weight: 4,
                    opacity: 0.8,
                    dashArray: "8, 8",
                }}
            />
            {positions.map((pos, idx) => (
                <Marker
                    key={`route-${idx}`}
                    position={pos}
                    icon={L.divIcon({
                        className: "route-marker",
                        html: `<div style="
                            background: #3b82f6;
                            color: white;
                            border-radius: 50%;
                            width: 24px;
                            height: 24px;
                            display: flex;
                            align-items: center;
                            justify-content: center;
                            font-size: 12px;
                            font-weight: bold;
                            border: 2px solid white;
                            box-shadow: 0 2px 4px rgba(0,0,0,0.3);
                        ">${idx + 1}</div>`,
                        iconSize: [24, 24],
                        iconAnchor: [12, 12],
                    })}
                />
            ))}
        </>
    );
}

export default function PropertyMap({
    properties,
    center = [4.711, -74.072], // Bogotá por defecto
    zoom = 12,
    height = "500px",
    onPropertyClick,
    showRoute = false,
    routeFrom,
    routeTo,
}: PropertyMapProps) {
    // Estado de la ruta cargada desde el backend
    const [route, setRoute] = useState<PropertyRoute | null>(null);

    // Cargar ruta cuando cambian los IDs de origen/destino
    useEffect(() => {
        if (showRoute && routeFrom && routeTo) {
            let active = true;
            // Limpiar la ruta previa antes de cada petición nueva
            setRoute(null);
            findPropertyRoute(routeFrom, routeTo).then(result => {
                // Ignorar respuestas obsoletas si la petición fue superada
                if (active) setRoute(result);
            });
            return () => {
                active = false;
            };
        }
        setRoute(null);
    }, [showRoute, routeFrom, routeTo]);

    // Filtrar propiedades que tengan coordenadas válidas
    const propertiesWithCoords = properties.filter(
        (p) =>
            p.location?.latitude != null &&
            p.location?.longitude != null &&
            p.location.latitude !== 0 &&
            p.location.longitude !== 0
    );

    return (
        <MapContainer
            center={center}
            zoom={zoom}
            style={{ height, width: "100%", borderRadius: "12px" }}
            scrollWheelZoom={true}
        >
            <TileLayer
                attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
                url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
            />
            <MapUpdater center={center} />
            {propertiesWithCoords.map((property) => (
                <Marker
                    key={property._id}
                    position={[
                        property.location!.latitude!,
                        property.location!.longitude!,
                    ]}
                    eventHandlers={{
                        click: () => {
                            onPropertyClick?.(property);
                        },
                    }}
                >
                    <Popup>
                        <div className="min-w-50">
                            <img
                                src={property.images[0]}
                                alt={property.title}
                                className="w-full h-28 object-cover rounded-md mb-2"
                            />
                            <h3 className="font-semibold text-sm">{property.title}</h3>
                            <p className="text-gray-500 text-xs">{property.city}</p>
                            <p className="font-bold text-primary text-sm mt-1">
                                ${property.price.sale.toLocaleString()} sale
                            </p>
                            <Link
                                to={`/listing/${property._id}`}
                                className="text-xs text-blue-500 hover:underline mt-1 block"
                            >
                                Check details
                            </Link>
                        </div>
                    </Popup>
                </Marker>
            ))}
            {/* Dibujar la ruta si existe */}
            {route && <RoutePolyline route={route} />}
            {propertiesWithCoords.length === 0 && (
                <div className="absolute top-1/2 left-1/2 transform -translate-x-1/2 -translate-y-1/2 z-1000 bg-white px-4 py-2 rounded-lg shadow-lg">
                    <p className="text-gray-500 text-sm">
                        There are no properties with valid coordinates to display on the map.
                    </p>
                </div>
            )}
        </MapContainer>
    );
}