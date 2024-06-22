import React, { useEffect } from 'react'
import './Message.css'
import ProfileIcon from '../assets/profile.svg'
import TextContent from './TextContent'
import Timestamp from './Timestamp'

export default function IncomingMessage({
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
        <div data-testid="incoming-message" className="incoming-wrapper">
            <div className="text-wrapper">
                <div className="incoming-header-container">
                    <div className="picture-container">
                        <img src={avatar} className="profile-picture"
                            onError={() => setAvatar(ProfileIcon)}
                        />
                    </div>
                    <div className="name">{user?.name}</div>

                    <Timestamp date={timestamp}/>
                </div>

                <div className="incoming-message-container">
                    <div className="incoming-background"/>
                        <TextContent>
                            {text}
                        </TextContent>
                    </div>
                </div>
        </div>
    )
}
