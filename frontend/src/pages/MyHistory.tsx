import { useEffect, useState, useCallback } from "react"
import { useAppContext } from "../context/AppContext"
import { useAuth } from "@clerk/react"
import axios from "axios"
import toast from "react-hot-toast"
import { Link } from "react-router-dom"

interface HistoryEvent {
    id: string
    type: "search" | "booking" | "invoice"
    title: string
    detail: string
    date: string
    link?: string
}

const MyHistory = () => {
    const { user, searchedCities, currency } = useAppContext()
    const { getToken } = useAuth()
    const [events, setEvents] = useState<HistoryEvent[]>([])
    const [loading, setLoading] = useState(true)
    const [failedSources, setFailedSources] = useState<string[]>([])

    const loadHistory = useCallback(async () => {
        if (!user) return
        setLoading(true)
        setFailedSources([])
        try {
            const token = await getToken()
            const timeline: HistoryEvent[] = []
            const failures: string[] = []

            // Recent searches from context (with persisted timestamps)
            searchedCities.forEach((item) => {
                timeline.push({
                    id: `search-${item.city}`,
                    type: "search",
                    title: `Searched: ${item.city}`,
                    detail: `You searched for properties in ${item.city}`,
                    date: item.searchedAt,
                    link: `/listing?destination=${encodeURIComponent(item.city)}`
                })
            })

            // Bookings
            try {
                const { data: bookings } = await axios.get("/api/bookings/me", {
                    headers: { Authorization: `Bearer ${token}` }
                })
                bookings.forEach((b: any) => {
                    timeline.push({
                        id: `booking-${b.id}`,
                        type: "booking",
                        title: `Booking: ${b.property?.title || "Property"}`,
                        detail: `${b.checkInDate} → ${b.checkOutDate} | ${currency}${b.totalPrice} | ${b.status}`,
                        date: b.checkInDate,
                        link: `/my-bookings`
                    })
                })
            } catch {
                failures.push("bookings")
            }

            // Invoices
            try {
                const { data: invoices } = await axios.get("/api/invoices/me", {
                    headers: { Authorization: `Bearer ${token}` }
                })
                invoices.forEach((inv: any) => {
                    timeline.push({
                        id: `invoice-${inv.id}`,
                        type: "invoice",
                        title: `Invoice: ${inv.invoiceNumber}`,
                        detail: `${currency}${inv.total} | ${inv.status}`,
                        date: inv.issueDate
                    })
                })
            } catch {
                failures.push("invoices")
            }

            setFailedSources(failures)
            timeline.sort((a, b) => new Date(b.date).getTime() - new Date(a.date).getTime())
            setEvents(timeline)
        } catch (error: any) {
            toast.error("Error loading history")
        } finally {
            setLoading(false)
        }
    }, [user, getToken, searchedCities, currency])

    useEffect(() => { loadHistory() }, [loadHistory])

    const SearchIcon = () => (
        <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <circle cx="11" cy="11" r="8" />
            <path d="m21 21-4.3-4.3" />
        </svg>
    )

    const HouseIcon = () => (
        <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <path d="m3 9 9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z" />
            <polyline points="9 22 9 12 15 12 15 22" />
        </svg>
    )

    const DocumentIcon = () => (
        <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <path d="M15 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7Z" />
            <path d="M14 2v4a2 2 0 0 0 2 2h4" />
            <path d="M10 9H8" />
            <path d="M16 13H8" />
            <path d="M16 17H8" />
        </svg>
    )

    const PinIcon = () => (
        <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <path d="M20 10c0 6-8 12-8 12s-8-6-8-12a8 8 0 0 1 16 0Z" />
            <circle cx="12" cy="10" r="3" />
        </svg>
    )

    const getEventIcon = (type: string) => {
        switch (type) {
            case "search": return <SearchIcon />
            case "booking": return <HouseIcon />
            case "invoice": return <DocumentIcon />
            default: return <PinIcon />
        }
    }

    if (!user) {
        return (
            <div className='bg-linear-to-r from-[#F0FDF4] to-white py-16 pt-28'>
                <div className='max-padd-container text-center py-10'>
                    <p className="text-gray-500">Please log in to see your history.</p>
                </div>
            </div>
        )
    }

    return (
        <div className='bg-linear-to-r from-[#F0FDF4] to-white py-16 pt-28'>
            <div className='max-padd-container'>
                <h2 className='h2 mb-6'>My History</h2>
                {loading && <p className="text-gray-500 text-center py-10">Loading history...</p>}
                {!loading && failedSources.length > 0 && (
                    <p className="text-amber-600 bg-amber-50 text-sm p-3 rounded-lg mb-4 text-center">
                        Some data couldn't be loaded: {failedSources.join(", ")}. Showing partial results.
                    </p>
                )}
                {!loading && events.length === 0 && (
                    <p className="text-gray-500 text-center py-10">No activity yet.</p>
                )}
                <div className="space-y-3">
                    {events.map((event) => (
                        <div key={event.id} className='bg-white ring-1 ring-slate-900/5 p-4 rounded-lg flex items-start gap-3'>
                            <span className="text-secondary">{getEventIcon(event.type)}</span>
                            <div className="flex-1">
                                <div className="flex items-center justify-between">
                                    {event.link ? (
                                        <Link to={event.link} className="h5 hover:underline">{event.title}</Link>
                                    ) : (
                                        <h5 className="h5">{event.title}</h5>
                                    )}
                                    <span className="text-xs text-gray-400">
                                        {new Date(event.date).toLocaleDateString()}
                                    </span>
                                </div>
                                <p className="text-sm text-gray-500 mt-1">{event.detail}</p>
                            </div>
                        </div>
                    ))}
                </div>
            </div>
        </div>
    )
}

export default MyHistory