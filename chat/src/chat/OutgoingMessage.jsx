import React, { useContext } from 'react'
import './Message.css'
import TextContent from './TextContent'
import Timestamp from './Timestamp'

export default function OutgoingMessage({
    text,
    timestamp,
}) {
    return (
        <div data-testid="outgoing-message" className='outgoing-wrapper'>
            <div className="text-wrapper">
                <div style={{ display: "flex" }}>
                    <div className="outgoing-message-container">
                        <div className="outgoing-background"/>
                        <TextContent>
                            {text}
                        </TextContent>

                        <Timestamp date={timestamp}/>
                    </div>
                </div>
            </div>
        </div>
    )
}