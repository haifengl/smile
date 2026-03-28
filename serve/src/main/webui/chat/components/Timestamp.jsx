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
import React, { useEffect, useState } from 'react'
import './Timestamp.css'

function calculateDifferences(date) {
    try {
        const currentDate = new Date()
        const timeDifference = (new Date(currentDate.toUTCString())).getTime() - (new Date(date ? date.toUTCString() : "")).getTime();
        const minutesAgo = Math.floor(timeDifference / (1000 * 60));
        const hoursAgo = Math.floor(minutesAgo / 60);
        const daysAgo = Math.floor(hoursAgo / 24);

        return {
            minutesAgo,
            hoursAgo,
            daysAgo
        }
    } catch (e) {
        return {
            minutesAgo: 0,
            hoursAgo: 0,
            daysAgo: 0
        }
    }
}

function calculateTimeAgo(date) {
    const diff = calculateDifferences(date)

    if (diff.minutesAgo < 1) {
        return 'just now';
    } else if (diff.minutesAgo < 60) {
        return `${diff.minutesAgo}m`
    } else if (diff.hoursAgo < 24) {
        return `${diff.hoursAgo}h`
    } else {
        return `${diff.daysAgo}d`
    }
}

export default function Timestamp({
    date,
}) {
    const [dateSent, setDateSent] = useState()

    useEffect(() => {
        function updateDateSent() {
            if (date) {
                setDateSent(calculateTimeAgo(date))
            }
        }

        updateDateSent()
        const intervalId = setInterval(() => updateDateSent(), 60_000)
        return () => clearInterval(intervalId);
    }, [])

    return (
        <div className="timestamp-container">
            <div className="timestamp-content">{dateSent}</div>
        </div>
    )
}