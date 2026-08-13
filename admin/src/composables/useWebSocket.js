import { ref, onMounted, onUnmounted } from 'vue'
import { useAuth } from '@clerk/vue'
import { Client } from '@stomp/stompjs'

export function useWebSocket() {
    const { getToken, isOnline } = useAuth()

    const connected = ref(false)
    const notifications = ref([])
    const unreadCount = ref(0)

    let client = null
    let active = false

    onMounted(() => {
        active = true

        const backendUrl = (import.meta.env.VITE_BACKEND_URL || 'http://127.0.0.1:4000').replace(/\/$/, '')
        const wsBaseUrl = backendUrl.replace(/^http/i, 'ws') + '/ws'

        const connect = async () => {
            if (!active) return

            let token
            try {
                token = await getToken.value()
            } catch (e) {
                // ClerkOfflineError: user is offline or auth not available
                if (!isOnline) {
                    connected.value = false
                } else {
                    throw e
                }
                return
            }

            if (!token || !active) {
                connected.value = false
                return
            }

            // Send bearer token via HTTP handshake query parameter expected by
            // WebSocketAuthInterceptor, instead of STOMP connectHeaders.
            const wsUrl = `${wsBaseUrl}?token=${token}`

            client = new Client({
                brokerURL: wsUrl,
                reconnectDelay: 5000,
                // connectHeaders removed: token sent through HTTP handshake above
                onConnect: () => {
                    if (!active) return
                    connected.value = true
                },
                onDisconnect: () => {
                    connected.value = false
                    // Re-attempt connection with fresh token on reconnect
                    // so that an expired token is refreshed automatically.
                    if (active) {
                        void connect()
                    }
                },
                onStompError: (frame) => {
                    console.error('STOMP error:', frame.headers['message'], frame.body)
                },
                onWebSocketError: (event) => {
                    console.warn('WebSocket transport error', event)
                },
            })

            client.activate()
        }

        void connect()
    })

    onUnmounted(() => {
        active = false
        if (client) {
            client.deactivate()
            client = null
        }
    })

    return { connected, notifications, unreadCount }
}
