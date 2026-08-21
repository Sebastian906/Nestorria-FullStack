import { Link } from "react-router-dom"
import { assets, blogs } from "../assets/data"

// Reordenar las imágenes para mejor flujo visual
// blog1=Cities, blog2=Rental, blog3=Interior, blog4=Checklist,
// blog6=Community, blog7=Staging, blog8=Forecast
// blog5=ROI (redundante con propiedades, se excluye)
const guideImages = [
    { image: blogs[7].image, step: "01", title: "Explore Properties", category: "Market Trends" },
    { image: blogs[1].image, step: "02", title: "Find the Right Fit", category: "Renting Guide" },
    { image: blogs[2].image, step: "03", title: "Visualize Your Space", category: "Home Improvement" },
    { image: blogs[3].image, step: "04", title: "Follow the Checklist", category: "Buying Tips" },
    { image: blogs[5].image, step: "05", title: "Consider the Lifestyle", category: "Lifestyle" },
    { image: blogs[6].image, step: "06", title: "Prepare & Book", category: "Selling Tips" },
]

const steps = [
    {
        number: "01",
        title: "Explore Properties",
        description: "Browse our catalog of available properties. Use filters to narrow down by type, price range, or location. You can also view properties on the interactive map.",
        icon: (
            <svg xmlns="http://www.w3.org/2000/svg" width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <circle cx="11" cy="11" r="8" />
                <path d="m21 21-4.3-4.3" />
            </svg>
        ),
        link: "/listing",
        linkText: "Start Exploring"
    },
    {
        number: "02",
        title: "Select a Property",
        description: "Found something you like? Click on any property to see full details including photos, location on the map, amenities, and reviews from other users.",
        icon: (
            <svg xmlns="http://www.w3.org/2000/svg" width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <path d="m3 9 9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z" />
                <polyline points="9 22 9 12 15 12 15 22" />
            </svg>
        ),
        link: "/listing",
        linkText: "View Properties"
    },
    {
        number: "03",
        title: "Check Availability",
        description: "Select your check-in and check-out dates. The system will verify if the property is available for your desired dates in real time.",
        icon: (
            <svg xmlns="http://www.w3.org/2000/svg" width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <rect width="18" height="18" x="3" y="4" rx="2" ry="2" />
                <line x1="16" x2="16" y1="2" y2="6" />
                <line x1="8" x2="8" y1="2" y2="6" />
                <line x1="3" x2="21" y1="10" y2="10" />
            </svg>
        ),
        link: null,
        linkText: null
    },
    {
        number: "04",
        title: "Set Guest Count",
        description: "Indicate how many guests will be staying. This helps agencies prepare the property and ensures a smooth check-in experience.",
        icon: (
            <svg xmlns="http://www.w3.org/2000/svg" width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2" />
                <circle cx="9" cy="7" r="4" />
                <path d="M22 21v-2a4 4 0 0 0-3-3.87" />
                <path d="M16 3.13a4 4 0 0 1 0 7.75" />
            </svg>
        ),
        link: null,
        linkText: null
    },
    {
        number: "05",
        title: "Review Price & Book",
        description: "Review the total price including all fees. When you're ready, confirm your booking. A rental contract will be automatically generated for you to review and sign.",
        icon: (
            <svg xmlns="http://www.w3.org/2000/svg" width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <line x1="12" x2="12" y1="1" y2="23" />
                <path d="M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6" />
            </svg>
        ),
        link: null,
        linkText: null
    },
    {
        number: "06",
        title: "Manage Your Booking",
        description: "Access all your bookings from your profile. View contract status, make payments via Stripe, and track your upcoming stays — all in one place.",
        icon: (
            <svg xmlns="http://www.w3.org/2000/svg" width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <path d="M15 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7Z" />
                <path d="M14 2v4a2 2 0 0 0 2 2h4" />
                <path d="M10 9H8" />
                <path d="M16 13H8" />
                <path d="M16 17H8" />
            </svg>
        ),
        link: "/my-bookings",
        linkText: "View My Bookings"
    }
]

