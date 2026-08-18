import { useState, useMemo } from "react";
import { useAppContext } from "../context/AppContext";
import PropertyMap from "../components/PropertyMap";
import { assets, type Property } from "../assets/data";
import { Link } from "react-router-dom";

const MapExplorer = () => {
    const { properties, currency } = useAppContext();
    const [selectedProperty, setSelectedProperty] = useState<Property | null>(null);
    const [cityFilter, setCityFilter] = useState<string>("");

    // --- Route mode state ---
    const [routeMode, setRouteMode] = useState(false);
    const [routeFrom, setRouteFrom] = useState<string | null>(null);
    const [routeTo, setRouteTo] = useState<string | null>(null);

    // Obtener ciudades únicas de las propiedades que tienen ubicación
    const cities = useMemo(() => {
        const citySet = new Set<string>();
        properties.forEach((p) => {
            if (p.location?.latitude && p.location?.longitude) {
                citySet.add(p.city);
            }
        });
        return Array.from(citySet).sort();
    }, [properties]);

    // Filtrar propiedades con coordenadas válidas
    const propertiesWithCoords = useMemo(() => {
        return properties.filter(
            (p) =>
                p.location?.latitude != null &&
                p.location?.longitude != null &&
                p.location.latitude !== 0 &&
                p.location.longitude !== 0 &&
                (cityFilter === "" || p.city === cityFilter)
        );
    }, [properties, cityFilter]);

    // Calcular centro del mapa basado en las propiedades filtradas
    const mapCenter = useMemo((): [number, number] => {
        if (propertiesWithCoords.length === 0) {
            // Centro de Colombia por defecto
            return [4.5709, -74.2973];
        }
        const avgLat =
            propertiesWithCoords.reduce(
                (sum, p) => sum + (p.location?.latitude ?? 0),
                0
            ) / propertiesWithCoords.length;
        const avgLng =
            propertiesWithCoords.reduce(
                (sum, p) => sum + (p.location?.longitude ?? 0),
                0
            ) / propertiesWithCoords.length;
        return [avgLat, avgLng];
    }, [propertiesWithCoords]);

    // Handler de click en propiedad: en modo ruta selecciona origen/destino,
    // en modo normal muestra detalles
    const handlePropertyClick = (property: Property) => {
        if (routeMode) {
            if (!routeFrom) {
                setRouteFrom(property._id);
            } else if (!routeTo && property._id !== routeFrom) {
                setRouteTo(property._id);
            }
        } else {
            setSelectedProperty(property);
        }
    };

    return (
        <div className="bg-linear-to-r from-[#F0FDF4] to-white pt-24 min-h-screen">
            <div className="max-padd-container">
                {/* Header */}
                <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4 py-6">
                    <div>
                        <h1 className="h2">Explore properties on the map</h1>
                        <p className="text-gray-500 mt-1">
                            {propertiesWithCoords.length} properties with location
                            {cityFilter && ` in ${cityFilter}`}
                        </p>
                    </div>
                    <div className="flex items-center gap-3">
                        {/* Filtro de ciudad */}
                        <select
                            value={cityFilter}
                            onChange={(e) => {
                                setCityFilter(e.target.value);
                                setSelectedProperty(null);
                            }}
                            className="bg-white border border-slate-900/10 rounded-lg px-4 py-2 text-sm outline-none cursor-pointer"
                        >
                            <option value="">All cities</option>
                            {cities.map((city) => (
                                <option key={city} value={city}>
                                    {city}
                                </option>
                            ))}
                        </select>
                        {/* Botón de modo ruta */}
                        <button
                            onClick={() => {
                                setRouteMode(!routeMode);
                                setRouteFrom(null);
                                setRouteTo(null);
                                setSelectedProperty(null);
                            }}
                            className={`px-4 py-2 text-sm rounded-lg border transition ${routeMode
                                    ? "bg-blue-500 text-white border-blue-500"
                                    : "bg-white border-slate-900/10 hover:bg-gray-50"
                                }`}
                        >
                            {routeMode ? "Exit Route" : "Find Route"}
                        </button>
                        <Link
                            to="/listing"
                            className="btn-outline px-4 py-2 text-sm rounded-lg"
                        >
                            Check listing
                        </Link>
                    </div>
                </div>

                {/* Mapa + Sidebar */}
                <div className="flex flex-col lg:flex-row gap-6 pb-10">
                    {/* Mapa */}
                    <div className="flex-1 rounded-xl overflow-hidden border border-gray-200 shadow-sm">
                        <PropertyMap
                            properties={propertiesWithCoords}
                            center={mapCenter}
                            zoom={propertiesWithCoords.length === 0 ? 6 : 12}
                            height="calc(100vh - 200px)"
                            onPropertyClick={handlePropertyClick}
                            showRoute={routeMode && routeFrom != null && routeTo != null}
                            routeFrom={routeFrom ?? undefined}
                            routeTo={routeTo ?? undefined}
                        />
                    </div>

                    {/* Sidebar con detalles de la propiedad seleccionada */}
                    <div className="w-full lg:w-96 shrink-0">
                        {/* Indicador de modo ruta */}
                        {routeMode && (
                            <div className="bg-blue-50 border border-blue-200 rounded-xl p-4 mb-4">
                                <p className="text-sm font-medium text-blue-800">
                                    {!routeFrom
                                        ? "Click on the origin property"
                                        : !routeTo
                                            ? "Now click on the destination property"
                                            : "Route calculated"}
                                </p>
                                {(routeFrom || routeTo) && (
                                    <button
                                        onClick={() => {
                                            setRouteFrom(null);
                                            setRouteTo(null);
                                        }}
                                        className="text-xs text-blue-600 hover:underline mt-1"
                                    >
                                        Reset route
                                    </button>
                                )}
                            </div>
                        )}

                        {selectedProperty ? (
                            <div className="bg-white rounded-xl border border-slate-900/10 p-5 sticky top-28">
                                <img
                                    src={selectedProperty.images[0]}
                                    alt={selectedProperty.title}
                                    className="w-full h-48 object-cover rounded-lg mb-4"
                                />
                                <h3 className="font-semibold text-lg">
                                    {selectedProperty.title}
                                </h3>
                                <p className="text-gray-500 text-sm mt-1 flex items-center gap-1">
                                    <img src={assets.pin} alt="" width={14} />
                                    {selectedProperty.address}, {selectedProperty.city}
                                </p>
                                {selectedProperty.location?.neighborhood && (
                                    <p className="text-gray-400 text-xs mt-1 ml-5">
                                        {selectedProperty.location.neighborhood}
                                        {selectedProperty.location.postalCode &&
                                            ` · CP: ${selectedProperty.location.postalCode}`}
                                    </p>
                                )}
                                <div className="flex items-center justify-between mt-4">
                                    <div>
                                        <p className="text-xs text-gray-400">Sale price</p>
                                        <p className="font-bold text-lg text-secondary">
                                            {currency}
                                            {selectedProperty.price.sale.toLocaleString()}
                                        </p>
                                    </div>
                                    <div className="text-right">
                                        <p className="text-xs text-gray-400">Type</p>
                                        <p className="font-medium text-sm">
                                            {selectedProperty.propertyType}
                                        </p>
                                    </div>
                                </div>
                                <div className="flex gap-4 mt-3 text-sm text-gray-500">
                                    <span>{selectedProperty.facilities.bedrooms} rooms</span>
                                    <span>{selectedProperty.facilities.bathrooms} bathrooms</span>
                                    <span>{selectedProperty.area} m²</span>
                                </div>
                                <Link
                                    to={`/listing/${selectedProperty._id}`}
                                    className="block w-full text-center btn-dark rounded-lg py-2 mt-5"
                                >
                                    View details
                                </Link>
                                <button
                                    onClick={() => setSelectedProperty(null)}
                                    className="w-full text-center text-gray-400 hover:text-gray-600 text-sm mt-2"
                                >
                                    Close
                                </button>
                            </div>
                        ) : (
                            <div className="bg-white rounded-xl border border-slate-900/10 p-6 text-center sticky top-28">
                                <img
                                    src={assets.map}
                                    alt=""
                                    className="w-16 h-16 mx-auto opacity-30 mb-3"
                                />
                                <p className="text-gray-400 text-sm">
                                    Click on a map marker to view the details of the property
                                </p>
                                <div className="mt-6 space-y-2 text-left">
                                    <p className="text-xs font-medium text-gray-500">
                                        Properties per city:
                                    </p>
                                    {cities.map((city) => {
                                        const count = propertiesWithCoords.filter(
                                            (p) => p.city === city
                                        ).length;
                                        return (
                                            <button
                                                key={city}
                                                onClick={() => setCityFilter(city)}
                                                className="flex items-center justify-between w-full text-sm text-gray-600 hover:text-secondary transition px-2 py-1 rounded hover:bg-secondary/5"
                                            >
                                                <span>{city}</span>
                                                <span className="text-xs bg-gray-100 px-2 py-0.5 rounded-full">
                                                    {count}
                                                </span>
                                            </button>
                                        );
                                    })}
                                </div>
                            </div>
                        )}
                    </div>
                </div>
            </div>
        </div>
    );
};

export default MapExplorer;
