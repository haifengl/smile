/*
 * Copyright (c) 2010-2025 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
import React from 'react'
import './TypingIndicator.css'

export default function TypingIndicator({
    content,
    theme = '#8dd4e8'
}) {
    return (
        <div className="typing-indicator">
            <div className="loading-animation">
                <div className="dot1" style={{ backgroundColor: theme }} />
                <div className="dot2" style={{ backgroundColor: theme }} />
                <div className="dot3" style={{ backgroundColor: theme }} />
            </div>

            <div className="text" style={{ color: theme }}>{content}</div>
        </div>
    )
}

