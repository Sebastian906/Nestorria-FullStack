<template>
    <div class="notification-bell-container relative">
        <!-- Bell Button -->
        <button @click.stop="toggleDropdown" class="relative p-1.5 rounded-full transition-all duration-200"
            :class="isOpen ? 'bg-secondary/20' : 'hover:bg-secondary/10'" aria-label="Notifications">
            <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none"
                stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9" />
                <path d="M10.3 21a1.94 1.94 0 0 0 3.4 0" />
            </svg>
            <span v-if="unreadCount > 0"
                class="absolute -top-1 -right-1 bg-red-500 text-white text-[10px] font-bold rounded-full min-w-[16px] h-4 flex items-center justify-center px-1">
                {{ unreadCount > 99 ? '99+' : unreadCount }}
            </span>
        </button>

        <!-- Dropdown -->
        <div v-if="isOpen" class="absolute right-0 w-80 bg-white rounded-xl shadow-lg ring-1 ring-slate-900/10 z-50 overflow-hidden
                   top-full mt-2
                   md:top-auto md:mt-0 md:bottom-full md:mb-2">
            <!-- Header -->
            <div class="flex items-center justify-between px-4 py-3 border-b border-slate-100">
                <h3 class="font-semibold text-gray-800">Notifications</h3>
                <button v-if="unreadCount > 0" @click="markAllAsRead"
                    class="text-xs text-green-500 hover:text-green-600 font-medium">
                    Mark all as read
                </button>
            </div>

            <!-- Notifications List -->
            <div class="max-h-96 overflow-y-auto">
                <div v-if="notifications.length === 0" class="py-8 text-center text-gray-400">
                    <p class="text-sm">You don't have notifications</p>
                </div>
                <div v-for="notification in notifications" :key="notification.id"
                    @click="!notification.isRead && markAsRead(notification.id)"
                    class="flex items-start gap-3 px-4 py-3 cursor-pointer transition-colors"
                    :class="notification.isRead ? 'bg-white hover:bg-slate-50' : 'bg-green-50 hover:bg-green-100'">
                    <span class="text-xl mt-0.5">{{ getNotificationIcon(notification.type) }}</span>
                    <div class="flex-1 min-w-0">
                        <div class="flex items-start justify-between gap-2">
                            <p class="text-sm font-medium"
                                :class="notification.isRead ? 'text-gray-600' : 'text-gray-800'">
                                {{ notification.title }}
                            </p>
                            <span v-if="!notification.isRead"
                                class="w-2 h-2 bg-green-500 rounded-full mt-1.5 flex-shrink-0" />
                        </div>
                        <p class="text-xs text-gray-500 mt-0.5 line-clamp-2">
                            {{ notification.message }}
                        </p>
                        <p class="text-xs text-gray-400 mt-1">
                            {{ formatDate(notification.createdAt) }}
                        </p>
                    </div>
                </div>
            </div>

            <!-- Load More -->
            <div v-if="hasMore && notifications.length > 0" class="border-t border-slate-100">
                <button @click="loadMore" :disabled="loading"
                    class="w-full py-2 text-sm text-green-500 hover:text-green-600 font-medium disabled:opacity-50">
                    {{ loading ? 'Loading...' : 'Load more' }}
                </button>
            </div>
        </div>
    </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, watch } from 'vue'
import { useWebSocket } from '../composables/useWebSocket'
import { useAppContext } from '../composables/useAppContext'
import axios from 'axios'

const { auth } = useAppContext()

const notifications = ref([])
const unreadCount = ref(0)
const isOpen = ref(false)
const loading = ref(false)
const page = ref(0)
const hasMore = ref(true)

let pollingInterval = null
const hasLoggedNetworkIssue = ref(false)

const handleOutsideClick = (event) => {
    if (!event.target.closest('.notification-bell-container')) {
        isOpen.value = false
    }
}

const isNetworkError = (error) => axios.isAxiosError(error) && error.code === 'ERR_NETWORK'

// WebSocket hook
const { unreadCount: wsUnreadCount, notifications: wsNotifications } = useWebSocket()

// Watchers para actualizar estado desde WebSocket
watch(wsUnreadCount, (newCount) => {
    if (newCount !== undefined) {
        unreadCount.value = newCount
    }
})

watch(wsNotifications, (newNotifications) => {
    if (newNotifications && newNotifications.length > 0) {
        const newNotif = newNotifications[0]
        notifications.value = [newNotif, ...notifications.value].slice(0, 20)
    }
})

