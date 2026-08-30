interface ChatSourcesProps {
    sources: string[]
}

const ChatSources = ({ sources }: ChatSourcesProps) => {
    if (!sources || sources.length === 0) return null

    return (
        <div className="mt-2 pt-2 border-t border-gray-200/50">
            <p className="text-[11px] text-gray-400 mb-1 font-medium uppercase tracking-wide">Sources</p>
            <div className="flex flex-wrap gap-1">
                {sources.map((source, i) => (
                    <span
                        key={i}
                        className="inline-block text-[11px] px-2 py-0.5 rounded-full bg-secondary/10 text-secondary font-medium"
                    >
                        {source}
                    </span>
                ))}
            </div>
        </div>
    )
}

export default ChatSources