import { useEffect, useState } from "react"
import { useAppContext } from "../context/AppContext"
import { useAuth } from "@clerk/react"
import { assets } from "../assets/data"
import axios from "axios"
import toast from "react-hot-toast"
import { Link } from "react-router-dom"

interface UserReview {
    id: string
    propertyId: string
    rating: number
    comment?: string | null
    isVerified: boolean
    createdAt: string
}

const MyReviews = () => {
    const { user } = useAppContext()
    const { getToken } = useAuth()
    const [reviews, setReviews] = useState<UserReview[]>([])
    const [loading, setLoading] = useState(true)
    const [editingId, setEditingId] = useState<string | null>(null)
    const [editRating, setEditRating] = useState<number>(5)
    const [editComment, setEditComment] = useState<string>("")

    useEffect(() => {
        if (user) fetchMyReviews()
    }, [user])

    const fetchMyReviews = async () => {
        setLoading(true)
        try {
            const token = await getToken()
            const { data } = await axios.get("/api/reviews/me", {
                headers: { Authorization: `Bearer ${token}` }
            })
            setReviews(data)
        } catch (error: any) {
            toast.error("Error loading reviews")
        } finally {
            setLoading(false)
        }
    }

    const handleUpdate = async (reviewId: string) => {
        try {
            const token = await getToken()
            await axios.patch(`/api/reviews/${reviewId}`, {
                rating: editRating,
                comment: editComment.trim() || undefined
            }, { headers: { Authorization: `Bearer ${token}` } })
            toast.success("Review updated")
            setEditingId(null)
            fetchMyReviews()
        } catch (error: any) {
            toast.error(error.response?.data?.message || "Error updating review")
        }
    }

    const handleDelete = async (reviewId: string) => {
        if (!confirm("Are you sure you want to delete this review?")) return
        try {
            const token = await getToken()
            await axios.delete(`/api/reviews/${reviewId}`, {
                headers: { Authorization: `Bearer ${token}` }
            })
            toast.success("Review deleted")
            fetchMyReviews()
        } catch (error: any) {
            toast.error(error.response?.data?.message || "Error deleting review")
        }
    }

    const renderStars = (rating: number) => {
        return Array.from({ length: 5 }, (_, i) => (
            <img key={i} src={assets.star} alt="star" width={14}
                className={i < rating ? "text-amber-400" : "text-gray-300 opacity-30"} />
        ))
    }

    if (!user) {
        return (
            <div className='bg-linear-to-r from-[#F0FDF4] to-white py-16 pt-28'>
                <div className='max-padd-container text-center py-10'>
                    <p className="text-gray-500">Please log in to see your reviews.</p>
                </div>
            </div>
        )
    }

    return (
        <div className='bg-linear-to-r from-[#F0FDF4] to-white py-16 pt-28'>
            <div className='max-padd-container'>
                <h2 className='h2 mb-6'>My Reviews</h2>
                {loading && <p className="text-gray-500 text-center py-10">Loading reviews...</p>}
                {!loading && reviews.length === 0 && (
                    <p className="text-gray-500 text-center py-10">You haven't written any reviews yet.</p>
                )}
                {reviews.map((review) => (
                    <div key={review.id} className='bg-white ring-1 ring-slate-900/5 p-4 rounded-lg mt-3'>
                        {editingId === review.id ? (
                            <div className="space-y-3">
                                <div className="flex items-center gap-2">
                                    <span className="text-sm">Rating:</span>
                                    {Array.from({ length: 5 }, (_, i) => (
                                        <button key={i} onClick={() => setEditRating(i + 1)}>
                                            <img src={assets.star} alt="" width={20}
                                                className={i < editRating ? "text-amber-400" : "text-gray-300 opacity-30"} />
                                        </button>
                                    ))}
                                </div>
                                <textarea value={editComment} onChange={(e) => setEditComment(e.target.value)}
                                    rows={3} maxLength={2000} className="w-full p-2 border rounded text-sm" />
                                <div className="flex gap-2">
                                    <button onClick={() => handleUpdate(review.id)}
                                        className="btn-secondary py-1 px-4 rounded text-sm">Save</button>
                                    <button onClick={() => setEditingId(null)}
                                        className="py-1 px-4 rounded text-sm border">Cancel</button>
                                </div>
                            </div>
                        ) : (
                            <>
                                <div className="flex items-center justify-between">
                                    <Link to={`/listing/${review.propertyId}`} className="h5 hover:underline">
                                        Property {review.propertyId.slice(0, 8)}...
                                    </Link>
                                    <div className="flex gap-2">
                                        <button onClick={() => {
                                            setEditingId(review.id)
                                            setEditRating(review.rating)
                                            setEditComment(review.comment || "")
                                        }} className="text-xs text-secondary hover:underline">Edit</button>
                                        <button onClick={() => handleDelete(review.id)}
                                            className="text-xs text-red-500 hover:underline">Delete</button>
                                    </div>
                                </div>
                                <div className="flex items-center gap-2 mt-1">
                                    <div className="flex">{renderStars(review.rating)}</div>
                                    <span className="text-xs text-gray-400">
                                        {new Date(review.createdAt).toLocaleDateString()}
                                    </span>
                                </div>
                                {review.comment && <p className="text-sm text-gray-600 mt-2">{review.comment}</p>}
                            </>
                        )}
                    </div>
                ))}
            </div>
        </div>
    )
}

export default MyReviews