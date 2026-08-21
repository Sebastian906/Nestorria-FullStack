import { useState, useMemo } from "react"
import { Link } from "react-router-dom"
import { useAppContext } from "../context/AppContext"
import { assets, type Property } from "../assets/data"

const MAX_COMPARE = 4

const Compare = () => {
    const { properties, currency } = useAppContext()
    const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set())

    const toggleSelect = (id: string) => {
        setSelectedIds(prev => {
            const next = new Set(prev)
            if (next.has(id)) {
                next.delete(id)
            } else if (next.size < MAX_COMPARE) {
                next.add(id)
            }
            return next
        })
    }

    const removeSelected = (id: string) => {
        setSelectedIds(prev => {
            const next = new Set(prev)
            next.delete(id)
            return next
        })
    }

    const clearAll = () => setSelectedIds(new Set())

    const selectedProperties = useMemo(() => {
        return properties.filter(p => selectedIds.has(p._id))
    }, [properties, selectedIds])

    const comparisonFields = [
        { label: "Price (Sale)", render: (p: Property) => `${currency}${p.price.sale.toLocaleString()}` },
        { label: "Price (Rent/Night)", render: (p: Property) => `${currency}${p.price.rent}` },
        { label: "Area", render: (p: Property) => `${p.area} m²` },
        { label: "Bedrooms", render: (p: Property) => p.facilities.bedrooms },
        { label: "Bathrooms", render: (p: Property) => p.facilities.bathrooms },
        { label: "Garages", render: (p: Property) => p.facilities.garages },
        { label: "Type", render: (p: Property) => p.propertyType },
        { label: "City", render: (p: Property) => p.city },
        { label: "Available", render: (p: Property) => p.isAvailable ? "Yes" : "No" },
        { label: "Rating", render: (p: Property) => p.averageRating ? `${p.averageRating.toFixed(1)} (${p.reviewCount})` : "No reviews" },
    ]

    return (
        <div className='bg-linear-to-r from-[#F0FDF4] to-white py-16 pt-28'>
            <div className='max-padd-container'>
                <div className='flex items-center justify-between mb-6'>
                    <h2 className='h2'>Compare Properties</h2>
                    {selectedIds.size > 0 && (
                        <button onClick={clearAll} className="text-sm text-red-500 hover:underline">
                            Clear all ({selectedIds.size})
                        </button>
                    )}
                </div>

                {/* Property selector */}
                <div className='mb-8'>
                    <p className="text-sm text-gray-500 mb-3">
                        Select up to {MAX_COMPARE} properties to compare ({selectedIds.size}/{MAX_COMPARE})
                    </p>
                    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3">
                        {properties.map((property) => (
                            <button
                                key={property._id}
                                type="button"
                                onClick={() => toggleSelect(property._id)}
                                aria-pressed={selectedIds.has(property._id)}
                                className={`cursor-pointer p-3 rounded-lg border text-left transition-all ${selectedIds.has(property._id)
                                        ? 'border-secondary bg-secondary/10 ring-2 ring-secondary/30'
                                        : 'border-slate-900/10 bg-white hover:border-secondary/50'
                                    }`}
                            >
                                <div className="flex items-center gap-3">
                                    <img
                                        src={property.images[0]}
                                        alt={property.title}
                                        className="h-12 w-12 rounded object-cover"
                                    />
                                    <div className="flex-1 min-w-0">
                                        <p className="text-sm font-medium line-clamp-1">{property.title}</p>
                                        <p className="text-xs text-gray-500">{property.city}</p>
                                    </div>
                                    {selectedIds.has(property._id) && (
                                        <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" className="text-secondary shrink-0">
                                            <path d="M20 6 9 17l-5-5" />
                                        </svg>
                                    )}
                                </div>
                            </button>
                        ))}
                    </div>
                </div>

                {/* Comparison table */}
                {selectedProperties.length > 0 ? (
                    <div className="overflow-x-auto">
                        <table className="w-full bg-white rounded-xl ring-1 ring-slate-900/5">
                            <thead>
                                <tr className="border-b border-slate-900/5">
                                    <th className="text-left p-4 text-sm font-medium text-gray-500 w-40">Feature</th>
                                    {selectedProperties.map((p) => (
                                        <th key={p._id} className="text-left p-4">
                                            <div className="flex items-center justify-between">
                                                <Link to={`/listing/${p._id}`} className="h5 hover:underline line-clamp-1">
                                                    {p.title}
                                                </Link>
                                                <button
                                                    onClick={() => removeSelected(p._id)}
                                                    className="ml-2 text-gray-400 hover:text-red-500"
                                                >
                                                    <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                                                        <path d="M18 6 6 18" />
                                                        <path d="m6 6 12 12" />
                                                    </svg>
                                                </button>
                                            </div>
                                        </th>
                                    ))}
                                </tr>
                            </thead>
                            <tbody>
                                {comparisonFields.map((field, idx) => (
                                    <tr key={field.label} className={idx % 2 === 0 ? 'bg-secondary/5' : ''}>
                                        <td className="p-4 text-sm font-medium text-gray-600">{field.label}</td>
                                        {selectedProperties.map((p) => (
                                            <td key={p._id} className="p-4 text-sm">
                                                {field.render(p)}
                                            </td>
                                        ))}
                                    </tr>
                                ))}
                                {/* Amenities row */}
                                <tr className="border-t border-slate-900/5">
                                    <td className="p-4 text-sm font-medium text-gray-600">Amenities</td>
                                    {selectedProperties.map((p) => (
                                        <td key={p._id} className="p-4 text-sm">
                                            <div className="flex flex-wrap gap-1">
                                                {p.amenities.slice(0, 5).map((a, i) => (
                                                    <span key={i} className="text-xs bg-secondary/10 px-2 py-0.5 rounded">
                                                        {a}
                                                    </span>
                                                ))}
                                                {p.amenities.length > 5 && (
                                                    <span className="text-xs text-gray-400">+{p.amenities.length - 5}</span>
                                                )}
                                            </div>
                                        </td>
                                    ))}
                                </tr>
                            </tbody>
                        </table>
                    </div>
                ) : (
                    <div className="text-center py-16 bg-white rounded-xl ring-1 ring-slate-900/5">
                        <img src={assets.sliders} alt="" className="w-12 h-12 mx-auto mb-4 opacity-30" />
                        <p className="text-gray-500">Select properties above to start comparing</p>
                    </div>
                )}
            </div>
        </div>
    )
}

export default Compare