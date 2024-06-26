import React, { } from 'react'
import ChatHeader from './ChatHeader'
import MessageInput from './MessageInput'
import MessageList from './MessageList'
import "./Chat.css"

export default function Chat({
    style,
    userId,
    messages,
    onSendMessage,
    showTypingIndicator,
    title = "SmileChat",
    placeholder = "Type prompt here",
    theme = '#6ea9d7',
}) {
    return (
        <div className="chat-container" style={style}>
            <div className="message-container">
                <ChatHeader>
                    {title}
                </ChatHeader>
                <MessageList
                    messages={messages}
                    userId={userId}
                    showTypingIndicator={showTypingIndicator}
                    theme={theme}
                />
                <MessageInput
                    onSendMessage={onSendMessage}
                    placeholder={placeholder}
                    theme={theme}
                />
            </div>
        </div>
    )
}
