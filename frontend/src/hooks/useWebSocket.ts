import { useEffect, useRef, useState } from 'react';
import { useAuth } from '@clerk/react';
import { Client } from '@stomp/stompjs';

export function useWebSocket() {
    const { getToken } = useAuth();
    const clientRef = useRef<Client | null>(null);
    const [connected, setConnected] = useState(false);
    const [notifications, setNotifications] = useState<any[]>([]);
    const [unreadCount, setUnreadCount] = useState(0);

    useEffect(() => {
        const backendUrl = (import.meta.env.VITE_BACKEND_URL || 'http://127.0.0.1:4000').replace(/\/$/, '');
        const wsUrl = backendUrl.replace(/^http/i, 'ws') + '/ws';
        let active = true;

        const connect = async () => {
            const token = await getToken();
            if (!token || !active) {
                setConnected(false);
                return;
            }

            const client = new Client({
                brokerURL: `${wsUrl}?token=${token}`,
                reconnectDelay: 5000,
                onConnect: () => {
                    if (!active) {
                        return;
                    }
                    setConnected(true);

                    client.subscribe('/user/topic/notifications', (message: any) => {
                        try {
                            const notification = JSON.parse(message.body);
                            setNotifications(prev => [notification, ...prev]);
                            setUnreadCount(prev => prev + 1);
                        } catch (e) {
                            console.error('Error parsing WebSocket message', e);
                        }
                    });

                    client.subscribe('/user/topic/notifications/unread-count', (message: any) => {
                        try {
                            const { count } = JSON.parse(message.body);
                            setUnreadCount(count);
                        } catch (e) {
                            console.error('Error parsing unread count', e);
                        }
                    });
                },
                onDisconnect: () => {
                    setConnected(false);
                },
                onStompError: (frame: any) => {
                    console.error('STOMP error:', frame.headers['message'], frame.body);
                },
                onWebSocketError: (event: any) => {
                    console.warn('WebSocket transport error', event);
                },
            });

            client.activate();
            clientRef.current = client;
        };

        void connect();

        return () => {
            active = false;
            if (clientRef.current) {
                clientRef.current.deactivate();
                clientRef.current = null;
            }
        };
    }, [getToken]);

    return { connected, notifications, unreadCount };
}