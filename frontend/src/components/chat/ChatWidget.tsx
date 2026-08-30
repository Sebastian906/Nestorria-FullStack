import { useEffect, useState } from 'react'
import { useChat } from '../../hooks/useChat'
import { assets } from '../../assets/data'
import ChatMessage from './ChatMessage'
import ChatInput from './ChatInput'

const ChatWidget = () => {
    const {
        messages,
        isStreaming,
        error,
        remainingMessages,
        sendMessage,
        clearMessages,
        clearError,
    } = useChat()
    const [isOpen, setIsOpen] = useState(false)

    useEffect(() => {
        const handleOpen = () => setIsOpen(true)
        window.addEventListener('open-chat', handleOpen)
        return () => window.removeEventListener('open-chat', handleOpen)
    }, [])

    return (
        <div className="fixed bottom-6 right-6 z-50">
            {/* Toggle Button */}
            <button
                onClick={() => setIsOpen(prev => !prev)}
                className={`w-14 h-14 rounded-full shadow-lg flex items-center justify-center transition-all duration-300 ${isOpen
                    ? 'bg-gray-800 text-white rotate-0'
                    : 'bg-secondary text-gray-900 hover:scale-105'
                    }`}
                aria-label={isOpen ? 'Close chat' : 'Open chat'}
            >
                {isOpen ? (
                    <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                        <path d="M18 6 6 18" /><path d="m6 6 12 12" />
                    </svg>
                ) : (
                    <img src={assets.chat} alt="" className="w-6 h-6" />
                )}
            </button>

            {/* Chat Panel */}
            {isOpen && (
                <div className="absolute bottom-20 right-0 w-95 max-w-[calc(100vw-3rem)] bg-white rounded-2xl shadow-2xl ring-1 ring-slate-900/10 overflow-hidden flex flex-col"
                    style={{ height: 'min(520px, calc(100vh - 160px))' }}>
                    {/* Header */}
                    <div className="flex items-center justify-between px-4 py-3 bg-gray-800 text-white">
                        <div className="flex items-center gap-2">
                            <img src={assets.chat} alt="" className="w-5 h-5" />
                            <span className="font-semibold text-sm">AI Assistant</span>
                        </div>
                        <div className="flex items-center gap-3">
                            <span className="text-xs text-gray-400">
                                {remainingMessages}/20 msg
                            </span>
                            {messages.length > 0 && (
                                <button
                                    onClick={clearMessages}
                                    className="text-xs text-gray-400 hover:text-white transition-colors"
                                    title="New conversation"
                                >
                                    <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                                        <path d="M21 12a9 9 0 0 0-9-9 9.75 9.75 0 0 0-6.74 2.74L3 8" />
                                        <path d="M3 3v5h5" />
                                        <path d="M3 12a9 9 0 0 0 9 9 9.75 9.75 0 0 0 6.74-2.74L21 16" />
                                        <path d="M16 16h5v5" />
                                    </svg>
                                </button>
                            )}
                        </div>
                    </div>

                    {/* Messages */}
                    <div className="flex-1 overflow-y-auto px-4 py-4 space-y-4">
                        {messages.length === 0 && (
                            <div className="text-center text-gray-400 py-8">
                                <p className="text-sm">Hi! Ask me about properties, contracts, or business rules.</p>
                            </div>
                        )}
                        {messages.map((msg) => (
                            <ChatMessage key={msg.id} message={msg} />
                        ))}
                        {isStreaming && messages[messages.length - 1]?.content === '' && (
                            <div className="flex items-center gap-1 text-gray-400 text-sm">
                                <span className="animate-pulse">●</span>
                                <span className="animate-pulse" style={{ animationDelay: '0.2s' }}>●</span>
                                <span className="animate-pulse" style={{ animationDelay: '0.4s' }}>●</span>
                            </div>
                        )}
                    </div>

                    {/* Error */}
                    {error && (
                        <div className="px-4 py-2 bg-red-50 border-t border-red-100">
                            <div className="flex items-center justify-between">
                                <p className="text-xs text-red-600">{error}</p>
                                <button onClick={clearError} className="text-red-400 hover:text-red-600">
                                    <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                                        <path d="M18 6 6 18" /><path d="m6 6 12 12" />
                                    </svg>
                                </button>
                            </div>
                        </div>
                    )}

                    {/* Input */}
                    <ChatInput onSend={sendMessage} disabled={isStreaming} />
                </div>
            )}
        </div>
    )
}

export default ChatWidget