import React from 'react'
import OutgoingMessage from './OutgoingMessage'
import IncomingMessage from './IncomingMessage'

export default function Message({
    text,
    timestamp,
    user,
    type = "outgoing",
}) {

    return (
        type === "outgoing" ?
            <OutgoingMessage
                text={text}
                timestamp={timestamp}
            />
            :
            <IncomingMessage
                user={user}
                text={text}
                timestamp={timestamp}
            />

    )
}
