import React from 'react'
import Markdown from 'react-markdown'
import remarkGemoji from 'remark-gemoji'
import remarkGfm from 'remark-gfm'
import './TextContent.css'

export default function TextContent({
    children
}) {
    return (
        <div className="text-content">
            <Markdown className="line-break" remarkPlugins={[remarkGfm, remarkGemoji]}>
                {children}
            </Markdown>
        </div>
    )
}
