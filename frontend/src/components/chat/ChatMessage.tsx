import type { Message } from '../../hooks/useChat'
import ChatSources from './ChatSources'

interface ChatMessageProps {
    message: Message
}

const ChatMessage = ({ message }: ChatMessageProps) => {
    const isUser = message.role === 'user'

    return (
        <div className={`flex ${isUser ? 'justify-end' : 'justify-start'}`}>
            <div className={`max-w-[85%] rounded-2xl px-4 py-3 ${isUser
                ? 'bg-secondary text-gray-900 rounded-br-md'
                : 'bg-gray-100 text-gray-800 rounded-bl-md'
                }`}>
                <p className="text-sm whitespace-pre-wrap leading-relaxed">{message.content}</p>
                {message.sources && message.sources.length > 0 && (
                    <ChatSources sources={message.sources} />
                )}
            </div>
        </div>
    )
}

export default ChatMessage