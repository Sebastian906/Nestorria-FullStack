import { useEffect, useState } from "react"
import { useAppContext } from "../context/AppContext"
import { assets } from "../assets/data"
import axios from "axios"
import { useAuth } from "@clerk/react"
import toast from "react-hot-toast"

interface ApiBooking {
    id: string;
    property: {
        title: string;
        address: string;
        images: string[];
    };
    checkInDate: string;
    checkOutDate: string;
    totalPrice: number;
    guests: number;
    status: string;
    paymentMethod: string;
    isPaid: boolean;
}

const MyBookings = () => {

    const [bookings, setBookings] = useState<ApiBooking[]>([])
    const { currency, user } = useAppContext()
    const { getToken } = useAuth()
    const [loading, setLoading] = useState(false)

    const getUserBookings = async () => {
        setLoading(true)
        try {
            const response = await axios.get("/api/bookings/me", {
                headers: { Authorization: `Bearer ${await getToken()}` }
            });
            setBookings(response.data);
        } catch (error: any) {
            toast.error(error.response?.data?.message || error.message)
        } finally {
            setLoading(false)
        }
    }

    useEffect(() => {
        if (user) {
            getUserBookings()
        }
    }, [user])

    return (
        <div className='bg-linear-to-r from-[#F0FDF4] to-white py-16 pt-28 w-full'>
            <div className='max-padd-container'>
                {loading && (
                    <p className="text-gray-500 text-center py-10">Loading bookings...</p>
                )}
                {!loading && bookings.length === 0 && (
                    <p className="text-gray-500 text-center py-10">No bookings found.</p>
                )}
                {bookings?.map((booking) => (
                    <div
                        key={booking.id}
                        className='bg-white ring-1 ring-slate-900/5 p-2 pr-4 mt-3 rounded-lg'
                    >
                        {/* PROPERTY LIST */}
                        <div className='flexStart gap-3 mb-3'>
                            <img
                                src={booking.property?.images?.[0]}
                                alt="property image"
                                className='h-14 w-26 object-cover rounded-lg'
                            />
                            <div className=''>
                                <h5 className='h5 capitalize line-clamp-1'>
                                    {booking.property?.title}
                                </h5>
                                <div className='flex gap-4'>
                                    <div className='flex items-center gap-x-2'>
                                        <h5 className='medium-14'>Guests:</h5>
                                        <p>{booking.guests}</p>
                                    </div>
                                    <div className='flex items-center gap-x-2'>
                                        <h5 className='medium-14'>Total:</h5>
                                        <p className='text-gray-400 text-sm'>{currency}{booking.totalPrice.toLocaleString()}</p>
                                    </div>
                                </div>
                                <div className='flex items-center gap-1'>
                                    <img
                                        src={assets.pin}
                                        alt="pinIcon"
                                        width={13}
                                    />
                                    <p className='text-sm'>{booking.property.address}</p>
                                </div>
                            </div>
                        </div>
                        {/* BOOKING SUMMARY */}
                        <div className='flex flex-col lg:flex-row justify-between items-start lg:items-center gap-3 border-t border-gray-300 pt-3'>
                            <div className='flex gap-2 gap-x-4 flex-wrap'>
                                <div className='flex items-center gap-x-2'>
                                    <h5 className='medium-14'>Booking ID:</h5>
                                    <p className='text-gray-400 text-sm break-all'>{booking.id}</p>
                                </div>
                                <div className='flex items-center gap-x-2'>
                                    <h5 className='medium-14'>Check In:</h5>
                                    <p className='text-gray-400 text-sm break-all'>{new Date(booking.checkInDate).toDateString()}</p>
                                </div>
                                <div className='flex items-center gap-x-2'>
                                    <h5 className='medium-14'>Check Out:</h5>
                                    <p className='text-gray-400 text-sm break-all'>{new Date(booking.checkOutDate).toDateString()}</p>
                                </div>
                            </div>
                            <div className='flex gap-2 gap-x-3'>
                                <div className='flex items-center gap-x-2'>
                                    <h5 className='medium-14'>Payment:</h5>
                                    <div className='flex items-center gap-1'>
                                        <span className={`min-w-2.5 h-2.5 rounded-full ${booking.isPaid ? "bg-green-500" : "bg-yellow-500"}`} />
                                        <p>{booking.isPaid ? "Paid" : "Unpaid"}</p>
                                    </div>
                                </div>
                                {!booking.isPaid && (
                                    <button className='btn-secondary py-1! text-xs! rounded-sm'>Pay Now</button>
                                )}
                            </div>
                        </div>
                    </div>
                ))}
            </div>
        </div>
    )
}

export default MyBookings