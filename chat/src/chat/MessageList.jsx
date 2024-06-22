import React, { useEffect, useRef, useState } from 'react'
import './MessageList.css'
import IncomingMessage from './IncomingMessage'
import OutgoingMessage from './OutgoingMessage'
import TypingIndicator from './TypingIndicator'

export default function MessageList({
    messages,
    userId,
    showTypingIndicator,
    typingIndicatorContent,
    theme = '#6ea9d7',
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