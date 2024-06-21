import React, { useRef, useState } from 'react'
import './MessageInput.css'

export default function MessageInput({
    onSendMessage,
    placeholder = 'Send a message...',
    theme = '#6ea9d7'
}) {
    const inputRef = useRef(null);
    const [text, setText] = useState("")
    
    const handleSubmit = () => {
        if (text.trim().length > 0) {
            inputRef.current.innerText = ''
            onSendMessage && onSendMessage(text.trim())
            setText("")
        }
    }

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
                        <input className="input-element"
                            ref={inputRef}
                            data-testid='message-input'
                            onInput={(event) => setText(event.target.innerText)}
                            contentEditable={true}
                            suppressContentEditableWarning={true}
                            onKeyDown={(event) => {
                                if (event.key === 'Enter') {
                                    event.preventDefault();  // Prevents adding a new line
                                    handleSubmit();
                                    return;
                                }
                            }}
                        />
                        {text === '' &&
                            <span className="message-placeholder">{placeholder}</span>
                        }
                    </div>
                </div>

                <div className="send-container" onClick={handleSubmit}>
                    <svg
                        fill={theme}
                        xmlns="http://www.w3.org/2000/svg"
                        width="24"
                        height="24"
                        viewBox="0 0 512 512" >
                        <path d="M498.1 5.6c10.1 7 15.4 19.1 13.5 31.2l-64 416c-1.5 9.7-7.4 18.2-16 23s-18.9 5.4-28 1.6L284 427.7l-68.5 74.1c-8.9 9.7-22.9 12.9-35.2 8.1S160 493.2 160 480V396.4c0-4 1.5-7.8 4.2-10.7L331.8 202.8c5.8-6.3 5.6-16-.4-22s-15.7-6.4-22-.7L106 360.8 17.7 316.6C7.1 311.3 .3 300.7 0 288.9s5.9-22.8 16.1-28.7l448-256c10.7-6.1 23.9-5.5 34 1.4z" />
                    </svg>
                </div>
            </form >
        </div>
    )
}