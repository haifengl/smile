/*
 * Copyright (c) 2010-2025 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * it under the terms of the GNU General Public License as published by
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
import React, { useEffect } from 'react'
import ProfileIcon from '../assets/profile.svg'
import TextContent from './TextContent'
import Timestamp from './Timestamp'
import './Message.css'

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


    let start = text.indexOf('<think>');
    let end = text.indexOf('</think>');
    if (start !== -1) {
        let think = "";
        let answer = "";
        if (end !== -1) {
            think = text.substring(start + 7, end).trimEnd();
            answer = text.substring(end + 8);
        } else {
            think = text.substring(start + 7);
        }

        // think is not empty
        if (think) {
            // block quote
            think = think.replaceAll('\n', '\n> ');
            if (!think.startsWith('\n> ')) {
                think = '> ' + think;
            }
            think += '\n';
        }

        text = think + answer;
    }

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
