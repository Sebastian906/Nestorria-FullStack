import { useEffect, useState, useCallback } from "react"
import { useAppContext } from "../context/AppContext"
import { assets, type ContractSummary } from "../assets/data"
import axios from "axios"
import { useAuth } from "@clerk/react"
import toast from "react-hot-toast"
import { formatCurrency, formatDate } from "../utils/format"
import { useTranslation } from "react-i18next"
import { serverMsg } from "../services/serverMsg"

interface ApiBooking {
    id: string;
    property: {
        id: string;
        title: string;
        address: string;
        images: string[];
    };
    agency: {
        id: string;
        name: string;
    };
    checkInDate: string;
    checkOutDate: string;
    totalPrice: number;
    guests: number;
    status: string;
    paymentMethod: string;
    isPaid: boolean;
}

interface AgencyDashboardResponse {
    totalBookings: number;
    totalRevenue: number;
    bookings: ApiBooking[];
}

const MyBookings = () => {

    const [bookings, setBookings] = useState<ApiBooking[]>([])
    const [contracts, setContracts] = useState<Map<string, ContractSummary>>(new Map())
    const { currency, user, isOwner, navigate } = useAppContext()
    const { getToken } = useAuth()
    const { t } = useTranslation(["booking", "property"])
    const [loading, setLoading] = useState(true)
    const [creatingContract, setCreatingContract] = useState<string | null>(null)

    const getUserBookings = useCallback(async () => {
        setLoading(true)
        try {
            const token = await getToken()
            if (isOwner) {
                // Agency owner: fetch agency bookings
                const { data } = await axios.get<AgencyDashboardResponse>("/api/bookings/agency", {
                    headers: { Authorization: `Bearer ${token}` }
                });
                setBookings(data.bookings);
            } else {
                // Tenant: fetch own bookings
                const { data } = await axios.get<ApiBooking[]>("/api/bookings/me", {
                    headers: { Authorization: `Bearer ${token}` }
                });
                setBookings(data);
            }
        } catch (error: any) {
            toast.error(serverMsg(error, "booking:errors.generic"))
        } finally {
            setLoading(false)
        }
    }, [getToken, isOwner])

    const getUserContracts = useCallback(async () => {
        try {
            const token = await getToken()
            const endpoint = isOwner ? "/api/contracts/agency" : "/api/contracts/me"
            const { data } = await axios.get(endpoint, {
                headers: { Authorization: `Bearer ${token}` }
            });
            const contractMap = new Map<string, ContractSummary>()
            data.forEach((c: ContractSummary) => contractMap.set(c.bookingId, c))
            setContracts(contractMap)
        } catch (error: any) {
            // Contracts endpoint may not exist yet — fail silently
            console.warn("Could not load contracts:", error.message)
        }
    }, [getToken, isOwner])

    const handleGenerateContract = async (bookingId: string) => {
        setCreatingContract(bookingId)
        try {
            const token = await getToken()
            const { data } = await axios.post("/api/contracts",
                { bookingId, contractType: "RENTAL" },
                { headers: { Authorization: `Bearer ${token}` } }
            );
            toast.success(t("booking:success.contractGenerated"))
            navigate(`/contracts/${data.id}`)
            scrollTo(0, 0)
        } catch (error: any) {
            if (error.response?.status === 409) {
                toast.error(t("booking:errors.alreadySigned"))
                getUserContracts()
            } else {
                toast.error(serverMsg(error, "booking:errors.generic"))
            }
        } finally {
            setCreatingContract(null)
        }
    }

    const handleViewContract = (contractId: string) => {
        navigate(`/contracts/${contractId}`)
        scrollTo(0, 0)
    }

    const getStatusLabel = (status: string) => {
        switch (status) {
            case "PENDING_SIGNATURE": return { text: t("booking:status.pending"), color: "bg-yellow-500" }
            case "SIGNED": return { text: t("booking:status.signed"), color: "bg-green-500" }
            case "DRAFT": return { text: t("booking:status.draft"), color: "bg-gray-400" }
            case "EXPIRED": return { text: t("booking:status.expired"), color: "bg-red-500" }
            default: return { text: status, color: "bg-gray-400" }
        }
    }

    // Stripe payment
    const handlePayment = async (bookingId: string) => {
        try {
            const token = await getToken()
            const { data } = await axios.post("/api/bookings/stripe", { bookingId }, {
                headers: { Authorization: `Bearer ${token}` },
            });

            if (data.success) {
                window.location.href = data.url
            } else {
                toast.error(serverMsg({ response: { data } }, "booking:errors.generic"))
            }
        } catch (error: any) {
            toast.error(serverMsg(error, "booking:errors.generic"))
        }
    }

    useEffect(() => {
        if (user) {
            getUserBookings()
            getUserContracts()
        }
    }, [user, getUserBookings, getUserContracts])

    return (
        <div className='bg-linear-to-r from-[#F0FDF4] to-white py-16 pt-28 w-full'>
            <div className='max-padd-container'>
                <h2 className='h2 mb-6'>
                    {isOwner ? t("booking:page.agencyTitle") : t("booking:page.title")}
                </h2>
                {loading && (
                    <p className="text-gray-500 text-center py-10">{t("booking:page.loading")}</p>
                )}
                {!loading && bookings.length === 0 && (
                    <p className="text-gray-500 text-center py-10">{t("booking:page.empty")}</p>
                )}
                {bookings?.map((booking) => {
                    const contract = contracts.get(booking.id)
                    return (
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
                                            <h5 className='medium-14'>{t("booking:card.guests")}:</h5>
                                            <p>{booking.guests}</p>
                                        </div>
                                        <div className='flex items-center gap-x-2'>
                                            <h5 className='medium-14'>{t("booking:card.total")}:</h5>
                                            <p className='text-gray-400 text-sm'>
                                                {formatCurrency(booking.totalPrice, currency)}
                                            </p>
                                        </div>
                                    </div>
                                    <div className='flex items-center gap-1'>
                                        <img src={assets.pin} alt="pinIcon" width={13} />
                                        <p className='text-sm'>{booking.property?.address}</p>
                                    </div>
                                </div>
                            </div>
                            {/* BOOKING SUMMARY */}
                            <div className='flex flex-col lg:flex-row justify-between items-start lg:items-center gap-3 border-t border-gray-300 pt-3'>
                                <div className='flex gap-2 gap-x-4 flex-wrap'>
                                    <div className='flex items-center gap-x-2'>
                                        <h5 className='medium-14'>{t("booking:card.bookingId")}:</h5>
                                        <p className='text-gray-400 text-sm break-all'>{booking.id}</p>
                                    </div>
                                    <div className='flex items-center gap-x-2'>
                                        <h5 className='medium-14'>{t("booking:card.checkIn")}:</h5>
                                        <p className='text-gray-400 text-sm break-all'>
                                            {formatDate(booking.checkInDate)}
                                        </p>
                                    </div>
                                    <div className='flex items-center gap-x-2'>
                                        <h5 className='medium-14'>{t("booking:card.checkOut")}:</h5>
                                        <p className='text-gray-400 text-sm break-all'>
                                            {formatDate(booking.checkOutDate)}
                                        </p>
                                    </div>
                                </div>
                                <div className='flex gap-2 items-center flex-wrap'>
                                    {/* Payment */}
                                    <div className='flex items-center gap-x-2'>
                                        <h5 className='medium-14'>{t("booking:card.payment")}:</h5>
                                        <div className='flex items-center gap-1'>
                                            <span className={`min-w-2.5 h-2.5 rounded-full ${booking.isPaid ? "bg-green-500" : "bg-yellow-500"
                                                }`} />
                                            <p>{booking.isPaid ? t("booking:card.paid") : t("booking:card.unpaid")}</p>
                                        </div>
                                    </div>
                                    {!booking.isPaid && (
                                        <button
                                            onClick={() => handlePayment(booking.id)}
                                            className='btn-secondary py-1! text-xs! rounded-sm'
                                        >
                                            {t("booking:card.payNow")}
                                        </button>
                                    )}
                                    {/* Contract */}
                                    {booking.status === "CONFIRMED" && (
                                        <>
                                            {!contract ? (
                                                <button
                                                    onClick={() => handleGenerateContract(booking.id)}
                                                    disabled={creatingContract === booking.id}
                                                    className='flex items-center gap-1 btn-outline py-1! text-xs! rounded-sm disabled:opacity-50'
                                                >
                                                    <img src={assets.signature} alt="" width={14} />
                                                    {creatingContract === booking.id
                                                        ? t("booking:card.generating")
                                                        : t("booking:card.generate")}
                                                </button>
                                            ) : (
                                                <button
                                                    onClick={() => handleViewContract(contract.id)}
                                                    className='flex items-center gap-1 btn-outline py-1! text-xs! rounded-sm'
                                                >
                                                    <img src={assets.signature} alt="" width={14} />
                                                    {t("booking:card.view")}
                                                    <span className={`ml-1 w-2 h-2 rounded-full ${getStatusLabel(contract.status).color
                                                        }`} />
                                                </button>
                                            )}
                                        </>
                                    )}
                                </div>
                            </div>
                        </div>
                    )
                })}
            </div>
        </div>
    )
}

export default MyBookings