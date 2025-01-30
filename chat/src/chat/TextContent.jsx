/*
 * Copyright (c) 2010-2025 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */
import React from 'react'
import Markdown from 'react-markdown'
import remarkGemoji from 'remark-gemoji'
import remarkGfm from 'remark-gfm'
import SyntaxHighlighter from 'react-syntax-highlighter';
import { a11yDark } from 'react-syntax-highlighter/dist/esm/styles/hljs';
import rehypeKatex from 'rehype-katex'
import remarkMath from 'remark-math'
import 'katex/dist/katex.min.css' // `rehype-katex` does not import the CSS
import CopyButton from './CopyButton';
import './TextContent.css'

export default function TextContent({
    children
}) {
    const Pre = ({ children }) => <pre className="code-pre">
        <CopyButton>{children}</CopyButton>
        {children}
    </pre>

    return (
        <div className="text-content">
            <Markdown className="line-break"
                remarkPlugins={[remarkGfm, remarkGemoji, remarkMath]}
                rehypePlugins={[rehypeKatex]}
                components={{
                  pre: Pre,
                  code(props) {
                    const {children, className, node, ...rest} = props
                    const language = /language-(\w+)/.exec(className || '');
                    const code = String(children)
                    const multiline = /[\r\n]/.exec(code);
                    return multiline ?
                    (
                        <SyntaxHighlighter
                            {...rest}
                            PreTag="div"
                            children={code.replace(/\n$/, '')}
                            language={language ? language[1] : null}
                            style={a11yDark}
                            showLineNumbers={true}
                            wrapLongLines={true}
                        />
                    ) :
                    (
                        <div className='inlineCode' variant='outlined'>
                            <code {...rest} className={className}>
                                {children}
                            </code>
                        </div>
                    )
                  }
                }}
            >
                {children}
            </Markdown>
        </div>
    )
}
