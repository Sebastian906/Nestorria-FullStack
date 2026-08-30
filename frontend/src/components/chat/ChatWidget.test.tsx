import { render, screen } from '../../test/utils'
import { vi, describe, it, expect, beforeEach } from 'vitest'
import ChatWidget from './ChatWidget'

// Mock the useChat hook
const mockSendMessage = vi.fn()
const mockClearMessages = vi.fn()
const mockClearError = vi.fn()

vi.mock('../../hooks/useChat', () => ({
    useChat: () => ({
        messages: [],
        isStreaming: false,
        error: null,
        remainingMessages: 20,
        conversationId: null,
        sendMessage: mockSendMessage,
        clearMessages: mockClearMessages,
        clearError: mockClearError,
    }),
}))

// Mock Clerk
vi.mock('@clerk/react', () => ({
    useAuth: () => ({
        getToken: vi.fn().mockResolvedValue('test-token'),
    }),
}))

describe('ChatWidget', () => {
    beforeEach(() => {
        vi.clearAllMocks()
    })

    it('renders the chat toggle button', () => {
        render(<ChatWidget />)
        expect(screen.getByLabelText(/open chat/i)).toBeInTheDocument()
    })

    it('opens chat panel when toggle is clicked', async () => {
        const user = (await import('@testing-library/user-event')).default.setup()
        render(<ChatWidget />)

        await user.click(screen.getByLabelText(/open chat/i))

        expect(screen.getByText(/ai assistant/i)).toBeInTheDocument()
        expect(screen.getByText(/20\/20 msg/)).toBeInTheDocument()
    })

    it('closes chat panel when close button is clicked', async () => {
        const user = (await import('@testing-library/user-event')).default.setup()
        render(<ChatWidget />)

        await user.click(screen.getByLabelText(/open chat/i))
        expect(screen.getByText(/ai assistant/i)).toBeInTheDocument()

        await user.click(screen.getByLabelText(/close chat/i))
        expect(screen.queryByText(/ai assistant/i)).not.toBeInTheDocument()
    })

    it('shows welcome message when no messages', async () => {
        const user = (await import('@testing-library/user-event')).default.setup()
        render(<ChatWidget />)

        await user.click(screen.getByLabelText(/open chat/i))

        expect(screen.getByText(/ask me about properties/i)).toBeInTheDocument()
    })
})