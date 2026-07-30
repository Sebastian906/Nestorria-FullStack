import { MapContainer, TileLayer, Marker, Popup, useMap } from "react-leaflet";
import { Link } from "react-router-dom";
import type { Property } from "../assets/data";
import "leaflet/dist/leaflet.css";
import L from "leaflet";

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
}

// Componente interno para cambiar el centro del mapa programáticamente
function MapUpdater({ center }: { center?: [number, number] }) {
    const map = useMap();
    if (center) {
        map.setView(center, map.getZoom());
    }
    return null;
}

export default function PropertyMap({
    properties,
    center = [4.711, -74.072], // Bogotá por defecto
    zoom = 12,
    height = "500px",
    onPropertyClick,
}: PropertyMapProps) {
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