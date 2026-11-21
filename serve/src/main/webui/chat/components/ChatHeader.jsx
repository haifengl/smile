/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
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
import ThemeToggle from '../../shared/ThemeToggle'
import './ChatHeader.css'

export default function ChatHeader({
    logo,
    title,
    showThemeToggle = false,
}) {
    return (
        <div className="chat-header">
            <div className="inner-container">
                <div className="heading-container">
                    <img src={logo} alt="" className="chat-header__logo" height="28" />
                    <div className="title">{title}</div>
                </div>
                {showThemeToggle && (
                    <div className="chat-header__actions">
                        <ThemeToggle />
                    </div>
                )}
            </div>
        </div>
    )
}
