import React, { useEffect } from 'react'
import './Message.css'
import ProfilePicture from '../assets/profile.png'
import TextContent from './TextContent'
import Timestamp from './Timestamp'

export default function IncomingMessage({
    text,
    timestamp,
    user,
}) {
    const [avatar, setAvatar] = React.useState(ProfilePicture)

    useEffect(() => {
        if (user?.avatar && user.avatar.trim().length > 0) {
            setAvatar(user.avatar)
        }
    }, [user])

    return (
        <div data-testid="incoming-message" className='incoming-wrapper'>
            <div className="text-wrapper">
                <div className="header-container">
                    <div className="picture-container">
                        <img src={avatar} className="profile-picture"
                            onError={() => setAvatar(ProfilePicture)}
                        />
                    </div>
                    <div className="name">{user?.name}</div>
                </div>

                <div style={{ display: "flex" }}>
                    <div className="incoming-message-container">
                        <div className="incoming-background"/>
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
