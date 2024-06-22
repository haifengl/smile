import React from 'react'
import OutgoingMessage from './OutgoingMessage'
import IncomingMessage from './IncomingMessage'

export default function Message({
    user,
    text,
    timestamp,
    type = "outgoing",
}) {

    return (
        type === "outgoing" ?
            <OutgoingMessage
                user={user}
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
