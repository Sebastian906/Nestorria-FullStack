const API_BASE = (import.meta.env.VITE_BACKEND_URL || 'http://127.0.0.1:4000').replace(/\/$/, '')

export interface ChatStreamEvent {
    type: 'start' | 'token' | 'content' | 'end' | 'error'
    content: string | null
    conversationId: string | null
    sources: string[] | null
}

export interface ChatRequest {
    message: string
    conversationId?: string
}

export async function* streamChat(
    request: ChatRequest,
    token: string,
    signal?: AbortSignal
): AsyncGenerator<ChatStreamEvent> {
    const response = await fetch(`${API_BASE}/api/ai/chat/stream`, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${token}`,
        },
        body: JSON.stringify(request),
        signal,
    })

    if (!response.ok) {
        // Parse error body (503 rate-limit or other)
        let errorMessage = `Error ${response.status}`
        try {
            const body = await response.json()
            errorMessage = body.message || errorMessage
        } catch { /* ignore parse error */ }
        yield { type: 'error', content: errorMessage, conversationId: null, sources: null }
        return
    }

    const reader = response.body!.getReader()
    const decoder = new TextDecoder()
    let buffer = ''

    try {
        while (true) {
            const { done, value } = await reader.read()
            if (done) break

            buffer += decoder.decode(value, { stream: true })

            // Process complete SSE blocks (separated by blank lines)
            const blocks = buffer.split('\n\n')
            buffer = blocks.pop()! // Keep incomplete block in buffer

            for (const block of blocks) {
                const event = parseSSEBlock(block)
                if (event) yield event
            }
        }

        // Process remaining buffer
        if (buffer.trim()) {
            const event = parseSSEBlock(buffer)
            if (event) yield event
        }
    } finally {
        reader.releaseLock()
    }
}

function parseSSEBlock(block: string): ChatStreamEvent | null {
    let eventType: string | null = null
    let data: string | null = null

    for (const line of block.split('\n')) {
        const trimmed = line.trim()
        if (trimmed.startsWith('event:')) {
            eventType = trimmed.slice(6).trim()
        } else if (trimmed.startsWith('data:')) {
            data = trimmed.slice(5).trim()
        } else if (trimmed.startsWith('{') || trimmed.startsWith('[')) {
            // Raw JSON without data: prefix
            data = trimmed
        }
    }

    if (!data) return null

    try {
        const parsed = JSON.parse(data)
        return {
            // parsed.type takes precedence over SSE event name,
            // because Spring Boot forwards all events as "event: message"
            // but the JSON body carries the actual semantic type.
            type: parsed.type || eventType || 'token',
            content: parsed.content ?? null,
            conversationId: parsed.conversationId ?? null,
            sources: parsed.sources ?? null,
        }
    } catch {
        return null
    }
}