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
import React, { useEffect, useRef, useState } from 'react'
import IncomingMessage from './IncomingMessage'
import OutgoingMessage from './OutgoingMessage'
import TypingIndicator from './TypingIndicator'
import './MessageList.css'

export default function MessageList({
    messages,
    userId,
    showTypingIndicator,
    typingIndicatorContent,
    theme = '#8dd4e8',
}) {
    const bottomBufferRef = useRef();
    const scrollContainerRef = useRef();

    const scrollToBottom = async () => {
        if (bottomBufferRef.current && scrollContainerRef.current) {
            const container = scrollContainerRef.current
            const scrollPoint = bottomBufferRef.current

            const parentRect = container.getBoundingClientRect()
            const childRect = scrollPoint.getBoundingClientRect()

            // Scroll by offset relative to parent
            const scrollOffset = childRect.top + container.scrollTop - parentRect.top;

            if (container.scrollBy) {
                container.scrollBy({ top: scrollOffset, behavior: "smooth" });
            } else {
                container.scrollTop = scrollOffset;
            }
        }
    }

    useEffect(() => {
        scrollToBottom()
    }, [messages, showTypingIndicator])

    return (
        <div className="message-list">
            <div className="scroll-background-container">
                <div className="scroll-background"/>
            </div>

            <div style={{ "height" : "100%" }}>
                <div className="scroll-container" ref={scrollContainerRef}>
                    {messages && messages.map(({ user, text, createdAt }, index) => {
                        if (user.id == (userId && userId.toLowerCase())) {
                            return <OutgoingMessage key={index}
                                    user={user}
                                    text={text}
                                    timestamp={createdAt}
                                />
                        } else {
                            return <IncomingMessage key={index}
                                    user={user}
                                    text={text}
                                    timestamp={createdAt}
                                />
                        }
                    })}

                    {showTypingIndicator &&
                        <TypingIndicator
                            content={typingIndicatorContent}
                            theme={theme} />
                    }

                    <div>
                        <div className="buffer" ref={bottomBufferRef} />
                    </div>
                </div>
            </div>
        </div>
    )
}

