import React from 'react'
import Markdown from 'react-markdown'
import remarkGemoji from 'remark-gemoji'
import remarkGfm from 'remark-gfm'
import SyntaxHighlighter from 'react-syntax-highlighter';
import { a11yDark } from 'react-syntax-highlighter/dist/esm/styles/hljs';
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
                remarkPlugins={[remarkGfm, remarkGemoji]}
                components={{
                  pre: Pre,
                  code(props) {
                    const {children, className, node, ...rest} = props
                    const language = /language-(\w+)/.exec(className || '');
                    const code = String(children)
                    const multiline = /\r|\n/.exec(code);
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
                            useInlineStyles={true}
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
