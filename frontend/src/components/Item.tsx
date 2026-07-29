import { Link } from "react-router-dom"
import { assets } from "../assets/data"
import { useAppContext } from "../context/AppContext"

{/* @ts-ignore */ }
const Item = ({ property }) => {

    const { currency, user, favoriteIds, toggleFavorite } = useAppContext()

    const handleFavoriteClick = async (e: React.MouseEvent) => {
        e.preventDefault()
        e.stopPropagation()
        if (!user) {
            return
        }
        await toggleFavorite(property._id)
    }

    // Renderizar estrellas llenas, medias y vacías
    const renderStars = (rating: number) => {
        const stars = []
        for (let i = 1; i <= 5; i++) {
            if (i <= Math.floor(rating)) {
                // Estrella llena
                stars.push(
                    <img
                        key={i}
                        src={assets.star}
                        alt="star"
                        width={14}
                        className="text-amber-400"
                    />
                )
            } else if (i - 0.5 <= rating) {
                // Media estrella (usamos la misma imagen con opacidad reducida)
                stars.push(
                    <img
                        key={i}
                        src={assets.star}
                        alt="half star"
                        width={14}
                        className="text-amber-400 opacity-50"
                    />
                )
            } else {
                // Estrella vacía
                stars.push(
                    <img
                        key={i}
                        src={assets.star}
                        alt="empty star"
                        width={14}
                        className="text-gray-300 opacity-30"
                    />
                )
            }
        }
        return stars
    }

    return (
        <Link
            to={`/listing/${property._id}`}
            className='block rounded-lg bg-white ring-1 ring-slate-900/5'
        >
            {/* IMAGE */}
            <div className='relative'>
                <img
                    src={property.images[0]}
                    alt={property.title}
                    className='h-52 w-full aspect-square object-cover rounded-t-xl'
                />
                {/* FAVORITE ICON */}
                {user && (
                    <button
                        onClick={handleFavoriteClick}
                        className='absolute top-3 right-3 p-2 rounded-full bg-white/80 backdrop-blur-sm hover:bg-white transition-all shadow-sm'
                    >
                        <svg
                            xmlns="http://www.w3.org/2000/svg"
                            width="18"
                            height="18"
                            viewBox="0 0 24 24"
                            fill={favoriteIds.has(property._id) ? "currentColor" : "none"}
                            stroke="currentColor"
                            strokeWidth="2"
                            strokeLinecap="round"
                            strokeLinejoin="round"
                            className={favoriteIds.has(property._id) ? "text-red-500" : "text-gray-500"}
                        >
                            <path d="M19 14c1.49-1.46 3-3.21 3-5.5A5.5 5.5 0 0 0 16.5 3c-1.76 0-3 .5-4.5 2-1.5-1.5-2.74-2-4.5-2A5.5 5.5 0 0 0 2 8.5c0 2.3 1.5 4.05 3 5.5l7 7Z" />
                        </svg>
                    </button>
                )}
            </div>
            {/* INFO */}
            <div className='p-3'>
                <div className='flexBetween'>
                    <h5 className='bold-16 my-1'>{property.propertyType}</h5>
                    <div className='bold-15 text-secondary'>
                        {currency}{property.price.sale} | {currency}{property.price.rent}.00 <span className='text-xs'>/night</span>
                    </div>
                </div>
                <h4 className='h4 line-clamp-1'>{property.title}</h4>
                {/* RATING - Solo se muestra si hay reviews */}
                {property.reviewCount > 0 && property.averageRating != null && (
                    <div className='flex items-center gap-1.5 mt-1.5'>
                        <div className='flex items-center gap-0.5'>
                            {renderStars(property.averageRating)}
                        </div>
                        <span className='text-xs text-gray-500 font-medium'>
                            {property.averageRating.toFixed(1)}
                        </span>
                        <span className='text-xs text-gray-400'>
                            ({property.reviewCount} {property.reviewCount === 1 ? 'review' : 'reviews'})
                        </span>
                    </div>
                )}
                <div className='flexCenter gap-4 py-2'>
                    <p className='flexCenter gap-x-2 border-r border-slate-900/50 pr-4 font-medium'>
                        <img
                            src={assets.bed}
                            alt='facilitiesIcon'
                            width={21}
                        />
                        {property.facilities.bedrooms}
                    </p>
                    <p className='flexCenter gap-x-2 border-r border-slate-900/50 pr-4 font-medium'>
                        <img
                            src={assets.bath}
                            alt='facilitiesIcon'
                            width={21}
                        />
                        {property.facilities.bathrooms}
                    </p>
                    <p className='flexCenter gap-x-2 border-r border-slate-900/50 pr-4 font-medium'>
                        <img
                            src={assets.car}
                            alt='facilitiesIcon'
                            width={21}
                        />
                        {property.facilities.garages}
                    </p>
                    <p className='flexCenter gap-x-2 pr-4 font-medium'>
                        <img
                            src={assets.ruler}
                            alt='facilitiesIcon'
                            width={21}
                        />
                        {property.area}
                    </p>
                </div>
                <p className='pt-2 mb-4 line-clamp-2'>
                    {property.description}
                </p>
            </div>
        </Link>
    )
}

export default Item