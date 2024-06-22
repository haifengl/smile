import React from 'react'
import './TextContent.css'
import Markdown from 'react-markdown'
import remarkGfm from 'remark-gfm'

export default function TextContent({
    children
}) {
    return (
        <div className="text-content">
            <Markdown remarkPlugins={[remarkGfm]}
                components={{
                    p: React.Fragment,
                }}
            >
                {children}
            </Markdown>
        </div>
    )
}
