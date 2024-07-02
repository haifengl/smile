import React from 'react'
import './ChatHeader.css'

export default function ChatHeader({
    logo,
    title
}) {
    return (
        <div className="chat-header">
            <div className="inner-container">
                <div className="heading-container">
                    <img src={logo} height='32px' />
                    <div className="title">{title}</div>
                </div>
            </div>
        </div>
    )
}