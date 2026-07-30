import { useEffect, useState } from "react"
import { useAppContext } from "../context/AppContext"
import { useParams } from "react-router-dom"
import PropertyImages from "../components/PropertyImages"
import { assets } from "../assets/data"
import axios from "axios"
import { useAuth } from "@clerk/react"
import toast from "react-hot-toast"
import PropertyMap from "../components/PropertyMap";

interface Review {
    id: string;
    propertyId: string;
    userId: string;
    userName: string;
    userImage: string;
    rating: number;
    comment?: string | null;
    isVerified: boolean;
    createdAt: string;
}

const PropertyDetails = () => {

    const { properties, currency, navigate, user, favoriteIds, toggleFavorite } = useAppContext()
    const { getToken } = useAuth()
    const [property, setProperty] = useState<any>(null)
    const { id } = useParams()
    const [checkInDate, setCheckInDate] = useState<any>(null)
    const [checkOutDate, setCheckOutDate] = useState<any>(null)
    const [guests, setGuests] = useState<number>(1)
    const [isAvailable, setIsAvailable] = useState<boolean | null>(null)

    // Reviews state
    const [reviews, setReviews] = useState<Review[]>([])
    const [reviewsLoading, setReviewsLoading] = useState<boolean>(true)
    const [newRating, setNewRating] = useState<number>(5)
    const [newComment, setNewComment] = useState<string>("")
    const [submittingReview, setSubmittingReview] = useState<boolean>(false)
    const [hoveredStar, setHoveredStar] = useState<number>(0)

    const checkAvailability = async () => {
        try {
            if (!checkInDate || !checkOutDate) {
                toast.error("Please select check-in and check-out dates");
                return
            }
            if (checkInDate >= checkOutDate) {
                toast.error("checkInDate should be less than checkOutDate");
                return
            }
            const { data } = await axios.post(`/api/bookings/check-availability`, {
                propertyId: id,
                checkInDate,
                checkOutDate
            }, {
                headers: { Authorization: `Bearer ${await getToken()}` },
            });
            if (data.isAvailable) {
                setIsAvailable(true);
                toast.success("Property is available");
            } else {
                setIsAvailable(false);
                toast.error("Property is not available for the selected dates");
            }
        } catch (error: any) {
            setIsAvailable(false);
            toast.error(error.message);
        }
    };

    // Book Property if isAvailable
    const onSubmitHandler = async (e: any) => {
        try {
            e.preventDefault()
            if (!isAvailable) {
                return checkAvailability()
            } else {
                await axios.post(`/api/bookings`, {
                    propertyId: id,
                    checkInDate,
                    checkOutDate,
                    guests,
                    paymentMethod: "Pay at check-in"
                }, {
                    headers: { Authorization: `Bearer ${await getToken()}` },
                });
                toast.success("Booking confirmed successfully!");
                navigate('/my-bookings')
                scrollTo(0, 0)
            }
        } catch (error: any) {
            toast.error(error.response?.data?.message || error.message);
        }
    }

    // Fetch reviews for this property (public endpoint)
    const fetchReviews = async () => {
        setReviewsLoading(true)
        try {
            const { data } = await axios.get(`/api/properties/${id}/reviews`)
            setReviews(data)
        } catch (error: any) {
            console.warn("Error loading reviews:", error.message)
        } finally {
            setReviewsLoading(false)
        }
    }

    // Submit a new review
    const submitReview = async () => {
        if (!user) {
            toast.error("Inicia sesión para dejar una reseña")
            return
        }
        if (newRating < 1 || newRating > 5) {
            toast.error("La calificación debe ser entre 1 y 5")
            return
        }
        setSubmittingReview(true)
        try {
            await axios.post(`/api/properties/${id}/reviews`, {
                rating: newRating,
                comment: newComment.trim() || undefined
            }, {
                headers: { Authorization: `Bearer ${await getToken()}` },
            })
            toast.success("Reseña publicada exitosamente")
            setNewRating(5)
            setNewComment("")
            fetchReviews() // Recargar reviews
        } catch (error: any) {
            const message = error.response?.data?.message || error.message
            if (error.response?.status === 409) {
                toast.error("Ya has publicado una reseña para esta propiedad")
            } else {
                toast.error(message)
            }
        } finally {
            setSubmittingReview(false)
        }
    }

    useEffect(() => {
        const property = properties.find((property) => property._id === id)
        property && setProperty(property)
    }, [properties])

    useEffect(() => {
        if (id) {
            fetchReviews()
        }
    }, [id])

    // Calcular rating promedio de las reviews cargadas (fallback si el property no tiene)
    const reviewCount = reviews.length > 0 ? reviews.length : (property?.reviewCount ?? 0)
    const averageRating = reviews.length > 0
        ? reviews.reduce((sum, r) => sum + r.rating, 0) / reviews.length
        : (property?.averageRating ?? null)

    // Renderizar estrellas interactivas para el formulario
    const renderInteractiveStars = () => {
        const stars = []
        for (let i = 1; i <= 5; i++) {
            const isActive = i <= (hoveredStar || newRating)
            stars.push(
                <button
                    key={i}
                    type="button"
                    onMouseEnter={() => setHoveredStar(i)}
                    onMouseLeave={() => setHoveredStar(0)}
                    onClick={() => setNewRating(i)}
                    className="cursor-pointer transition-transform hover:scale-110"
                >
                    <img
                        src={assets.star}
                        alt={`${i} star`}
                        width={24}
                        className={isActive ? "text-amber-400" : "text-gray-300 opacity-40"}
                    />
                </button>
            )
        }
        return stars
    }

    // Renderizar estrellas de solo lectura
    const renderReadonlyStars = (rating: number) => {
        const stars = []
        for (let i = 1; i <= 5; i++) {
            stars.push(
                <img
                    key={i}
                    src={assets.star}
                    alt="star"
                    width={14}
                    className={i <= rating ? "text-amber-400" : "text-gray-300 opacity-30"}
                />
            )
        }
        return stars
    }

    // Formatear fecha relativa
    const formatRelativeDate = (dateStr: string) => {
        const date = new Date(dateStr)
        const now = new Date()
        const diffMs = now.getTime() - date.getTime()
        const diffDays = Math.floor(diffMs / (1000 * 60 * 60 * 24))
        if (diffDays === 0) return "Hoy"
        if (diffDays === 1) return "Ayer"
        if (diffDays < 7) return `Hace ${diffDays} días`
        if (diffDays < 30) return `Hace ${Math.floor(diffDays / 7)} semanas`
        if (diffDays < 365) return `Hace ${Math.floor(diffDays / 30)} meses`
        return `Hace ${Math.floor(diffDays / 365)} años`
    }

    return (
        property && (
            <div className='bg-linear-to-r from-[#F0FDF4] to-white py-16 pt-28'>
                <div className='max-padd-container'>
                    {/* IMAGE */}
                    <PropertyImages property={property} />
                    {/* CONTAINER */}
                    <div className='flex flex-col xl:flex-row gap-8 mt-6'>
                        {/* LEFT SIDE */}
                        <div className='p-4 flex-2 rounded-xl border border-slate-900/50'>
                            <p className='flexStart gap-x-2'>
                                <img
                                    src={assets.pin}
                                    alt=""
                                    width={19}
                                />
                                <span>{property.address}</span>
                            </p>
                            {property.location?.neighborhood && (
                                <p className="text-gray-500 text-sm mt-1 ml-6">{property.location.neighborhood}</p>
                            )}
                            {property.location?.postalCode && (
                                <p className="text-gray-400 text-xs ml-6">Postal Code: {property.location.postalCode}</p>
                            )}
                            {property.location?.latitude != null && property.location?.longitude != null && (
                                <div className="mt-4 relative overflow-hidden rounded-lg">
                                    <h3 className="font-semibold text-lg mb-3">Map location</h3>
                                    <PropertyMap
                                        properties={[property]}
                                        center={[property.location.latitude, property.location.longitude]}
                                        zoom={15}
                                        height="300px"
                                    />
                                </div>
                            )}
                            <div className='flex justify-between flex-col md:flex-row sm:items-end mt-3'>
                                <h3 className='h3'>{property.title}</h3>
                                <div className='bold-18'>
                                    {currency}{property.price.sale} | {currency}{property.price.rent}.00/night
                                </div>
                            </div>
                            {/* FAVORITE BUTTON */}
                            {user && (
                                <button
                                    onClick={() => toggleFavorite(property._id)}
                                    className='flex items-center gap-2 px-4 py-2 rounded-lg ring-1 ring-slate-900/10 hover:bg-secondary/10 transition-all'
                                >
                                    <svg
                                        xmlns="http://www.w3.org/2000/svg"
                                        width="22"
                                        height="22"
                                        viewBox="0 0 24 24"
                                        fill={favoriteIds.has(property._id) ? "currentColor" : "none"}
                                        stroke="currentColor"
                                        strokeWidth="2"
                                        strokeLinecap="round"
                                        strokeLinejoin="round"
                                        className={favoriteIds.has(property._id) ? "text-red-500" : "text-gray-400"}
                                    >
                                        <path d="M19 14c1.49-1.46 3-3.21 3-5.5A5.5 5.5 0 0 0 16.5 3c-1.76 0-3 .5-4.5 2-1.5-1.5-2.74-2-4.5-2A5.5 5.5 0 0 0 2 8.5c0 2.3 1.5 4.05 3 5.5l7 7Z" />
                                    </svg>
                                    <span className='text-sm font-medium'>
                                        {favoriteIds.has(property._id) ? 'Saved' : 'Save'}
                                    </span>
                                </button>
                            )}
                            <div className='flex justify-between items-start my-1'>
                                <h4 className='h4 text-secondary'>{property.propertyType}</h4>
                                {/* DYNAMIC RATING */}
                                {averageRating != null && reviewCount > 0 ? (
                                    <div className='flex items-center gap-1.5 text-amber-400'>
                                        <h4 className='bold-18 text-black'>{averageRating.toFixed(1)}</h4>
                                        <div className='flex items-center gap-0.5'>
                                            {renderReadonlyStars(Math.round(averageRating))}
                                        </div>
                                        <span className='text-xs text-gray-500'>({reviewCount})</span>
                                    </div>
                                ) : (
                                    <div className='flex items-center gap-x-2 text-amber-400 relative top-1.5'>
                                        <h4 className='bold-18 relative bottom-0.5 text-black'>Sin reviews</h4>
                                    </div>
                                )}
                            </div>
                            <div className='flex gap-x-4 mt-3'>
                                <p className='flexCenter gap-x-2 border-r border-slate-900/50 pr-4 font-medium'>
                                    <img
                                        src={assets.bed}
                                        alt="bed icon"
                                        width={18}
                                    />
                                    {property.facilities.bedrooms}
                                </p>
                                <p className='flexCenter gap-x-2 border-r border-slate-900/50 pr-4 font-medium'>
                                    <img
                                        src={assets.bath}
                                        alt="bathroom"
                                        width={18}
                                    />
                                    {property.facilities.bathrooms}
                                </p>
                                <p className='flexCenter gap-x-2 border-r border-slate-900/50 pr-4 font-medium'>
                                    <img
                                        src={assets.car}
                                        alt="garage icon"
                                        width={18}
                                    />
                                    {property.facilities.garages}
                                </p>
                                <p className='flexCenter gap-x-2 border-r border-slate-900/50 pr-4 font-medium'>
                                    <img
                                        src={assets.ruler}
                                        alt="ruler icon"
                                        width={18}
                                    />
                                    {property.area}
                                </p>
                            </div>
                            <div className="mt-6">
                                <h4 className='h4 mt-4 mb-1'>Property Details</h4>
                                <p className='mb-4'>{property.description}</p>
                            </div>
                            <h4 className='h4 mt-6 mb-2'>Amenities</h4>
                            <div className='flex flex-wrap gap-3 items-start'>
                                {property.amenities.map((amenity: any, index: any) => (
                                    <div
                                        key={index}
                                        className='p-3 py-1 rounded-lg bg-secondary/10 ring-1 ring-slate-900/10 text-sm'
                                    >
                                        {amenity}
                                    </div>
                                ))}
                            </div>
                            {/* FORM | CHECK AVAILABILITY */}
                            <form
                                onSubmit={onSubmitHandler}
                                className='text-slate-500 bg-secondary/10 rounded-lg px-6 py-4 flex flex-col lg:flex-row gap-4 max-w-md lg:max-w-full ring-1 ring-slate-900/5 relative mt-10'
                            >
                                <div className='flex flex-col w-full'>
                                    <div className='flex items-center gap-2'>
                                        <img
                                            src={assets.calendar}
                                            alt='calendarIcon'
                                            width={20}
                                        />
                                        <label htmlFor='checkInDate'>Check In</label>
                                    </div>
                                    <input
                                        onChange={(e) => { setCheckInDate(e.target.value); setIsAvailable(null) }}
                                        value={checkInDate ?? ''}
                                        min={new Date().toISOString().split("T")[0]}
                                        type='date'
                                        id='checkInDate'
                                        className='rounded bg-secondary/10 border border-gray-200 px-3 py-1.5 text-sm outline-none'
                                    />
                                </div>
                                <div className='flex flex-col w-full'>
                                    <div className='flex items-center gap-2'>
                                        <img
                                            src={assets.calendar}
                                            alt='calendarIcon'
                                            width={20}
                                        />
                                        <label htmlFor='checkOutDate'>Check Out</label>
                                    </div>
                                    <input
                                        onChange={(e) => { setCheckOutDate(e.target.value); setIsAvailable(null) }}
                                        value={checkOutDate ?? ''}
                                        min={checkInDate}
                                        type='date'
                                        id='checkOutDate'
                                        disabled={!checkInDate}
                                        className='rounded bg-secondary/10 border border-gray-200 px-3 py-1.5 text-sm outline-none'
                                    />
                                </div>
                                <div className='flex flex-col w-full'>
                                    <div className='flex items-center gap-2'>
                                        <img
                                            src={assets.user}
                                            alt='userIcon'
                                            width={20}
                                        />
                                        <label htmlFor='guests'>Guests</label>
                                    </div>
                                    <input
                                        onChange={(e) => setGuests(Number(e.target.value))}
                                        value={guests}
                                        type='number'
                                        id='guests'
                                        min={1}
                                        max={8}
                                        className='rounded bg-secondary/10 border border-gray-200 px-3 py-1.5 text-sm outline-none'
                                        placeholder='0'
                                    />
                                </div>
                                <button
                                    type='submit'
                                    className='flexCenter gap-1 rounded-md btn-dark min-w-44'
                                >
                                    <img
                                        src={assets.search}
                                        alt='searchIcon'
                                        width={20}
                                        className='invert'
                                    />
                                    <span>{isAvailable ? 'Book Property' : 'Check Dates'}</span>
                                </button>
                            </form>
                            {/* SECCION DE REVIEWS */}
                            <div className='mt-10 border-t border-slate-900/10 pt-8'>
                                <h4 className='h4 mb-6'>Reviews & Ratings</h4>

                                {/* Resumen de rating */}
                                {averageRating != null && reviewCount > 0 && (
                                    <div className='flex items-center gap-4 mb-6 p-4 bg-secondary/10 rounded-lg'>
                                        <div className='text-center'>
                                            <div className='bold-28 text-secondary'>{averageRating.toFixed(1)}</div>
                                            <div className='flex items-center gap-0.5 mt-1'>
                                                {renderReadonlyStars(Math.round(averageRating))}
                                            </div>
                                            <p className='text-xs text-gray-500 mt-1'>
                                                {reviewCount} {reviewCount === 1 ? 'review' : 'reviews'}
                                            </p>
                                        </div>
                                    </div>
                                )}

                                {/* Lista de reviews */}
                                {reviewsLoading ? (
                                    <p className='text-gray-500 text-sm'>Loading reviews...</p>
                                ) : reviews.length === 0 ? (
                                    <p className='text-gray-500 text-sm mb-6'>
                                        There aren't any reviews yet. Be the first to leave a review.
                                    </p>
                                ) : (
                                    <div className='space-y-4 mb-8'>
                                        {reviews.map((review) => (
                                            <div
                                                key={review.id}
                                                className='p-4 rounded-lg border border-slate-900/10'
                                            >
                                                <div className='flex items-start gap-3'>
                                                    <img
                                                        src={review.userImage || "https://images.unsplash.com/photo-1560250097-0b93528c311a"}
                                                        alt={review.userName}
                                                        className='h-10 w-10 rounded-full object-cover'
                                                    />
                                                    <div className='flex-1'>
                                                        <div className='flex items-center gap-2'>
                                                            <h5 className='font-medium text-sm'>{review.userName}</h5>
                                                            {review.isVerified && (
                                                                <span className='text-xs bg-green-100 text-green-700 px-1.5 py-0.5 rounded'>
                                                                    Verified
                                                                </span>
                                                            )}
                                                        </div>
                                                        <div className='flex items-center gap-2 mt-0.5'>
                                                            <div className='flex items-center gap-0.5'>
                                                                {renderReadonlyStars(review.rating)}
                                                            </div>
                                                            <span className='text-xs text-gray-400'>
                                                                {formatRelativeDate(review.createdAt)}
                                                            </span>
                                                        </div>
                                                        {review.comment && (
                                                            <p className='text-sm text-gray-600 mt-2'>
                                                                {review.comment}
                                                            </p>
                                                        )}
                                                    </div>
                                                </div>
                                            </div>
                                        ))}
                                    </div>
                                )}

                                {/* Formulario para nueva review */}
                                {user ? (
                                    <div className='p-4 rounded-lg border border-slate-900/10 bg-secondary/5'>
                                        <h5 className='font-medium mb-3'>Leave your review</h5>
                                        <div className='flex items-center gap-2 mb-3'>
                                            <span className='text-sm text-gray-500'>Rating:</span>
                                            <div className='flex items-center gap-1'>
                                                {renderInteractiveStars()}
                                            </div>
                                            <span className='text-sm font-medium ml-2'>
                                                {hoveredStar || newRating}/5
                                            </span>
                                        </div>
                                        <textarea
                                            value={newComment}
                                            onChange={(e) => setNewComment(e.target.value)}
                                            placeholder="Tell us about your experience (optional)"
                                            rows={3}
                                            maxLength={2000}
                                            className='w-full p-3 border border-gray-300 rounded-lg text-sm outline-none focus:border-secondary resize-none'
                                        />
                                        <div className='flex items-center justify-between mt-3'>
                                            <span className='text-xs text-gray-400'>
                                                {newComment.length}/2000
                                            </span>
                                            <button
                                                onClick={submitReview}
                                                disabled={submittingReview || newRating < 1}
                                                className='btn-secondary rounded-lg px-6 py-1.5 text-sm disabled:opacity-50'
                                            >
                                                {submittingReview ? 'Sending...' : 'Publish'}
                                            </button>
                                        </div>
                                    </div>
                                ) : (
                                    <p className='text-sm text-gray-500'>
                                        Login to make a review.
                                    </p>
                                )}
                            </div>
                        </div>
                        {/* RIGHT SIDE */}
                        <div className='flex-1 max-w-sm'>
                            <div className='p-6 rounded-xl border border-slate-900/10'>
                                <h4 className='h4 mb-3'>Contact Agent</h4>
                                <form className='flex flex-col gap-4'>
                                    <input
                                        type="text"
                                        placeholder="Your Name"
                                        className='p-2 py-1 border border-gray-300 rounded-md text-sm'
                                        required
                                    />
                                    <input
                                        type="text"
                                        placeholder="Your Email"
                                        className='p-2 py-1 border border-gray-300 rounded-md text-sm'
                                        required
                                    />
                                    <textarea
                                        rows={4}
                                        placeholder="Your Message"
                                        className='p-2 py-1 border border-gray-300 rounded-md text-sm'
                                        required
                                    />
                                    <button
                                        type='submit'
                                        className='btn-secondary rounded-lg py-1.5'
                                    >
                                        Send Message
                                    </button>
                                </form>
                                <h4 className='h4 mb-3 mt-8'>
                                    For Buying Contact
                                </h4>
                                <div className='text-sm w-80 divide-y divide-gray-500/30 border border-gray-500/30 rounded'>
                                    <div className='flex items-start justify-between p-3'>
                                        <div>
                                            <div className='flex items-center space-x-2'>
                                                <h5>{property.agency.name}</h5>
                                                <p>Agency</p>
                                            </div>
                                            <p>Agency Office</p>
                                        </div>
                                        <img
                                            src={property.agency.owner.image}
                                            alt='agencyImage'
                                            className='h-10 w-10 rounded-full'
                                        />
                                    </div>
                                    <div className='flexStart gap-2 p-1.5'>
                                        <div className='bg-green-500/20 p-1 rounded-full border border-green-500/30'>
                                            <img
                                                src={assets.phone}
                                                alt='phoneIcon'
                                                width={14}
                                            />
                                        </div>
                                        <p>{property.agency.contact}</p>
                                    </div>
                                    <div className='flexStart gap-2 p-1.5'>
                                        <div className='bg-green-500/20 p-1 rounded-full border border-green-500/30'>
                                            <img
                                                src={assets.mail}
                                                alt='mailIcon'
                                                width={14}
                                            />
                                        </div>
                                        <p>{property.agency.email}</p>
                                    </div>
                                    <div className='flex items-center divide-x divide-gray-500/30'>
                                        <button className='flex items-center justify-center gap-2 w-1/2 py-3 cursor-pointer'>
                                            <img
                                                src={assets.mail}
                                                alt='mailIcon'
                                                width={19}
                                            />
                                            Send Email
                                        </button>
                                        <button className='flex items-center justify-center gap-2 w-1/2 py-3 cursor-pointer'>
                                            <img
                                                src={assets.phone}
                                                alt='phoneIcon'
                                                width={19}
                                            />
                                            Call Now
                                        </button>
                                    </div>
                                </div>

                            </div>
                        </div>
                    </div>
                </div>
            </div>
        )
    )
}

export default PropertyDetails