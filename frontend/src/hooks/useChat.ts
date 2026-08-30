import { useState, useRef, useCallback, useEffect } from 'react'
import { useAuth } from '@clerk/react'
import { streamChat } from '../services/chatService'

export interface Message {
    id: string
    role: 'user' | 'assistant'
    content: string
    sources?: string[]
    timestamp: Date
}

interface ChatState {
    messages: Message[]
    isStreaming: boolean
    error: string | null
    conversationId: string | null
}

const STORAGE_KEY = 'nestorria_chat'
const RATE_LIMIT_KEY = 'nestorria_chat_rate'
const RATE_LIMIT_MAX = 20
const RATE_LIMIT_WINDOW_MS = 60 * 60 * 1000 // 1 hour

function loadState(): ChatState {
    try {
        const raw = sessionStorage.getItem(STORAGE_KEY)
        if (!raw) return { messages: [], isStreaming: false, error: null, conversationId: null }
        const parsed = JSON.parse(raw)
        return {
            ...parsed,
            messages: (parsed.messages ?? []).map((m: Record<string, unknown>) => ({
                ...m,
                timestamp: new Date(m.timestamp as string),
            })),
            isStreaming: false, // Never restore streaming state
        }
    } catch {
        return { messages: [], isStreaming: false, error: null, conversationId: null }
    }
}

function loadRateLimit(): { count: number; windowStart: number } {
    try {
        const raw = sessionStorage.getItem(RATE_LIMIT_KEY)
        if (!raw) return { count: 0, windowStart: Date.now() }
        const parsed = JSON.parse(raw)
        // Reset if window expired
        if (Date.now() - parsed.windowStart > RATE_LIMIT_WINDOW_MS) {
            return { count: 0, windowStart: Date.now() }
        }
        return parsed
    } catch {
        return { count: 0, windowStart: Date.now() }
    }
}

function saveRateLimit(count: number, windowStart: number) {
    try {
        sessionStorage.setItem(RATE_LIMIT_KEY, JSON.stringify({ count, windowStart }))
    } catch { /* ignore */ }
}

export function useChat() {
    const { getToken } = useAuth()
    const [state, setState] = useState<ChatState>(loadState)
    const [rateLimit, setRateLimit] = useState(loadRateLimit)
    const abortRef = useRef<AbortController | null>(null)

    // Persist messages on change
    useEffect(() => {
        try {
            sessionStorage.setItem(STORAGE_KEY, JSON.stringify({
                messages: state.messages,
                conversationId: state.conversationId,
            }))
        } catch { /* storage full or unavailable */ }
    }, [state.messages, state.conversationId])

    const remainingMessages = Math.max(0, RATE_LIMIT_MAX - rateLimit.count)

    const sendMessage = useCallback(async (content: string) => {
        const token = await getToken()
        if (!token) return

        // Client-side rate limit check
        if (rateLimit.count >= RATE_LIMIT_MAX) {
            setState(prev => ({ ...prev, error: 'Message limit reached. Try again later.' }))
            return
        }

        const userMessage: Message = {
            id: crypto.randomUUID(),
            role: 'user',
            content,
            timestamp: new Date(),
        }

        // Add user message + empty assistant placeholder
        const assistantMessage: Message = {
            id: crypto.randomUUID(),
            role: 'assistant',
            content: '',
            timestamp: new Date(),
        }

        setState(prev => ({
            ...prev,
            messages: [...prev.messages, userMessage, assistantMessage],
            isStreaming: true,
            error: null,
        }))

        const controller = new AbortController()
        abortRef.current = controller

        try {
            const stream = streamChat(
                {
                    message: content,
                    conversationId: state.conversationId ?? undefined,
                },
                token,
                controller.signal
            )

            for await (const event of stream) {
                if (controller.signal.aborted) break

                switch (event.type) {
                    case 'start':
                        setState(prev => ({
                            ...prev,
                            conversationId: event.conversationId,
                        }))
                        break

                    case 'token':
                        setState(prev => {
                            const msgs = [...prev.messages]
                            const last = msgs[msgs.length - 1]
                            if (last && last.role === 'assistant') {
                                msgs[msgs.length - 1] = {
                                    ...last,
                                    content: last.content + (event.content ?? ''),
                                }
                            }
                            return { ...prev, messages: msgs }
                        })
                        break

                    case 'end':
                        setState(prev => {
                            const msgs = [...prev.messages]
                            const last = msgs[msgs.length - 1]
                            if (last && last.role === 'assistant') {
                                msgs[msgs.length - 1] = {
                                    ...last,
                                    sources: event.sources ?? [],
                                }
                            }
                            return {
                                ...prev,
                                messages: msgs,
                                conversationId: event.conversationId ?? prev.conversationId,
                                isStreaming: false,
                            }
                        })
                        // Increment local rate limit
                        setRateLimit(prev => {
                            const newCount = prev.count + 1
                            saveRateLimit(newCount, prev.windowStart)
                            return { ...prev, count: newCount }
                        })
                        break

                    case 'error':
                        setState(prev => ({
                            ...prev,
                            error: event.content ?? 'AI service error',
                            isStreaming: false,
                            // Remove empty assistant message on error
                            messages: prev.messages.filter(
                                (m, i) => !(i === prev.messages.length - 1 && m.role === 'assistant' && m.content === '')
                            ),
                        }))
                        break
                }
            }
        } catch (err: unknown) {
            const error = err instanceof Error ? err : new Error(String(err))
            if (error.name === 'AbortError') return

            let errorMsg = 'Connection error'
            if (error.message.includes('503') || error.message.includes('limit')) {
                errorMsg = 'Message limit reached. Try again later.'
            }

            setState(prev => ({
                ...prev,
                error: errorMsg,
                isStreaming: false,
                messages: prev.messages.filter(
                    (m, i) => !(i === prev.messages.length - 1 && m.role === 'assistant' && m.content === '')
                ),
            }))
        } finally {
            abortRef.current = null
        }
    }, [getToken, state.conversationId, rateLimit])

    const clearMessages = useCallback(() => {
        abortRef.current?.abort()
        setState({ messages: [], isStreaming: false, error: null, conversationId: null })
        sessionStorage.removeItem(STORAGE_KEY)
    }, [])

    const clearError = useCallback(() => {
        setState(prev => ({ ...prev, error: null }))
    }, [])

    // Cleanup on unmount
    useEffect(() => {
        return () => abortRef.current?.abort()
    }, [])

    return {
        messages: state.messages,
        isStreaming: state.isStreaming,
        error: state.error,
        conversationId: state.conversationId,
        remainingMessages,
        sendMessage,
        clearMessages,
        clearError,
    }
}