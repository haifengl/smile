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
import TextContent from './TextContent'
import MediaContent from './MediaContent'
import ToolCallContent from './ToolCallContent'
import ToolResultContent from './ToolResultContent'
import { messageText } from '../mediaUtils'
import { splitThinking } from '../thinkingUtils'
import './MessageParts.css'

function isMessageTextPart(part) {
  return part?.type === 'text'
    && typeof part.text === 'string'
    && !part.url
    && !part.contentId
}

function isFileAttachment(part) {
  if (!part) return false
  if (part.type === 'file') return true
  // Text-file attachments reuse type "text" but carry a media url / content id.
  return part.type === 'text' && !!(part.url || part.contentId)
}

function isInlineMedia(type) {
  return type === 'image' || type === 'video' || type === 'audio'
}

function FileAttachment({ part, downloadable }) {
  const mediaType = part.type === 'text' ? 'text' : 'file'
  const url = part.previewUrl || part.url
  if (!url && part.textContent == null) return null

  return (
    <MediaContent
      type={mediaType}
      url={url}
      name={part.name}
      mime={part.mime}
      textContent={part.textContent}
      downloadable={downloadable}
    />
  )
}

export default function MessageParts({
  parts,
  text,
  downloadable = false,
  streaming = false,
  toolCalls,
  toolCallId,
  role,
}) {
  if (role === 'tool') {
    return (
      <ToolResultContent
        toolCallId={toolCallId}
        content={text ?? messageText({ parts, text })}
      />
    )
  }

  const resolved = parts?.length
    ? parts
    : text != null
      ? [{ type: 'text', text }]
      : []

  if (!resolved.length && !toolCalls?.length) {
    return null
  }

  const textParts = resolved.filter((p) => isMessageTextPart(p))
  const inlineMedia = resolved.filter((p) => isInlineMedia(p.type))
  const fileAttachments = resolved.filter((p) => isFileAttachment(p))

  const combinedText = textParts.map((p) => p.text ?? '').join('')
  const { thinking, answer } = splitThinking(combinedText)
  const thinkingBody = thinking.replace(/^\n+/, '').replace(/\n+$/, '')

  if (!thinkingBody && !answer && inlineMedia.length === 0
      && fileAttachments.length === 0 && !toolCalls?.length) {
    return null
  }

  return (
    <div className="message-parts">
      {thinkingBody ? (
        <blockquote className="thinking-block">
          {thinkingBody}
        </blockquote>
      ) : null}

      {answer ? (
        <TextContent downloadable={downloadable} streaming={streaming}>
          {answer}
        </TextContent>
      ) : null}

      {toolCalls?.length ? <ToolCallContent toolCalls={toolCalls} /> : null}

      {inlineMedia.map((part, index) => {
        const url = part.previewUrl || part.url
        if (!url) return null
        return (
          <MediaContent
            key={`media-${index}-${part.contentId || url}`}
            type={part.type}
            url={url}
            name={part.name}
            mime={part.mime}
            downloadable={downloadable}
          />
        )
      })}

      {fileAttachments.length > 0 ? (
        <div className="message-attachments">
          {fileAttachments.map((part, index) => (
            <FileAttachment
              key={`file-${index}-${part.contentId || part.name}`}
              part={part}
              downloadable={downloadable}
            />
          ))}
        </div>
      ) : null}
    </div>
  )
}

/** @deprecated use messageText from mediaUtils */
export { messageText }
