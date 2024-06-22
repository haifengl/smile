import React from 'react'
import './ChatHeader.css'

export default function ChatHeader({
    children
}) {
    return (
        <div className="chat-header">
            <div className="inner-container">
                <div className="heading-container">
                    <div className="chat-title">{children}</div>
                </div>
            </div>
        </div>
    )
}