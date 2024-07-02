import React from 'react'
import './TypingIndicator.css'

export default function TypingIndicator({
    content,
    theme = '#8dd4e8'
}) {
    return (
        <div className="typing-indicator">
            <div className="loading-animation">
                <div className="dot1" style={{ backgroundColor: theme }} />
                <div className="dot2" style={{ backgroundColor: theme }} />
                <div className="dot3" style={{ backgroundColor: theme }} />
            </div>

            <div className="text" style={{ color: theme }}>{content}</div>
        </div>
    )
}

