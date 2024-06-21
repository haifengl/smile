import React from 'react'
import './ChatHeader.css'

export default function MessageHeader({
    children
}) {
    return (
        <div className="message-header">
            <div className="inner-container">
                <div className="heading-container">
                    <div className="chat-title">{children}</div>
                </div>
            </div>
        </div>
    )
}