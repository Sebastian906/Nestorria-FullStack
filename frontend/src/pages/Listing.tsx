import { assets } from "../assets/data";
import Item from "../components/Item";
import { useAppContext } from "../context/AppContext";

const Listing = () => {
    const { properties } = useAppContext()
    const sortOptions = ['Relevant', 'Low to High', 'High to Low', 'Newest', 'Oldest']

    const propertyTypes = [
        'House', 'Apartment', 'Villa', 'Penthouse', 'Townhouse', 'Commercial', 'Land Plot'
    ];

    const priceRange = [
        '0 to 10000', '10000 to 50000', '50000 to 100000', '100000 to 500000', '500000 to 1000000', '1000000+'
    ]

    return (
        <div className='bg-linear-to-r from-[#F0FDF4] to-white py-28'>
            <div className='max-padd-container flex flex-col sm:flex-row gap-8 mb-16'>
                {/* LEFT SIDE - FILTERS */}
                <div className='bg-secondary/10 ring-1 ring-slate-900/5 p-6 sm:min-w-60 rounded-xl h-fit'>
                    {/* SORT */}
                    <div className='pb-2 mt-2'>
                        <h5 className='h5 mb-3'>Sort By</h5>
                        <select className='bg-secondary/10 border border-slate-900/10 outline-none text-gray-30 medium-14 h-10 w-full rounded px-3 cursor-pointer'>
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
                                    <input type="checkbox" className="w-4 h-4 cursor-pointer" />
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
                                    <input type="checkbox" className="w-4 h-4 cursor-pointer" />${price}
                                </label>
                            ))}
                        </div>
                        <div className='flex items-center gap-2 mt-5'>
                            <input
                                type="number"
                                placeholder="Min"
                                className='bg-white border-slate-900/10 outline-none text-gray-30 medium-14 h-10 w-full rounded px-2 placeholder:opacity-60'
                            />
                            <input
                                type="number"
                                placeholder="Max"
                                className='bg-white border-slate-900/10 outline-none text-gray-30 medium-14 h-10 w-full rounded px-2 placeholder:opacity-60'
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
                    {properties?.length > 0 ? (
                        <div className='grid gap-6 grid-cols-1 lg:grid-cols-2 xl:grid-cols-3'>
                            {properties.map((property) => (
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