const fetchUnreadCount = async () => {
    try {
        if (!auth.isLoaded?.value || !auth.isSignedIn?.value) return
        const token = await auth.getToken.value()
        if (!token) return

        const { data } = await axios.get('/api/notifications/me/unread-count', {
            headers: { Authorization: `Bearer ${token}` }
        })
        unreadCount.value = data.count
    } catch (error) {
        if (isNetworkError(error)) {
            if (!hasLoggedNetworkIssue.value) {
                hasLoggedNetworkIssue.value = true
                console.warn('Backend no disponible temporalmente en /api/notifications/me/unread-count')
            }
            return
        }
        console.error('Error fetching unread count', error)
    }
}

const fetchNotifications = async (pageNum = 0, append = false) => {
    try {
        const token = await auth.getToken.value()
        if (!token) return

        loading.value = true
        const { data } = await axios.get('/api/notifications/me', {
            headers: { Authorization: `Bearer ${token}` },
            params: { page: pageNum, size: 10 }
        })

        if (append) {
            notifications.value = [...notifications.value, ...data.content]
        } else {
            notifications.value = data.content
        }
        hasMore.value = pageNum < data.totalPages - 1
    } catch (error) {
        if (!isNetworkError(error)) {
            console.error('Error loading notifications', error)
        }
    } finally {
        loading.value = false
    }
}

const markAsRead = async (notificationId) => {
    try {
        const token = await auth.getToken.value()
        if (!token) return

        await axios.patch(`/api/notifications/${notificationId}/read`, {}, {
            headers: { Authorization: `Bearer ${token}` }
        })

        const notification = notifications.value.find(n => n.id === notificationId)
        if (notification && !notification.isRead) {
            notification.isRead = true
            unreadCount.value = Math.max(0, unreadCount.value - 1)
        }
    } catch (error) {
        if (!isNetworkError(error)) {
            console.error('Error marking as read', error)
        }
    }
}

const markAllAsRead = async () => {
    try {
        const token = await auth.getToken.value()
        if (!token) return

        await axios.post('/api/notifications/read-all', {}, {
            headers: { Authorization: `Bearer ${token}` }
        })

        notifications.value.forEach(n => { n.isRead = true })
        unreadCount.value = 0
    } catch (error) {
        if (!isNetworkError(error)) {
            console.error('Error marking all as read', error)
        }
    }
}

const loadMore = () => {
    if (!loading.value && hasMore.value) {
        page.value++
        fetchNotifications(page.value, true)
    }
}

const toggleDropdown = () => {
    if (!isOpen.value) {
        page.value = 0
        fetchNotifications(0, false)
    }
    isOpen.value = !isOpen.value
}

const getNotificationIcon = (type) => {
    const icons = {
        BOOKING_CONFIRMED: 'Booking Confirmed',
        BOOKING_CANCELLED: 'Booking Cancelled',
        PAYMENT_RECEIVED: 'Payment Received',
        CONTRACT_SIGNED: 'Contract Signed',
        CONTRACT_EXPIRED: 'Contract Expired',
        REVIEW_RECEIVED: 'Review Received',
        PROPERTY_INQUIRY: 'Property Inquiry'
    }
    return icons[type] || 'Notification Alert'
}

const formatDate = (dateString) => {
    const date = new Date(dateString)
    const now = new Date()
    const diffMs = now.getTime() - date.getTime()
    const diffMins = Math.floor(diffMs / 60000)
    const diffHours = Math.floor(diffMs / 3600000)
    const diffDays = Math.floor(diffMs / 86400000)

    if (diffMins < 1) return 'Now'
    if (diffMins < 60) return `${diffMins}m ago`
    if (diffHours < 24) return `${diffHours}h ago`
    if (diffDays < 7) return `${diffDays}d ago`
    return date.toLocaleDateString(undefined, { day: 'numeric', month: 'short' })
}

onMounted(() => {
    // Espera a que Clerk hidrate la sesión antes del primer request
    if (auth.isLoaded?.value) {
        fetchUnreadCount()
    } else {
        // Clerk aún hidratando: dispara el primer fetch en cuanto la sesión cargue
        const stop = watch(() => auth.isLoaded?.value, (loaded) => {
            if (loaded) {
                stop()
                fetchUnreadCount()
            }
        })
    }
    pollingInterval = setInterval(fetchUnreadCount, 30000) // fallback
    document.addEventListener('click', handleOutsideClick)
})

onUnmounted(() => {
    if (pollingInterval) clearInterval(pollingInterval)
    document.removeEventListener('click', handleOutsideClick)
})
</script>
