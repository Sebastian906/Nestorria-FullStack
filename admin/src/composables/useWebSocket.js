import { ref, onMounted, onUnmounted } from 'vue'
import { useAuth } from '@clerk/vue'
import { Client } from '@stomp/stompjs'

export function useWebSocket() {
    const { getToken } = useAuth()

    const connected = ref(false)
    const notifications = ref([])
    const unreadCount = ref(0)

    let client = null
    let active = false

    onMounted(() => {
        active = true

        const backendUrl = (import.meta.env.VITE_BACKEND_URL || 'http://127.0.0.1:4000').replace(/\/$/, '')
        const wsUrl = backendUrl.replace(/^http/i, 'ws') + '/ws'

        const connect = async () => {
            if (!active) return

            const token = await getToken.value()
            if (!token || !active) {
                connected.value = false
                return
            }

            client = new Client({
                brokerURL: wsUrl,
                reconnectDelay: 5000,
                connectHeaders: { Authorization: `Bearer ${token}` },
                onConnect: () => {
                    if (!active) return
                    connected.value = true

                    client.subscribe('/user/topic/notifications', (message) => {
                        try {
                            const notification = JSON.parse(message.body)
                            notifications.value = [notification, ...notifications.value]
                            unreadCount.value += 1
                        } catch (e) {
                            console.error('Error parsing WebSocket message', e)
                        }
                    })

                    client.subscribe('/user/topic/notifications/unread-count', (message) => {
                        try {
                            const { count } = JSON.parse(message.body)
                            unreadCount.value = count
                        } catch (e) {
                            console.error('Error parsing unread count', e)
                        }
                    })
                },
                onDisconnect: () => {
                    connected.value = false
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
