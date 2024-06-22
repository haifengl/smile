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
    const [messagesWasEmpty, setMessagesWasEmpty] = useState(true)
    const containerRef = useRef()
    const bottomBufferRef = useRef()
    const scrollContainerRef = useRef()

    const detectBottom = (ref) => {
        if (ref.current) {
            const threshold = 100;
            return ref.current.scrollHeight - ref.current.scrollTop <= ref.current.clientHeight + threshold;
        }
        return false;
    }

    useEffect(() => {
        // scroll to the bottom when the scroll view is first rendered
        // and messages have rendered.
        if (bottomBufferRef.current && scrollContainerRef.current && !messagesWasEmpty) {
            scrollToBottom()
        }

    }, [messagesWasEmpty, bottomBufferRef.current, scrollContainerRef.current])


    useEffect(() => {
        if (!messages) {
            setMessagesWasEmpty(true)
        }

        if (messages) {
            if (messagesWasEmpty) {
                // scroll to bottom if the messages object was previously empty
                // e.g., when the first page of messages arrives.
                // However, don't scroll to bottom if the next page of messages arrives.
                setMessagesWasEmpty(false)
                scrollToBottom()
            }

            // scroll to bottom when closer to the bottom of the scroll bar
            // and a new message arrives.
            if (detectBottom(scrollContainerRef)) {
                scrollToBottom()
            }

        }
    }, [messages])

    useEffect(() => {
        scrollToBottom()
    }, [showTypingIndicator])

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

    return (
        <div className="message-list" ref={containerRef}>
            <div className="scroll-background-container">
                <div className="scroll-background"/>
            </div>

            <div style={{ "height" : "100%" }}>
                <div className="scroll-container"
                    ref={scrollContainerRef}
                >
                    {messages && scrollContainerRef.current && bottomBufferRef.current && messages.map(({ user, text, createdAt }, index) => {
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