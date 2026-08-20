import { useCallback, useEffect, useMemo, useState } from "react";
import { assets, type Property } from "../assets/data";
import Item from "../components/Item";
import { useAppContext } from "../context/AppContext";
import { useSearchParams } from "react-router-dom";
import PropertyMap from "../components/PropertyMap";
import NearbySearchPanel from "../components/NearbySearchPanel";
import { fetchListingPage, type PropertyPage } from "../services/propertyListingService";

const PAGE_SIZE = 9;

interface Filters {
    propertyType: string[];
    priceRange: string[];
}

const sortMapping: Record<string, { sortBy: "PRICE" | "DATE"; direction: "ASC" | "DESC" }> = {
    "Relevant": { sortBy: "DATE", direction: "DESC" },
    "Low to High": { sortBy: "PRICE", direction: "ASC" },
    "High to Low": { sortBy: "PRICE", direction: "DESC" },
    "Newest": { sortBy: "DATE", direction: "DESC" },
    "Oldest": { sortBy: "DATE", direction: "ASC" },
};

const Listing = () => {
    const { properties, searchQuery, user, favoriteIds } = useAppContext()
    const [selectedFilters, setSelectedFilters] = useState<Filters>({
        propertyType: [],
        priceRange: [],
    })

    const [selectedSort, setSelectedSort] = useState<string | null>(null)

    const [searchParams, setSearchParams] = useSearchParams()
    const [showOnlyFavorites, setShowOnlyFavorites] = useState<boolean>(
        () => searchParams.get("favorites") === "true"
    )
    const [viewMode, setViewMode] = useState<"grid" | "map">("grid");
    const [nearbyResults, setNearbyResults] = useState<Property[] | null>(null);
    const [nearbyError, setNearbyError] = useState<string | null>(null);

    // ---- Estado de paginación server-side ----
    const [page, setPage] = useState(0);
    const [pageData, setPageData] = useState<PropertyPage | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [reloadCount, setReloadCount] = useState(0);

    const heroDestination = (searchParams.get("destination") || "").toLowerCase().trim()

    const sortOptions = ['Relevant', 'Low to High', 'High to Low', 'Newest', 'Oldest']

    const propertyTypes = [
        'House', 'Apartment', 'Villa', 'Penthouse', 'Townhouse', 'Commercial', 'Land Plot'
    ];

    const priceRange = [
        '0 to 10000', '10000 to 50000', '50000 to 100000', '100000 to 500000', '500000 to 1000000', '1000000+'
    ]

    // Toggle filter checkboxes
    const handleFilterChange = (checked: boolean, type: keyof Filters, value: string) => {
        setSelectedFilters(prev => {
            const updated = { ...prev }
            if (checked) {
                updated[type] = [...updated[type], value]
            } else {
                updated[type] = updated[type].filter((v: string) => v !== value)
            }
            return updated
        })
    }

    // Filtros de la vista MAPA (siguen siendo client-side sobre el dataset completo del contexto) ----
    const matchesPrice = (property: Property): boolean => {
        if (selectedFilters.priceRange.length === 0) return true
        return selectedFilters.priceRange.some((range: string) => {
            if (range.endsWith('+')) {
                const min = Number(range.replace('+', ''))
                return property.price.sale >= min
            }
            const [min, max] = range.split(" to ").map(Number)
            return property.price.sale >= min && property.price.sale <= max
        })
    }

    // Type filter
    const matchesType = (property: Property): boolean => {
        if (selectedFilters.propertyType.length === 0) return true
        return selectedFilters.propertyType.includes(property.propertyType)
    }

    // Search filter using header's searchQuery
    const matchesSearch = (property: Property): boolean => {
        if (!searchQuery) return true
        return (
            property.title.toLowerCase().includes(searchQuery.toLowerCase()) ||
            property.city.toLowerCase().includes(searchQuery.toLowerCase()) ||
            property.country.toLowerCase().includes(searchQuery.toLowerCase())
        )
    }

    // Hero destination filter (from Hero form -> /listing?destination=...)
    const matchesHeroDestination = (property: any) => {
        if (!heroDestination) return true
        return (property.city || "").toLowerCase().includes(heroDestination)
    }

    const sortProperties = (a: Property, b: Property): number => {
        const s = selectedSort ? sortMapping[selectedSort] : sortMapping["Relevant"]
        const diff = s.sortBy === "PRICE"
            ? a.price.sale - b.price.sale
            : new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime()
        return s.direction === "ASC" ? diff : -diff
    }

    const mapProperties = useMemo(() => {
        return properties
            .filter(p =>
                matchesType(p) &&
                matchesPrice(p) &&
                (!heroDestination ? matchesSearch(p) : true) &&
                matchesHeroDestination(p) &&
                (!showOnlyFavorites || favoriteIds.has(p._id))
            )
            .sort(sortProperties)
    }, [properties, selectedFilters, selectedSort, searchQuery, heroDestination, showOnlyFavorites, favoriteIds]);

    // Parámetros de la consulta server-side
    const apiParams = useMemo(() => {
        const sort = selectedSort ? sortMapping[selectedSort] : sortMapping["Relevant"]
        const q = heroDestination || (searchQuery || "").trim() || undefined
        return {
            page,
            size: PAGE_SIZE,
            sortBy: sort.sortBy,
            direction: sort.direction,
            types: selectedFilters.propertyType.length > 0 ? selectedFilters.propertyType : undefined,
            priceRanges: selectedFilters.priceRange.length > 0 ? selectedFilters.priceRange : undefined,
            q,
            favoriteIds: showOnlyFavorites ? [...favoriteIds] : undefined,
        }
        // reloadCount solo para re-lanzar tras un error (retry)
    }, [page, selectedFilters, selectedSort, searchQuery, heroDestination, showOnlyFavorites, favoriteIds, reloadCount]);

    // Cualquier cambio de filtro/sort/búsqueda vuelve a la página 1
    useEffect(() => {
        setPage(0)
    }, [selectedFilters, selectedSort, searchQuery, heroDestination, showOnlyFavorites]);

    // Fetch paginado
    useEffect(() => {
        let cancelled = false
        setLoading(true)
        setError(null)
        fetchListingPage(apiParams)
            .then((data) => {
                if (!cancelled) { setPageData(data); setLoading(false) }
            })
            .catch((err: any) => {
                if (!cancelled) {
                    setError(err?.response?.data?.message ?? 'No se pudieron cargar las propiedades')
                    setPageData(null)
                    setLoading(false)
                }
            })
        return () => { cancelled = true }
    }, [apiParams]);

    const goToPage = useCallback((next: number) => {
        setPage(Math.max(0, Math.min(next, (pageData?.totalPages ?? 1) - 1)))
        scrollTo(0, 0)
    }, [pageData])

    return (
        <div className='bg-linear-to-r from-[#F0FDF4] to-white py-16 pt-28'>
            <div className='max-padd-container flex flex-col sm:flex-row gap-6 mb-16'>
                {/* LEFT SIDE - FILTERS */}
                <div className='bg-secondary/10 ring-1 ring-slate-900/5 p-5 sm:min-w-52 sm:max-w-60 rounded-xl h-fit'>
                    {/* SORT */}
                    <div className='pb-2 mt-2'>
                        <h5 className='h5 mb-3'>Sort By</h5>
                        <select
                            value={selectedSort ?? ""}
                            onChange={(e) => { setSelectedSort(e.target.value); setPage(0) }}
                            className='bg-secondary/10 border border-slate-900/10 outline-none text-gray-30 medium-14 h-10 w-full rounded px-3 cursor-pointer'
                        >
                            {sortOptions.map((sort, index) => (
                                <option key={index} value={sort}>{sort}</option>
                            ))}
                        </select>
                    </div>
                    {/* PROPERTY TYPE */}
                    <div className='py-4 mt-2'>
                        <h5 className='h5 mb-4'>Property Type</h5>
                        <div className="flex flex-col gap-3">
                            {propertyTypes.map((type) => (
                                <label key={type} className='flex items-center gap-3 medium-14 cursor-pointer'>
                                    <input
                                        type="checkbox"
                                        checked={(selectedFilters.propertyType as string[]).includes(type)}
                                        onChange={(e) => handleFilterChange(e.target.checked, 'propertyType', type)}
                                        className="w-4 h-4 cursor-pointer"
                                    />
                                    {type}
                                </label>
                            ))}
                        </div>
                    </div>
                    {/* PRICE RANGE */}
                    <div className='py-4 mt-2'>
                        <h5 className='h5 mb-4'>Price Range</h5>
                        <div className="flex flex-col gap-3">
                            {priceRange.map((price) => (
                                <label key={price} className='flex items-center gap-3 medium-14 cursor-pointer'>
                                    <input
                                        type="checkbox"
                                        checked={(selectedFilters.priceRange as string[]).includes(price)}
                                        onChange={(e) => handleFilterChange(e.target.checked, 'priceRange', price)}
                                        className="w-4 h-4 cursor-pointer"
                                    />
                                    ${price}
                                </label>
                            ))}
                        </div>
                        <div className='flex items-center gap-2 mt-5'>
                            <input type="number" placeholder="Min" className='bg-white border-slate-900/10 outline-none text-gray-30 medium-14 h-10 w-18 rounded px-2 placeholder:opacity-60' />
                            <input type="number" placeholder="Max" className='bg-white border-slate-900/10 outline-none text-gray-30 medium-14 h-10 w-18 rounded px-2 placeholder:opacity-60' />
                            <button type="button" className='bg-white border hover:bg-secondary/20 border-slate-900/10 outline-none h-10 w-12 rounded flex items-center justify-center cursor-pointer hover:opacity-90 active:scale-95 transition-all shrink-0'>
                                <img src={assets.search} alt="Search" className='w-4 h-4 invert-[0.4]' />
                            </button>
                        </div>
                    </div>
                    {/* NEARBY SEARCH */}
                    <div className="border-t border-gray-200 pt-4 mt-4">
                        <NearbySearchPanel
                            onResults={(results) => {
                                setNearbyResults(results)
                                setNearbyError(null)
                                setViewMode("map")
                            }}
                            onError={(msg) => {
                                setNearbyError(msg)
                                setNearbyResults(null)
                            }}
                        />
                        {nearbyError && <p className="text-red-500 text-xs mt-2">{nearbyError}</p>}
                        {nearbyResults && (
                            <button onClick={() => setNearbyResults(null)} className="text-xs text-gray-500 hover:text-gray-700 mt-2">
                                ← Clean nearby search results
                            </button>
                        )}
                    </div>
                    {/* FAVORITES */}
                    {user && (
                        <div className='py-4 mt-2 border-t border-slate-900/10'>
                            <label className='flex items-center gap-3 medium-14 cursor-pointer'>
                                <input
                                    type="checkbox"
                                    checked={showOnlyFavorites}
                                    onChange={(e) => {
                                        setShowOnlyFavorites(e.target.checked)
                                        setPage(0)
                                        if (e.target.checked) {
                                            setSearchParams({ favorites: 'true' })
                                        } else {
                                            setSearchParams({})
                                        }
                                    }}
                                    className="w-4 h-4 cursor-pointer"
                                />
                                Show Favorites Only
                            </label>
                        </div>
                    )}
                </div>
                {/* RIGHT SIDE - PROPERTY LIST */}
                <div className="flex-1">
                    {/* Header con toggle de vista */}
                    <div className="flex items-center justify-between mb-4">
                        {viewMode === "grid" ? (
                            <p className="text-gray-500">
                                Showing {(nearbyResults ?? pageData?.content ?? []).length} of {nearbyResults ? properties.length : (pageData?.totalElements ?? 0)} properties
                            </p>
                        ) : (
                            <p className="text-gray-500">
                                Showing {(nearbyResults ?? mapProperties).length} of {properties.length} properties
                            </p>
                        )}
                        <div className="flex items-center gap-1 bg-gray-100 rounded-lg p-1">
                            <button
                                onClick={() => setViewMode("grid")}
                                className={`p-2 rounded-md transition ${viewMode === "grid" ? "bg-white shadow-sm text-secondary" : "text-gray-400 hover:text-gray-600"}`}
                                title="Grid view"
                            >
                                <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 6a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2H6a2 2 0 01-2-2V6zM14 6a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2h-2a2 2 0 01-2-2V6zM4 16a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2H6a2 2 0 01-2-2v-2zM14 16a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2h-2a2 2 0 01-2-2v-2z" />
                                </svg>
                            </button>
                            <button
                                onClick={() => setViewMode("map")}
                                className={`p-2 rounded-md transition ${viewMode === "map" ? "bg-white shadow-sm text-secondary" : "text-gray-400 hover:text-gray-600"}`}
                                title="Map view"
                            >
                                <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 20l-5.447-2.724A1 1 0 013 16.382V5.618a1 1 0 011.447-.894L9 7m0 13l6-3m-6 3V7m6 10l4.553 2.276A1 1 0 0021 18.382V7.618a1 1 0 00-.553-.894L15 4m0 13V4m0 0L9 7" />
                                </svg>
                            </button>
                        </div>
                    </div>
                    {/* Vista grid — paginada server-side */}
                    {viewMode === "grid" && (
                        <>
                            {loading && (
                                <p className="text-gray-500 text-center py-10">Loading properties...</p>
                            )}
                            {!loading && error && (
                                <div className="text-center py-10">
                                    <p className="text-red-500 mb-4">{error}</p>
                                    <button
                                        onClick={() => setReloadCount(c => c + 1)}
                                        className="px-5 py-2 rounded-lg border border-slate-900/10 bg-white hover:bg-secondary/10 transition"
                                    >
                                        Retry
                                    </button>
                                </div>
                            )}
                            {!loading && !error && (nearbyResults ?? pageData?.content ?? []).length > 0 && (
                                <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-5">
                                    {(nearbyResults ?? pageData?.content ?? []).map((property) => (
                                        <Item key={property._id} property={property} />
                                    ))}
                                </div>
                            )}
                            {!loading && !error && (nearbyResults ?? pageData?.content ?? []).length === 0 && (
                                <p className="text-gray-500 text-center py-10">No matches found</p>
                            )}
                            {/* Paginación */}
                            {!nearbyResults && pageData && pageData.totalPages > 1 && !loading && !error && (
                                <div className="flex items-center justify-center gap-6 mt-10">
                                    <button
                                        onClick={() => goToPage(page - 1)}
                                        disabled={page === 0}
                                        className="px-5 py-2 rounded-lg border border-slate-900/10 bg-white hover:bg-secondary/10 disabled:opacity-40 disabled:cursor-not-allowed transition"
                                    >
                                        ← Previous
                                    </button>
                                    <span className="text-gray-600 medium-14">
                                        Page {page + 1} of {pageData.totalPages}
                                    </span>
                                    <button
                                        onClick={() => goToPage(page + 1)}
                                        disabled={page >= pageData.totalPages - 1}
                                        className="px-5 py-2 rounded-lg border border-slate-900/10 bg-white hover:bg-secondary/10 disabled:opacity-40 disabled:cursor-not-allowed transition"
                                    >
                                        Next →
                                    </button>
                                </div>
                            )}
                        </>
                    )}
                    {/* Vista mapa — usa el dataset completo del contexto (sin paginar) */}
                    {viewMode === "map" && (
                        <div className="rounded-xl overflow-hidden border border-gray-200">
                            <PropertyMap
                                properties={nearbyResults ?? mapProperties}
                                center={
                                    (nearbyResults ?? mapProperties).length > 0
                                        ? (() => {
                                            const withCoords = (nearbyResults ?? mapProperties).filter(
                                                (p) => p.location?.latitude != null && p.location?.longitude != null
                                            )
                                            return withCoords.length > 0
                                                ? [
                                                    withCoords.reduce((sum, p) => sum + p.location!.latitude!, 0) / withCoords.length,
                                                    withCoords.reduce((sum, p) => sum + p.location!.longitude!, 0) / withCoords.length,
                                                ]
                                                : undefined
                                        })()
                                        : undefined
                                }
                                height="600px"
                            />
                        </div>
                    )}
                </div>
            </div>
        </div>
    )
}

export default Listing