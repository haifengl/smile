/*
 * Copyright (c) 2010-2025 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
import React, { } from 'react'
import ChatHeader from './ChatHeader'
import MessageInput from './MessageInput'
import MessageList from './MessageList'
import ChatbotIcon from '../assets/chatbot.svg'
import "./Chat.css"

export default function Chat({
    style,
    userId,
    messages,
    onSendMessage,
    showTypingIndicator,
    logo = ChatbotIcon,
    title = "Smile Assistant",
    placeholder = "Type prompt here",
    theme = '#8dd4e8',
}) {
    return (
        <div className="chat-container" style={style}>
            <div className="message-container">
                <ChatHeader logo={logo} title={title} />
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
