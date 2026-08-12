import { ref } from 'vue'

const connected = ref(false)
const notifications = ref([])
const unreadCount = ref(0)

export function useWebSocket() {
    // Fallback composable: keeps NotificationBell reactive even if
    // real-time STOMP wiring is not configured yet in admin.
    return {
        connected,
        notifications,
        unreadCount
    }
}
