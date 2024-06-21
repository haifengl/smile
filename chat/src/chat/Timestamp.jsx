import React, { useEffect, useState } from 'react'

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
            <div className="content">{dateSent}</div>
        </div>
    )
}