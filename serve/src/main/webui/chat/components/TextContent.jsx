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
import React, { useMemo } from 'react'
import Markdown from 'react-markdown'
import remarkGemoji from 'remark-gemoji'
import remarkGfm from 'remark-gfm'
import SyntaxHighlighter from 'react-syntax-highlighter';
import { atomOneLight } from 'react-syntax-highlighter/dist/esm/styles/hljs';
import rehypeKatex from 'rehype-katex'
import remarkMath from 'remark-math'
import CopyButton from './CopyButton';
import { MarkdownImage, MarkdownLink } from './MediaContent';
import MermaidDiagram from './MermaidDiagram'
import { formatThinkingAsMarkdown } from '../thinkingUtils'
import 'katex/dist/katex.min.css' // rehype-katex does not import the CSS
import './TextContent.css'

function isMermaidChild(children) {
    const child = React.Children.toArray(children)[0]
    return React.isValidElement(child) && child.props?.['data-mermaid'] != null
}

export default function TextContent({
    children,
    downloadable = false,
    compact = false,
    streaming = false,
}) {
    // react-markdown only accepts a string; join if callers pass multiple children.
    const raw = typeof children === 'string'
        ? children
        : React.Children.toArray(children).join('')
    // react-markdown strips unknown HTML such as <think>, which would leave
    // thinking text as a plain <p>. Convert to blockquotes first.
    const markdown = formatThinkingAsMarkdown(raw)

    const components = useMemo(() => {
        const Pre = ({ children: preChildren }) => {
            // Mermaid renders its own frame; avoid wrapping in <pre>.
            if (isMermaidChild(preChildren)) {
                return <>{preChildren}</>
            }
            return (
                <pre className="code-pre">
                    <CopyButton>{preChildren}</CopyButton>
                    {preChildren}
                </pre>
            )
        }

        const Img = (props) => <MarkdownImage {...props} downloadable={downloadable} />
        const Link = (props) => <MarkdownLink {...props} downloadable={downloadable} />

        return {
            pre: Pre,
            img: Img,
            a: Link,
            code(props) {
                const { children: codeChildren, className, node, ...rest } = props
                const language = /language-(\w+)/.exec(className || '')
                const lang = language ? language[1] : null
                const code = String(codeChildren).replace(/\n$/, '')
                if (lang === 'mermaid') {
                    return <MermaidDiagram chart={code} streaming={streaming} />
                }
                const multiline = /[\r\n]/.exec(code)
                return multiline ? (
                    <SyntaxHighlighter
                        {...rest}
                        PreTag="div"
                        children={code}
                        language={lang}
                        style={atomOneLight}
                        showLineNumbers={true}
                        wrapLongLines={true}
                    />
                ) : (
                    <div className="inlineCode" variant="outlined">
                        <code {...rest} className={className}>
                            {codeChildren}
                        </code>
                    </div>
                )
            },
        }
    }, [downloadable, streaming])

    return (
        <div className={`text-content line-break${compact ? ' text-content--compact' : ''}`}>
            <Markdown
                remarkPlugins={[remarkGfm, remarkGemoji, remarkMath]}
                rehypePlugins={[rehypeKatex]}
                components={components}
            >
                {markdown}
            </Markdown>
        </div>
    )
}
