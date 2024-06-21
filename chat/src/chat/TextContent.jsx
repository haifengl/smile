import React from 'react'
import './TextContent.css'

export default function TextContent({
    children = ""
}) {
    // Regular expression to match URLs
    const urlRegex = /(https?:\/\/[^\s]+)/g;

    return (
        <div className="text-content"
            dangerouslySetInnerHTML={{ __html: children.replace(urlRegex, '<a href="$&" target="_blank">$&</a>') }}
        />
    )
}