const Guides = () => {
    return (
        <div className='bg-linear-to-r from-[#F0FDF4] to-white py-16 pt-28'>
            <div className='max-padd-container'>
                {/* Header */}
                <div className='text-center mb-12'>
                    <h2 className='h2 mb-4'>How It Works</h2>
                    <p className='text-gray-500 max-w-2xl mx-auto'>
                        Follow these simple steps to find and book your perfect property with Nestorria.
                    </p>
                </div>

                {/* Steps with images */}
                <div className='grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6 mb-16'>
                    {guideImages.map((item) => (
                        <div
                            key={item.step}
                            className='bg-white rounded-xl ring-1 ring-slate-900/5 overflow-hidden hover:shadow-md transition-shadow'
                        >
                            {/* Image */}
                            <div className='bg-secondary/10 p-4'>
                                <img
                                    src={item.image}
                                    alt={item.title}
                                    className='shadow-xl shadow-slate-900/20 rounded-xl w-full h-48 object-cover'
                                />
                            </div>
                            {/* Content */}
                            <div className='p-5'>
                                <div className='flex items-center gap-3 mb-3'>
                                    <div className='flex items-center justify-center w-10 h-10 rounded-full bg-secondary/10 text-secondary font-bold text-sm'>
                                        {item.step}
                                    </div>
                                    <p className='text-xs text-gray-400 uppercase tracking-wide'>{item.category}</p>
                                </div>
                                <h3 className='h5 mb-2'>{item.title}</h3>
                                <p className='text-sm text-gray-500'>
                                    {steps.find(s => s.number === item.step)?.description}
                                </p>
                                {steps.find(s => s.number === item.step)?.link && (
                                    <Link
                                        to={steps.find(s => s.number === item.step)!.link!}
                                        className='inline-flex items-center gap-1 text-sm font-medium text-secondary hover:underline mt-3'
                                    >
                                        {steps.find(s => s.number === item.step)!.linkText}
                                        <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                                            <path d="M5 12h14" />
                                            <path d="m12 5 7 7-7 7" />
                                        </svg>
                                    </Link>
                                )}
                            </div>
                        </div>
                    ))}
                </div>

                {/* Tips Grid - using remaining blog images */}
                <div className='mb-12'>
                    <h3 className='h3 mb-6 text-center'>Tips & Resources</h3>
                    <div className='grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-5'>
                        {blogs.filter((_, i) => i !== 4).map((blog, index) => (
                            <div
                                key={index}
                                className='relative bg-white rounded-xl ring-1 ring-slate-900/5 overflow-hidden hover:shadow-md transition-shadow'
                            >
                                <div className='bg-secondary/10 p-3'>
                                    <img
                                        src={blog.image}
                                        alt={blog.title}
                                        className='shadow-xl shadow-slate-900/20 rounded-xl w-full h-36 object-cover'
                                    />
                                </div>
                                <div className='p-4'>
                                    <p className='text-xs text-gray-400 mb-1'>{blog.category}</p>
                                    <h5 className='h5 line-clamp-2 text-sm'>{blog.title}</h5>
                                    <p className='text-xs text-gray-500 mt-1 line-clamp-2'>{blog.description}</p>
                                </div>
                            </div>
                        ))}
                    </div>
                </div>

                {/* CTA */}
                <div className='text-center mt-12 p-8 bg-white rounded-xl ring-1 ring-slate-900/5'>
                    <h3 className='h4 mb-3'>Ready to Get Started?</h3>
                    <p className='text-gray-500 mb-4'>Browse our available properties and find your next stay.</p>
                    <Link
                        to="/listing"
                        className='inline-flex items-center gap-2 btn-dark rounded-lg px-6 py-2'
                    >
                        <img src={assets.search} alt="" width={16} className="invert" />
                        Browse Properties
                    </Link>
                </div>
            </div>
        </div>
    )
}

export default Guides