/*
 * Copyright (c) 2010-2025 Haifeng Li. All rights reserved.
 *
 * SMILE Serve is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * SMILE Serve is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with SMILE Serve. If not, see
 * <https://www.gnu.org/licenses/>.
 */
import React from 'react'
import './ChatHeader.css'

export default function ChatHeader({
    logo,
    title
}) {
    return (
        <div className="chat-header">
            <div className="inner-container">
                <div className="heading-container">
                    <img src={logo} height='32px' />
                    <div className="title">{title}</div>
                </div>
            </div>
        </div>
    )
}
