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
import { downloadMedia, isInternalMediaUrl } from '../mediaUtils'
import './MediaContent.css'

export default function MediaContent({
  type,
  url,
  name,
  mime,
}) {
  const displayUrl = url?.startsWith('/') ? url : url
  const downloadName = name || 'download'

  const controls = (
    <div className="media-download">
      <button
        type="button"
        className="media-download-btn"
        onClick={() => downloadMedia(displayUrl, downloadName)}
      >
        Download
      </button>
    </div>
  )

  if (type === 'video') {
    return (
      <div className="media-block">
        <video className="media-video" src={displayUrl} controls playsInline />
        {controls}
      </div>
    )
  }

  if (type === 'audio') {
    return (
      <div className="media-block">
        <audio className="media-audio" src={displayUrl} controls />
        {controls}
      </div>
    )
  }

  if (type === 'file') {
    return (
      <div className="media-block media-file">
        <span className="media-filename">{downloadName}</span>
        {controls}
      </div>
    )
  }

  return (
    <div className="media-block">
      <img
        className="media-image"
        src={displayUrl}
        alt={downloadName}
        loading="lazy"
      />
      {controls}
    </div>
  )
}

export function MarkdownImage({ src, alt }) {
  const url = src || ''
  return (
    <span className="markdown-media">
      <MediaContent type="image" url={url} name={alt || 'image'} />
    </span>
  )
}

export function MarkdownLink({ href, children }) {
  const url = href || ''
  const lower = url.toLowerCase()
  if (/\.(mp4|webm|mov)(\?|$)/.test(lower) || lower.startsWith('data:video')) {
    return <MediaContent type="video" url={url} name={String(children)} />
  }
  if (/\.(mp3|wav|ogg|m4a)(\?|$)/.test(lower) || lower.startsWith('data:audio')) {
    return <MediaContent type="audio" url={url} name={String(children)} />
  }
  if (isInternalMediaUrl(url) || /\.(png|jpe?g|gif|webp)(\?|$)/.test(lower)) {
    return <MediaContent type="image" url={url} name={String(children)} />
  }
  return <a href={url} target="_blank" rel="noopener noreferrer">{children}</a>
}
