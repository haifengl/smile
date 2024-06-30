import React, { useState } from 'react'
import Check from '../assets/check-circle.svg?react';
import PaperPlane from '../assets/paper-plane.svg?react';
import './MessageInput.css'

export default function MessageInput({
    onSendMessage,
    placeholder = 'Send a message...',
    theme = '#6ea9d7'
}) {
    const [text, setText] = useState("")
    const [submit, setSubmit] = React.useState(false);
    
    const handleSubmit = () => {
        if (!submit && text.trim().length > 0) {
            onSendMessage && onSendMessage(text.trim())
            setText("")

            setSubmit(true);
            setTimeout(() => {
                setSubmit(false);
            }, 500);
        }
    }

    const button = submit ? <Check height='24px' fill='#0af20a' />
        : <PaperPlane height='24px' onClick={handleSubmit} fill={theme} />;

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
                            data-testid='message-input'
                            type="text"
                            value={text}
                            placeholder={placeholder}
                            onChange={(e) => (setText(e.target.value))}
                            onKeyDown={(e) => {
                                if (e.key === 'Enter') {
                                    e.preventDefault();  // Prevents adding a new line
                                    handleSubmit();
                                    return;
                                }
                            }}
                        />
                    </div>
                </div>

                <div className="send-container" onClick={handleSubmit}>
                    {button}
                </div>
            </form >
        </div>
    )
}