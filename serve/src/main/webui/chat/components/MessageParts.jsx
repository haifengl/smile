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
import { messageText } from '../mediaUtils'

export default function MessageParts({ parts, text, streaming }) {
  const resolved = parts?.length
    ? parts
    : text != null
      ? [{ type: 'text', text }]
      : []

  if (!resolved.length) {
    return streaming ? <span className="streaming-cursor">▍</span> : null
  }

  return (
    <div className="message-parts">
      {resolved.map((part, index) => {
        if (part.type === 'text') {
          const body = part.text ?? ''
          if (!body && !(streaming && index === resolved.length - 1)) {
            return null
          }
          return (
            <TextContent key={`text-${index}`}>
              {body}
              {streaming && index === resolved.length - 1 ? '▍' : ''}
            </TextContent>
          )
        }
        const mediaType = part.type === 'file' ? 'file' : part.type
        const url = part.previewUrl || part.url
        if (!url) return null
        return (
          <MediaContent
            key={`media-${index}-${part.contentId || url}`}
            type={mediaType}
            url={url}
            name={part.name}
            mime={part.mime}
          />
        )
      })}
    </div>
  )
}

/** @deprecated use messageText from mediaUtils */
export { messageText }
