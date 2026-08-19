import { ref, onMounted, onUnmounted } from 'vue'
import { useAuth } from '@clerk/vue'
import { Client } from '@stomp/stompjs'

// Cliente STOMP ÚNICO a nivel de módulo: aunque NotificationBell se monta dos
// veces (Sidebar mobile + desktop), hay una sola conexión y los mensajes se
// distribuyen a todas las instancias suscritas.
let sharedClient = null
let subscriberCount = 0
const listeners = new Set()

export function useWebSocket() {
    const { getToken } = useAuth()

    const backendUrl = (import.meta.env.VITE_BACKEND_URL || 'http://127.0.0.1:4000').replace(/\/$/, '')
    const wsBaseUrl = backendUrl.replace(/^http/i, 'ws') + '/ws'

    const connected = ref(false)
    const notifications = ref([])
    const unreadCount = ref(0)

    let active = false

    const listener = { connected, notifications, unreadCount }

    onMounted(() => {
        active = true
        subscriberCount++
        listeners.add(listener)

        if (!sharedClient) {
            sharedClient = new Client({
                brokerURL: wsBaseUrl,
                reconnectDelay: 5000,
                // CLAVE (@stomp/stompjs v7): el retorno de beforeConnect se IGNORA.
                // Para mandar headers dinámicos hay que MUTAR client.connectHeaders.
                // Clerk renueva el JWT solo: token fresco en CADA intento.
                beforeConnect: (c) => getToken.value().then((token) => {
                    if (!token) {
                        throw new Error('Token de Clerk no disponible; reintentando...')
                    }
                    c.connectHeaders = { ...c.connectHeaders, Authorization: `Bearer ${token}` }
                }),
                onConnect: () => {
                    listeners.forEach((l) => { l.connected.value = true })

                    sharedClient.subscribe('/user/topic/notifications', (message) => {
                        try {
                            const notification = JSON.parse(message.body)
                            listeners.forEach((l) => {
                                l.notifications.value = [notification, ...l.notifications.value].slice(0, 20)
                                l.unreadCount.value += 1
                            })
                        } catch (e) {
                            console.error('Error parsing WebSocket message', e)
                        }
                    })

                    sharedClient.subscribe('/user/topic/notifications/unread-count', (message) => {
                        try {
                            const { count } = JSON.parse(message.body)
                            listeners.forEach((l) => { l.unreadCount.value = count })
                        } catch (e) {
                            console.error('Error parsing unread count', e)
                        }
                    })
                },
                onDisconnect: () => {
                    listeners.forEach((l) => { l.connected.value = false })
                },
                onStompError: (frame) => {
                    console.error('STOMP error:', frame.headers['message'], frame.body)
                },
                onWebSocketError: (event) => {
                    console.warn('WebSocket transport error', event)
                },
            })
            sharedClient.activate()
        }
    })

    onUnmounted(() => {
        active = false
        listeners.delete(listener)
        subscriberCount--
        if (subscriberCount <= 0 && sharedClient) {
            sharedClient.deactivate()
            sharedClient = null
        }
    })

    return { connected, notifications, unreadCount }
}
