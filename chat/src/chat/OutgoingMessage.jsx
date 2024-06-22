import React, { useEffect } from 'react'
import './Message.css'
import ProfileIcon from '../assets/profile.svg'
import TextContent from './TextContent'
import Timestamp from './Timestamp'

export default function OutgoingMessage({
    user,
    text,
    timestamp,
}) {
    const [avatar, setAvatar] = React.useState(ProfileIcon)

    useEffect(() => {
        if (user?.avatar && user.avatar.trim().length > 0) {
            setAvatar(user.avatar)
        }
    }, [user])

    return (
        <div data-testid="outgoing-message" className="outgoing-wrapper">
            <div className="text-wrapper">
                <div className="outgoing-header-container">
                    <div className="picture-container">
                        <img src={avatar} className="profile-picture"
                            onError={() => setAvatar(ProfileIcon)}
                        />
                    </div>
                    <div className="name">{user?.name}</div>
                </div>

                <div>
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