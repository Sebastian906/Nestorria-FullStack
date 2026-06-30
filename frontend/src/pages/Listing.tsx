import { useMemo, useState } from "react";
import { assets, type Property } from "../assets/data";
import Item from "../components/Item";
import { useAppContext } from "../context/AppContext";
import { useSearchParams } from "react-router-dom";

interface Filters {
    propertyType: string[];
    priceRange: string[];
}

const Listing = () => {
    const { properties, searchQuery } = useAppContext();
    const [selectedFilters, setSelectedFilters] = useState<Filters>({
        propertyType: [],
        priceRange: [],
    })

    const [selectedSort, setSelectedSort] = useState<string | null>(null)

    const [searchParams] = useSearchParams()
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

    // Sorting functions
    const sortProperties = (a: Property, b: Property): number => {
        if (selectedSort === "Low to High") return a.price.sale - b.price.sale
        if (selectedSort === "High to Low") return b.price.sale - a.price.sale
        if (selectedSort === "Newest") return new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
        if (selectedSort === "Oldest") return new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime()
        return 0
    }

    // Price filter
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

    // Filtered & sorted properties
    const filteredProperties = useMemo(() => {
        return (
            properties.filter(p => matchesType(p) && matchesPrice(p) && matchesSearch(p) && matchesHeroDestination(p)).sort(sortProperties)
        );
    }, [properties, selectedFilters, selectedSort, searchQuery, heroDestination]);

    return (
        <div className='bg-linear-to-r from-[#F0FDF4] to-white py-16 pt-28'>
            <div className='max-padd-container flex flex-col sm:flex-row gap-8 mb-16'>
                {/* LEFT SIDE - FILTERS */}
                <div className='bg-secondary/10 ring-1 ring-slate-900/5 p-6 sm:min-w-60 rounded-xl h-fit'>
                    {/* SORT */}
                    <div className='pb-2 mt-2'>
                        <h5 className='h5 mb-3'>Sort By</h5>
                        <select
                            value={selectedSort ?? ""}
                            onChange={(e) => setSelectedSort(e.target.value)}
                            className='bg-secondary/10 border border-slate-900/10 outline-none text-gray-30 medium-14 h-10 w-full rounded px-3 cursor-pointer'
                        >
                            {sortOptions.map((sort, index) => (
                                <option
                                    key={index}
                                    value={sort}
                                >{sort}</option>
                            ))}
                        </select>
                    </div>
                    {/* PROPERTY TYPE */}
                    <div className='py-4 mt-2'>
                        <h5 className='h5 mb-4'>Property Type</h5>
                        <div className="flex flex-col gap-3">
                            {propertyTypes.map((type) => (
                                <label
                                    key={type}
                                    className='flex items-center gap-3 medium-14 cursor-pointer'
                                >
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
                                <label
                                    key={price}
                                    className='flex items-center gap-3 medium-14 cursor-pointer'
                                >
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
                            <input
                                type="number"
                                placeholder="Min"
                                className='bg-white border-slate-900/10 outline-none text-gray-30 medium-14 h-10 w-18 rounded px-2 placeholder:opacity-60'
                            />
                            <input
                                type="number"
                                placeholder="Max"
                                className='bg-white border-slate-900/10 outline-none text-gray-30 medium-14 h-10 w-18 rounded px-2 placeholder:opacity-60'
                            />
                            <button
                                type="button"
                                className='bg-white border hover:bg-secondary/20 border-slate-900/10 outline-none h-10 w-12 rounded flex items-center justify-center cursor-pointer hover:opacity-90 active:scale-95 transition-all shrink-0'
                            >
                                <img
                                    src={assets.search}
                                    alt="Search"
                                    className='w-4 h-4 invert-[0.4]'
                                />
                            </button>
                        </div>
                    </div>
                </div>
                {/* RIGHT SIDE - PROPERTY LIST */}
                <div className='min-h-[97vh] overflow-y-scroll rounded-xl w-full'>
                    {filteredProperties?.length > 0 ? (
                        <div className='grid gap-6 grid-cols-1 lg:grid-cols-2 xl:grid-cols-3'>
                            {filteredProperties.map((property) => (
                                <Item
                                    key={property._id}
                                    property={property}
                                />
                            ))}
                        </div>
                    ) : (
                        <div className='text-center text-gray-500 mt-20'>
                            No matches found.
                        </div>
                    )}
                </div>
            </div>
        </div>
    )
}

export default Listing