import React, { useEffect, useRef, useState } from 'react'
import './MessageList.css'
import Message from './Message'
import TypingIndicator from './TypingIndicator'

export default function MessageList({
    messages,
    userId,
    onScrollToTop,
    showTypingIndicator,
    typingIndicatorContent,
    theme = '#6ea9d7',
}) {
    /** keeps track of whether messages was previously empty or whether it has already scrolled */
    const [messagesWasEmpty, setMessagesWasEmpty] = useState(true)
    const containerRef = useRef()
    const bottomBufferRef = useRef()
    const scrollContainerRef = useRef()

    const detectTop = (ref) => {
        if (ref.current) {
            return ref.current.scrollTop < 50
        }
        return false
    }

    const detectBottom = (ref) => {
        if (ref.current) {
            const threshold = 100;
            return ref.current.scrollHeight - ref.current.scrollTop <= ref.current.clientHeight + threshold;
        }
        return false;
    }

    useEffect(() => {
        //detecting when the scroll view is first rendered and messages have rendered then you can scroll to the bottom
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
                //if the messages object was previously empty then scroll to bottom
                // this is for when the first page of messages arrives
                //if a user has instead scrolled to the top and the next page of messages arrives then don't scroll to bottom

                setMessagesWasEmpty(false)
                scrollToBottom()
            }

            // when closer to the bottom of the scroll bar and a new message arrives then scroll to bottom
            if (detectBottom(scrollContainerRef)) {
                scrollToBottom()
            }

        }
    }, [messages])


    useEffect(() => {
        //TODO when closer to the bottom of the scroll bar and a new message arrives then scroll to bottom
        if (detectBottom(scrollContainerRef)) {
            scrollToBottom()
        }
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
                container.scrollBy({ top: scrollOffset, behavior: "auto" });
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
                    onScroll={() => {
                        //detect when scrolled to top
                        if (detectTop(scrollContainerRef)) {
                            onScrollToTop && onScrollToTop()
                        }
                    }}
                >
                    {messages && scrollContainerRef.current && bottomBufferRef.current && messages.map(({ user, text, media, loading: messageLoading, seen, createdAt }, index) => {
                        if (user.id == (userId && userId.toLowerCase())) {
                            // my message
                            return <Message key={index}
                                    type="outgoing"
                                    text={text}
                                    timestamp={createdAt}
                                />
                        } else {
                            // other message
                            return <Message key={index}
                                    type='incoming'
                                    text={text}
                                    user={user}
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