/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
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
import React from 'react'
import Markdown from 'react-markdown'
import remarkGemoji from 'remark-gemoji'
import remarkGfm from 'remark-gfm'
import SyntaxHighlighter from 'react-syntax-highlighter';
import { atomOneLight } from 'react-syntax-highlighter/dist/esm/styles/hljs';
import rehypeKatex from 'rehype-katex'
import remarkMath from 'remark-math'
import CopyButton from './CopyButton';
import { MarkdownImage, MarkdownLink } from './MediaContent';
import 'katex/dist/katex.min.css' // rehype-katex does not import the CSS
import './TextContent.css'

export default function TextContent({
    children,
    downloadable = false,
    compact = false,
}) {
    // react-markdown only accepts a string; join if callers pass multiple children.
    const markdown = typeof children === 'string'
        ? children
        : React.Children.toArray(children).join('')

    const Pre = ({ children }) => <pre className="code-pre">
        <CopyButton>{children}</CopyButton>
        {children}
    </pre>

    const Img = (props) => <MarkdownImage {...props} downloadable={downloadable} />
    const Link = (props) => <MarkdownLink {...props} downloadable={downloadable} />

    return (
        <div className={`text-content line-break${compact ? ' text-content--compact' : ''}`}>
            <Markdown
                remarkPlugins={[remarkGfm, remarkGemoji, remarkMath]}
                rehypePlugins={[rehypeKatex]}
                components={{
                  pre: Pre,
                  img: Img,
                  a: Link,
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
                            style={atomOneLight}
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
                {markdown}
            </Markdown>
        </div>
    )
}
