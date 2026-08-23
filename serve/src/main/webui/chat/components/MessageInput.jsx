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
import React, { useEffect, useRef, useState } from 'react'
import {
  classifyFile,
  sizeLimitFor,
  uploadMedia,
} from '../mediaUtils'
import './MessageInput.css'

const ACCEPT = 'image/*,video/*,audio/*,.txt,.md,.csv,.json,text/plain'

export default function MessageInput({
    onSendMessage,
    conversationId,
    disabled = false,
    placeholder = 'Send a message...',
    theme = '#8dd4e8'
}) {
    const [text, setText] = useState('')
    const [submit, setSubmit] = useState(false)
    const [attachments, setAttachments] = useState([])
    const [error, setError] = useState('')
    const [uploading, setUploading] = useState(false)
    const [dragOver, setDragOver] = useState(false)
    const textareaRef = useRef(null)
    const fileInputRef = useRef(null)

    const resizeTextarea = () => {
        const el = textareaRef.current
        if (!el) {
            return
        }
        el.style.height = 'auto'
        const maxHeight = parseFloat(getComputedStyle(el).maxHeight)
        const next = Number.isFinite(maxHeight)
            ? Math.min(el.scrollHeight, maxHeight)
            : el.scrollHeight
        el.style.height = `${next}px`
    }

    useEffect(() => {
        resizeTextarea()
    }, [text])

    useEffect(() => {
        return () => {
            attachments.forEach((att) => {
                if (att.previewUrl) {
                    URL.revokeObjectURL(att.previewUrl)
                }
            })
        }
    }, [attachments])

    const canSend = !disabled && !uploading
        && (text.trim().length > 0 || attachments.length > 0)

    const addFiles = async (fileList) => {
        if (!fileList?.length || disabled) return
        setError('')
        const next = [...attachments]

        for (const file of fileList) {
            const kind = classifyFile(file)
            const limit = sizeLimitFor(kind)
            if (file.size > limit) {
                setError(`${file.name} exceeds ${Math.round(limit / (1024 * 1024))} MiB limit`)
                continue
            }

            if (kind === 'text') {
                try {
                    const body = await file.text()
                    setText((prev) => (prev ? `${prev}\n${body}` : body))
                } catch {
                    setError(`Failed to read ${file.name}`)
                }
                continue
            }

            const previewUrl = (kind === 'image' || kind === 'video' || kind === 'audio')
                ? URL.createObjectURL(file)
                : null
            next.push({
                id: `${Date.now()}-${file.name}`,
                file,
                kind,
                name: file.name,
                mime: file.type || 'application/octet-stream',
                size: file.size,
                previewUrl,
                uploading: false,
            })
        }
        setAttachments(next)
    }

    const removeAttachment = (id) => {
        setAttachments((prev) => {
            const target = prev.find((a) => a.id === id)
            if (target?.previewUrl) URL.revokeObjectURL(target.previewUrl)
            return prev.filter((a) => a.id !== id)
        })
    }

    const handleSubmit = async () => {
        if (!canSend || submit) return
        if (!conversationId) {
            setError('Conversation not ready yet')
            return
        }

        setSubmit(true)
        setUploading(true)
        setError('')

        try {
            const uploaded = []
            for (const att of attachments) {
                const result = await uploadMedia(conversationId, att.file)
                uploaded.push({
                    type: att.kind,
                    contentId: result.content_id,
                    url: result.url,
                    mime: result.mime_type || att.mime,
                    name: result.filename || att.name,
                    size: result.size_bytes ?? att.size,
                })
                if (att.previewUrl) URL.revokeObjectURL(att.previewUrl)
            }

            onSendMessage?.({
                text: text.trim(),
                attachments: uploaded,
            })
            setText('')
            setAttachments([])
        } catch (err) {
            setError(err.message || 'Upload failed')
        } finally {
            setUploading(false)
            setTimeout(() => setSubmit(false), 500)
        }
    }

    const button = submit ?
          <svg xmlns="http://www.w3.org/2000/svg"
               viewBox="0 0 512 512"
               fill={theme}
               width="24"
               height="24"
          >
               <path d="M504 256c0 136.967-111.033 248-248 248S8 392.967 8 256 119.033 8 256 8s248 111.033 248 248zM227.314 387.314l184-184c6.248-6.248 6.248-16.379 0-22.627l-22.627-22.627c-6.248-6.249-16.379-6.249-22.628 0L216 308.118l-70.059-70.059c-6.248-6.248-16.379-6.248-22.628 0l-22.627 22.627c-6.248 6.248-6.248 16.379 0 22.627l104 104c6.249 6.249 16.379 6.249 22.628.001z"/>
          </svg>
        : <svg xmlns="http://www.w3.org/2000/svg"
              viewBox="0 0 512 512"
              fill={theme}
              width="24"
              height="24"
          >
              <path d="M476 3.2L12.5 270.6c-18.1 10.4-15.8 35.6 2.2 43.2L121 358.4l287.3-253.2c5.5-4.9 13.3 2.6 8.6 8.3L176 407v80.5c0 23.6 28.5 32.9 42.5 15.8L282 426l124.6 52.2c14.2 6 30.4-2.9 33-18.2l72-432C515 7.8 493.3-6.8 476 3.2z"/>
          </svg>

    return (
        <div className="message-input">
            {attachments.length > 0 && (
                <div className="attachment-chips">
                    {attachments.map((att) => (
                        <div key={att.id} className="attachment-chip">
                            {att.kind === 'image' && att.previewUrl ? (
                                <img src={att.previewUrl} alt="" className="chip-thumb" />
                            ) : (
                                <span className="chip-icon">{att.kind}</span>
                            )}
                            <span className="chip-name">{att.name}</span>
                            <button
                                type="button"
                                className="chip-remove"
                                onClick={() => removeAttachment(att.id)}
                                aria-label="Remove attachment"
                            >
                                ×
                            </button>
                        </div>
                    ))}
                </div>
            )}
            {error && <div className="input-error">{error}</div>}
            <form className={`input-form${dragOver ? ' drag-over' : ''}`}
                data-testid="message-form"
                onSubmit={(e) => {
                    e.preventDefault()
                    handleSubmit()
                }}
                onDragOver={(e) => {
                    e.preventDefault()
                    setDragOver(true)
                }}
                onDragLeave={() => setDragOver(false)}
                onDrop={(e) => {
                    e.preventDefault()
                    setDragOver(false)
                    addFiles(e.dataTransfer.files)
                }}
            >
                <div
                    className="attach-button"
                    onClick={() => !disabled && fileInputRef.current?.click()}
                    title="Attach files"
                >
                    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 384 512" width="18" height="18" fill={theme}>
                        <path d="M64 0C28.7 0 0 28.7 0 64v288c0 35.3 28.7 64 64 64h176c8.8 0 16-7.2 16-16s-7.2-16-16-16H64c-17.7 0-32-14.3-32-32V64c0-17.7 14.3-32 32-32h192c17.7 0 32 14.3 32 32v272c0 8.8 7.2 16 16 16s16-7.2 16-16V64c0-35.3-28.7-64-64-64H64zM320 336c0-53-43-96-96-96s-96 43-96 96 7.2 16 16 16s16-7.2 16-16c0-35.3 28.7-64 64-64s64 28.7 64 64v128c0 35.3-28.7 64-64 64H160c-35.3 0-64-28.7-64-64V336c0-8.8-7.2-16-16-16s-16 7.2-16 16v128c0 53 43 96 96 96h192c53 0 96-43 96-96V336z"/>
                    </svg>
                </div>
                <input
                    ref={fileInputRef}
                    type="file"
                    multiple
                    accept={ACCEPT}
                    hidden
                    onChange={(e) => {
                        addFiles(e.target.files)
                        e.target.value = ''
                    }}
                />
                <div className="input-container">
                    <div className="input-background" style={{ backgroundColor: theme }}/>
                    <div className="input-element-container">
                        <textarea
                            ref={textareaRef}
                            className="input-element"
                            data-testid="message-input"
                            rows={1}
                            value={text}
                            disabled={disabled || uploading}
                            placeholder={uploading ? 'Uploading…' : placeholder}
                            onChange={(e) => setText(e.target.value)}
                            onKeyDown={(e) => {
                                if (e.key === 'Enter' && !e.shiftKey) {
                                    e.preventDefault()
                                    handleSubmit()
                                }
                            }}
                        />
                    </div>
                </div>

                <div
                    className={`send-container${canSend ? ' active' : ''}`}
                    onClick={canSend ? handleSubmit : undefined}
                >
                    {button}
                </div>
            </form>
        </div>
    )
}
