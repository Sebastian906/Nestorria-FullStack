import { useState, useEffect, useRef, useCallback } from "react"
import { useAuth } from "@clerk/react"
import axios from "axios"
import toast from "react-hot-toast"
import { useWebSocket } from "../hooks/useWebSocket"

export interface Notification {
    id: string
    type: NotificationType
    title: string
    message: string
    referenceType: string | null
    referenceId: string | null
    isRead: boolean
    createdAt: string
}

export type NotificationType =
    | "BOOKING_CONFIRMED"
    | "BOOKING_CANCELLED"
    | "PAYMENT_RECEIVED"
    | "CONTRACT_SIGNED"
    | "CONTRACT_EXPIRED"
    | "REVIEW_RECEIVED"
    | "PROPERTY_INQUIRY"

interface NotificationResponse {
    content: Notification[]
    totalElements: number
    totalPages: number
    number: number
    size: number
}

const NotificationBell = () => {
    const { getToken, isLoaded } = useAuth()
    const { connected, notifications: wsNotifications, unreadCount: wsUnreadCount } = useWebSocket()
    const [notifications, setNotifications] = useState<Notification[]>([])
    const [unreadCount, setUnreadCount] = useState(0)
    const [isOpen, setIsOpen] = useState(false)
    const [loading, setLoading] = useState(false)
    const [page, setPage] = useState(0)
    const [hasMore, setHasMore] = useState(true)
    const dropdownRef = useRef<HTMLDivElement>(null)
    const hasLoggedNetworkIssueRef = useRef(false)
    const seenIdsRef = useRef(new Set<string>())

    const isNetworkError = (error: unknown) =>
        axios.isAxiosError(error) && error.code === 'ERR_NETWORK'

    const fetchUnreadCount = useCallback(async () => {
        try {
            const token = await getToken()
            if (!token) return

            const { data } = await axios.get("/api/notifications/me/unread-count", {
                headers: { Authorization: `Bearer ${token}` }
            })
            setUnreadCount(data.count)
            hasLoggedNetworkIssueRef.current = false
        } catch (error) {
            if (isNetworkError(error)) {
                if (!hasLoggedNetworkIssueRef.current) {
                    hasLoggedNetworkIssueRef.current = true
                    console.warn("Backend not reachable for unread-count endpoint")
                }
                return
            }
            console.error("Error fetching unread count", error)
        }
    }, [getToken])

    const requestIdRef = useRef(0)

    const fetchNotifications = useCallback(async (pageNum: number = 0, append: boolean = false) => {
        const requestId = ++requestIdRef.current
        try {
            const token = await getToken()
            if (!token) return

            setLoading(true)
            const { data } = await axios.get<NotificationResponse>("/api/notifications/me", {
                headers: { Authorization: `Bearer ${token}` },
                params: { page: pageNum, size: 10 }
            })

            if (requestId !== requestIdRef.current) return

            if (append) {
                setNotifications(prev => {
                    const existingIds = new Set(prev.map(n => n.id))
                    return [...prev, ...data.content.filter(n => !existingIds.has(n.id))]
                })
            } else {
                setNotifications(data.content)
            }
            setHasMore(pageNum < data.totalPages - 1)
        } catch (error) {
            if (isNetworkError(error)) {
                return
            }
            console.error("Error fetching notifications", error)
            toast.error("Error fetching notifications")
        } finally {
            setLoading(false)
        }
    }, [getToken])

    const markAsRead = async (notificationId: string) => {
        try {
            const token = await getToken()
            if (!token) return

            await axios.patch(`/api/notifications/${notificationId}/read`, {}, {
                headers: { Authorization: `Bearer ${token}` }
            })

            setNotifications(prev =>
                prev.map(n => n.id === notificationId ? { ...n, isRead: true } : n)
            )
            setUnreadCount(prev => Math.max(0, prev - 1))
        } catch (error) {
            if (isNetworkError(error)) {
                return
            }
            console.error("Error marking as read", error)
            toast.error("Error at marking as read")
        }
    }

    const markAllAsRead = async () => {
        try {
            const token = await getToken()
            if (!token) return

            await axios.post("/api/notifications/read-all", {}, {
                headers: { Authorization: `Bearer ${token}` }
            })

            setNotifications(prev => prev.map(n => ({ ...n, isRead: true })))
            setUnreadCount(0)
            toast.success("All notifications marked as read")
        } catch (error) {
            if (isNetworkError(error)) {
                return
            }
            toast.error("Error at marking notifications as read")
        }
    }

    const loadMore = () => {
        if (!loading && hasMore) {
            const nextPage = page + 1
            setPage(nextPage)
            fetchNotifications(nextPage, true)
        }
    }

    const toggleDropdown = () => {
        if (!isOpen) {
            setPage(0)
            fetchNotifications(0, false)
        }
        setIsOpen(!isOpen)
    }

    const getNotificationIcon = (type: NotificationType) => {
        switch (type) {
            case "BOOKING_CONFIRMED":
                return "Booking Confirmed"
            case "BOOKING_CANCELLED":
                return "Booking Cancelled"
            case "PAYMENT_RECEIVED":
                return "Payment Received"
            case "CONTRACT_SIGNED":
                return "Contract Signed"
            case "CONTRACT_EXPIRED":
                return "Contract Expired"
            case "REVIEW_RECEIVED":
                return "Review Received"
            case "PROPERTY_INQUIRY":
                return "Property Inquiry"
            default:
                return "Notification Alert"
        }
    }

    const formatDate = (dateString: string) => {
        const date = new Date(dateString)
        const now = new Date()
        const diffMs = now.getTime() - date.getTime()
        const diffMins = Math.floor(diffMs / 60000)
        const diffHours = Math.floor(diffMs / 3600000)
        const diffDays = Math.floor(diffMs / 86400000)

        if (diffMins < 1) return "Now"
        if (diffMins < 60) return `${diffMins}m ago`
        if (diffHours < 24) return `${diffHours}h ago`
        if (diffDays < 7) return `${diffDays}d ago`
        return date.toLocaleDateString(undefined, { day: "numeric", month: "short" })
    }

    useEffect(() => {
        // Seed the badge from the authoritative HTTP count.
        // While the socket is connected, real-time refreshes arrive via pushed
        // WebSocket events (see the effect below), so this 30-second HTTP interval
        // stays dormant. When the socket is disconnected, polling refreshes the
        // unread count every 30 seconds.
        fetchUnreadCount()

        if (connected) {
            // Socket connected: WebSocket events handle real-time notification
            // updates and unread count tracking. No HTTP polling needed.
            return
        }

        // Socket disconnected (or not yet connected): fall back to polling.
        const interval = setInterval(fetchUnreadCount, 30000)
        return () => clearInterval(interval)
    }, [connected, fetchUnreadCount])

    // Synchronization effect: use the hook's unreadCount value for the badge
    // whenever the socket is connected. When disconnected, the first useEffect's
    // polling (fetchUnreadCount) refreshes the count from the HTTP endpoint.
    useEffect(() => {
        if (connected && wsUnreadCount !== undefined) {
            setUnreadCount(wsUnreadCount)
        }
    }, [connected, wsUnreadCount])

    useEffect(() => {
        // Real-time refresh from pushed notification events: prepend anything the
        // socket has newly delivered, deduplicating by id against both previously
        // surfaced pushes and the notifications already loaded from the paged API.
        const existingIds = new Set(notifications.map(n => n.id))
        const incoming: Notification[] = []
        for (const n of wsNotifications) {
            if (!n?.id || seenIdsRef.current.has(n.id)) continue
            seenIdsRef.current.add(n.id)
            if (!existingIds.has(n.id)) incoming.push(n as Notification)
        }
        if (incoming.length === 0) return

        setNotifications(prev => [...incoming, ...prev])
    }, [wsNotifications, notifications])

    useEffect(() => {
        const handleClickOutside = (event: MouseEvent) => {
            if (dropdownRef.current && !dropdownRef.current.contains(event.target as Node)) {
                setIsOpen(false)
            }
        }
        document.addEventListener("mousedown", handleClickOutside)
        return () => document.removeEventListener("mousedown", handleClickOutside)
    }, [])

    return (
        <div className="relative" ref={dropdownRef}>
            {/* Bell Button */}
            <button
                onClick={toggleDropdown}
                className={`relative p-2 rounded-full transition-all duration-200 ${
                    isOpen ? 'bg-secondary/20' : 'hover:bg-secondary/10'
                }`}
                aria-label="Notifications"
            >
                <svg
                    xmlns="http://www.w3.org/2000/svg"
                    width="22"
                    height="22"
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth="2"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                >
                    <path d="M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9" />
                    <path d="M10.3 21a1.94 1.94 0 0 0 3.4 0" />
                </svg>
                {unreadCount > 0 && (
                    <span className="absolute -top-1 -right-1 bg-red-500 text-white text-xs font-bold rounded-full min-w-[20px] h-5 flex items-center justify-center px-1">
                        {unreadCount > 99 ? "99+" : unreadCount}
                    </span>
                )}
            </button>

            {/* Dropdown */}
            {isOpen && (
                <div className="absolute right-0 top-full mt-2 w-80 sm:w-96 bg-white rounded-xl shadow-lg ring-1 ring-slate-900/10 z-50 overflow-hidden">
                    {/* Header */}
                    <div className="flex items-center justify-between px-4 py-3 border-b border-slate-100">
                        <h3 className="font-semibold text-gray-800">Notifications</h3>
                        {unreadCount > 0 && (
                            <button
                                onClick={markAllAsRead}
                                className="text-xs text-secondary hover:text-secondary/80 font-medium"
                            >
                                Mark all as read
                            </button>
                        )}
                    </div>

                    {/* Notifications List */}
                    <div className="max-h-96 overflow-y-auto">
                        {notifications.length === 0 ? (
                            <div className="py-8 text-center text-gray-400">
                                <p className="text-sm">You don't have any notifications</p>
                            </div>
                        ) : (
                            notifications.map(notification => (
                                <div
                                    key={notification.id}
                                    onClick={() => !notification.isRead && markAsRead(notification.id)}
                                    className={`flex items-start gap-3 px-4 py-3 cursor-pointer transition-colors ${
                                        notification.isRead
                                            ? 'bg-white hover:bg-slate-50'
                                            : 'bg-secondary/5 hover:bg-secondary/10'
                                    }`}
                                >
                                    <span className="text-xl mt-0.5">
                                        {getNotificationIcon(notification.type)}
                                    </span>
                                    <div className="flex-1 min-w-0">
                                        <div className="flex items-start justify-between gap-2">
                                            <p className={`text-sm font-medium ${
                                                notification.isRead ? 'text-gray-600' : 'text-gray-800'
                                            }`}>
                                                {notification.title}
                                            </p>
                                            {!notification.isRead && (
                                                <span className="w-2 h-2 bg-secondary rounded-full mt-1.5 flex-shrink-0" />
                                            )}
                                        </div>
                                        <p className="text-xs text-gray-500 mt-0.5 line-clamp-2">
                                            {notification.message}
                                        </p>
                                        <p className="text-xs text-gray-400 mt-1">
                                            {formatDate(notification.createdAt)}
                                        </p>
                                    </div>
                                </div>
                            ))
                        )}
                    </div>

                    {/* Load More */}
                    {hasMore && notifications.length > 0 && (
                        <div className="border-t border-slate-100">
                            <button
                                onClick={loadMore}
                                disabled={loading}
                                className="w-full py-2 text-sm text-secondary hover:text-secondary/80 font-medium disabled:opacity-50"
                            >
                                {loading ? "Loading..." : "Load more"}
                            </button>
                        </div>
                    )}
                </div>
            )}
        </div>
    )
}

export default NotificationBell