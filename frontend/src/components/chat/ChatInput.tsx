import { useState, useRef, type KeyboardEvent } from 'react'

interface ChatInputProps {
    onSend: (message: string) => void
    disabled: boolean
}

const ChatInput = ({ onSend, disabled }: ChatInputProps) => {
    const [value, setValue] = useState('')
    const textareaRef = useRef<HTMLTextAreaElement>(null)

    const handleSend = () => {
        const trimmed = value.trim()
        if (!trimmed || disabled) return
        onSend(trimmed)
        setValue('')
        if (textareaRef.current) {
            textareaRef.current.style.height = 'auto'
        }
    }

    const handleKeyDown = (e: KeyboardEvent<HTMLTextAreaElement>) => {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault()
            handleSend()
        }
    }

    const handleInput = () => {
        const el = textareaRef.current
        if (el) {
            el.style.height = 'auto'
            el.style.height = `${Math.min(el.scrollHeight, 120)}px`
        }
    }

    return (
        <div className="px-4 py-3 border-t border-slate-100">
            <div className="flex items-end gap-2 bg-gray-100 rounded-xl px-3 py-2">
                <textarea
                    ref={textareaRef}
                    value={value}
                    onChange={(e) => setValue(e.target.value)}
                    onKeyDown={handleKeyDown}
                    onInput={handleInput}
                    placeholder={disabled ? 'Waiting for response...' : 'Type your message...'}
                    disabled={disabled}
                    rows={1}
                    className="flex-1 bg-transparent text-sm resize-none outline-none placeholder:text-gray-400 disabled:opacity-50 max-h-[120px]"
                />
                <button
                    onClick={handleSend}
                    disabled={disabled || !value.trim()}
                    className="p-2 rounded-lg bg-secondary text-gray-900 hover:bg-secondary/80 disabled:opacity-30 disabled:cursor-not-allowed transition-colors flex-shrink-0"
                    aria-label="Send message"
                >
                    <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                        <path d="m22 2-7 20-4-9-9-4Z" /><path d="M22 2 11 13" />
                    </svg>
                </button>
            </div>
        </div>
    )
}

export default ChatInput