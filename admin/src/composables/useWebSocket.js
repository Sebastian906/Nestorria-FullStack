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

            // El JWT va en el frame STOMP CONNECT (connectHeaders), nunca en el
            // query string del handshake — lo exige WebSocketAuthInterceptor
            // (server/.../common/websocket/WebSocketAuthInterceptor.java).
            client = new Client({
                brokerURL: wsBaseUrl,
                reconnectDelay: 5000,
                // Token fresco en CADA conexión/reconexión: beforeConnect corre
                // antes de cada intento (incluidos los automáticos de
                // reconnectDelay) y evita reenviar un JWT expirado capturado
                // al construir el Client.
                beforeConnect: async () => {
                    const token = await getToken.value()
                    return token ? { Authorization: `Bearer ${token}` } : {}
                },
                onConnect: () => {
                    if (!active) return
                    connected.value = true

                    // Mismo contrato que frontend/src/hooks/useWebSocket.ts
                    client.subscribe('/user/topic/notifications', (message) => {
                        try {
                            const notification = JSON.parse(message.body)
                            notifications.value = [notification, ...notifications.value].slice(0, 20)
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
