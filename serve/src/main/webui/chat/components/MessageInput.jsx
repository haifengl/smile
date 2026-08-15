/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
import React, { useEffect, useRef, useState } from 'react'
import './MessageInput.css'

export default function MessageInput({
    onSendMessage,
    placeholder = 'Send a message...',
    theme = '#8dd4e8'
}) {
    const [text, setText] = useState('')
    const [submit, setSubmit] = useState(false)
    const textareaRef = useRef(null)

    const resizeTextarea = () => {
        const el = textareaRef.current
        if (!el) {
            return
        }
        el.style.height = 'auto'
        const maxHeight = parseFloat(getComputedStyle(el).maxHeight)
        const next = Number.isFinite(maxHeight)
            ? Math.min(el.scrollHeight, maxHeight)
            : el.scrollHeight
        el.style.height = `${next}px`
    }

    useEffect(() => {
        resizeTextarea()
    }, [text])

    const handleSubmit = () => {
        if (!submit && text.trim().length > 0) {
            onSendMessage && onSendMessage(text.trim())
            setText('')
            setSubmit(true)
            setTimeout(() => {
                setSubmit(false)
            }, 500)
        }
    }

    const button = submit ?
          <svg xmlns="http://www.w3.org/2000/svg"
               viewBox="0 0 512 512"
               fill={theme}
               width="24"
               height="24"
          >
               <path d="M504 256c0 136.967-111.033 248-248 248S8 392.967 8 256 119.033 8 256 8s248 111.033 248 248zM227.314 387.314l184-184c6.248-6.248 6.248-16.379 0-22.627l-22.627-22.627c-6.248-6.249-16.379-6.249-22.628 0L216 308.118l-70.059-70.059c-6.248-6.248-16.379-6.248-22.628 0l-22.627 22.627c-6.248 6.248-6.248 16.379 0 22.627l104 104c6.249 6.249 16.379 6.249 22.628.001z"/>
          </svg>
        : <svg xmlns="http://www.w3.org/2000/svg"
              viewBox="0 0 512 512"
              fill={theme}
              width="24"
              height="24"
          >
              <path d="M476 3.2L12.5 270.6c-18.1 10.4-15.8 35.6 2.2 43.2L121 358.4l287.3-253.2c5.5-4.9 13.3 2.6 8.6 8.3L176 407v80.5c0 23.6 28.5 32.9 42.5 15.8L282 426l124.6 52.2c14.2 6 30.4-2.9 33-18.2l72-432C515 7.8 493.3-6.8 476 3.2z"/>
          </svg>

    return (
        <div className="message-input">
            <form className="input-form"
                data-testid="message-form"
                onSubmit={(e) => {
                    e.preventDefault()
                    handleSubmit()
                }}
            >
                <div className="attach-placeholder" />
                <div className="input-container">
                    <div className="input-background" style={{ backgroundColor: theme }}/>
                    <div className="input-element-container">
                        <textarea
                            ref={textareaRef}
                            className="input-element"
                            data-testid="message-input"
                            rows={1}
                            value={text}
                            placeholder={placeholder}
                            onChange={(e) => setText(e.target.value)}
                            onKeyDown={(e) => {
                                if (e.key === 'Enter' && !e.shiftKey) {
                                    e.preventDefault()
                                    handleSubmit()
                                }
                            }}
                        />
                    </div>
                </div>

                <div className="send-container" onClick={handleSubmit}>
                    {button}
                </div>
            </form>
        </div>
    )
}
