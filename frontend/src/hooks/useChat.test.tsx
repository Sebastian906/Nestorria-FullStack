import { renderHook, act, waitFor } from '@testing-library/react'
import { useChat } from './useChat'
import { vi, describe, it, expect, beforeEach } from 'vitest'

// Mock Clerk
vi.mock('@clerk/react', () => ({
    useAuth: () => ({
        getToken: vi.fn().mockResolvedValue('test-token'),
    }),
}))

// Mock the streamChat service
const mockStream = vi.fn()
vi.mock('../../services/chatService', () => ({
    streamChat: (...args: unknown[]) => mockStream(...args),
}))

describe('useChat', () => {
    beforeEach(() => {
        sessionStorage.clear()
        vi.clearAllMocks()
    })

    it('starts with empty state', () => {
        const { result } = renderHook(() => useChat())
        expect(result.current.messages).toEqual([])
        expect(result.current.isStreaming).toBe(false)
        expect(result.current.error).toBeNull()
        expect(result.current.remainingMessages).toBe(20)
    })

    it('adds user message and streams assistant response', async () => {
        async function* mockGenerator() {
            yield { type: 'start', content: null, conversationId: 'conv-1', sources: null }
            yield { type: 'token', content: 'Hello', conversationId: null, sources: null }
            yield { type: 'token', content: ' world', conversationId: null, sources: null }
            yield { type: 'end', content: null, conversationId: 'conv-1', sources: ['FAQ'] }
        }
        mockStream.mockReturnValue(mockGenerator())

        const { result } = renderHook(() => useChat())

        await act(async () => {
            result.current.sendMessage('Hi')
        })

        await waitFor(() => {
            expect(result.current.isStreaming).toBe(false)
        })

        expect(result.current.messages).toHaveLength(2)
        expect(result.current.messages[0].role).toBe('user')
        expect(result.current.messages[0].content).toBe('Hi')
        expect(result.current.messages[1].role).toBe('assistant')
        expect(result.current.messages[1].content).toBe('Hello world')
        expect(result.current.messages[1].sources).toEqual(['FAQ'])
        expect(result.current.conversationId).toBe('conv-1')
    })

    it('handles error events', async () => {
        async function* mockGenerator() {
            yield { type: 'start', content: null, conversationId: 'conv-1', sources: null }
            yield { type: 'error', content: 'Service unavailable', conversationId: null, sources: null }
        }
        mockStream.mockReturnValue(mockGenerator())

        const { result } = renderHook(() => useChat())

        await act(async () => {
            result.current.sendMessage('Hi')
        })

        await waitFor(() => {
            expect(result.current.isStreaming).toBe(false)
        })

        expect(result.current.error).toBe('Service unavailable')
        // Empty assistant message should be removed
        expect(result.current.messages).toHaveLength(1)
        expect(result.current.messages[0].role).toBe('user')
    })

    it('persists conversation to sessionStorage', async () => {
        async function* mockGenerator() {
            yield { type: 'start', content: null, conversationId: 'conv-1', sources: null }
            yield { type: 'token', content: 'Hi', conversationId: null, sources: null }
            yield { type: 'end', content: null, conversationId: 'conv-1', sources: null }
        }
        mockStream.mockReturnValue(mockGenerator())

        const { result } = renderHook(() => useChat())

        await act(async () => {
            result.current.sendMessage('Hello')
        })

        await waitFor(() => {
            expect(result.current.isStreaming).toBe(false)
        })

        const stored = JSON.parse(sessionStorage.getItem('nestorria_chat')!)
        expect(stored.messages).toHaveLength(2)
        expect(stored.conversationId).toBe('conv-1')
    })

    it('clearMessages resets state and storage', async () => {
        // Pre-populate storage
        sessionStorage.setItem('nestorria_chat', JSON.stringify({
            messages: [{ id: '1', role: 'user', content: 'Hi', timestamp: new Date().toISOString() }],
            conversationId: 'conv-1',
        }))

        const { result } = renderHook(() => useChat())

        // Should load from storage
        expect(result.current.messages).toHaveLength(1)

        act(() => {
            result.current.clearMessages()
        })

        expect(result.current.messages).toEqual([])
        expect(result.current.conversationId).toBeNull()
        expect(sessionStorage.getItem('nestorria_chat')).toBeNull()
    })

    it('tracks rate limit locally', async () => {
        async function* mockGenerator() {
            yield { type: 'start', content: null, conversationId: 'c', sources: null }
            yield { type: 'end', content: null, conversationId: 'c', sources: null }
        }
        mockStream.mockReturnValue(mockGenerator())

        const { result } = renderHook(() => useChat())
        expect(result.current.remainingMessages).toBe(20)

        await act(async () => {
            result.current.sendMessage('msg1')
        })

        await waitFor(() => {
            expect(result.current.isStreaming).toBe(false)
        })

        expect(result.current.remainingMessages).toBe(19)
    })
})