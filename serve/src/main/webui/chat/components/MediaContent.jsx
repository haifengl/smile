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
import React, { useCallback, useEffect, useState } from 'react'
import { createPortal } from 'react-dom'
import { downloadMedia, isInternalMediaUrl } from '../mediaUtils'
import FileTypeIcon from './FileTypeIcon'
import './MediaContent.css'

function DownloadIcon() {
  return (
    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="18" height="18" aria-hidden="true">
      <path
        fill="currentColor"
        d="M12 3a1 1 0 0 1 1 1v9.59l2.3-2.3a1 1 0 1 1 1.4 1.42l-4 4a1 1 0 0 1-1.4 0l-4-4a1 1 0 1 1 1.4-1.42L11 13.59V4a1 1 0 0 1 1-1zm-7 14a1 1 0 0 1 1 1v1h12v-1a1 1 0 1 1 2 0v2a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1v-2a1 1 0 0 1 1-1z"
      />
    </svg>
  )
}

function CloseIcon() {
  return (
    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="18" height="18" aria-hidden="true">
      <path
        fill="currentColor"
        d="M6.225 4.811a1 1 0 0 0-1.414 1.414L10.586 12l-5.775 5.775a1 1 0 1 0 1.414 1.414L12 13.414l5.775 5.775a1 1 0 0 0 1.414-1.414L13.414 12l5.775-5.775a1 1 0 0 0-1.414-1.414L12 10.586 6.225 4.811z"
      />
    </svg>
  )
}

function ImageLightbox({ url, name, onClose }) {
  useEffect(() => {
    const onKey = (e) => {
      if (e.key === 'Escape') onClose()
    }
    document.addEventListener('keydown', onKey)
    const prev = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => {
      document.removeEventListener('keydown', onKey)
      document.body.style.overflow = prev
    }
  }, [onClose])

  return createPortal(
    <div
      className="media-lightbox"
      role="dialog"
      aria-modal="true"
      aria-label={name || 'Full size image'}
      onClick={onClose}
    >
      <div
        className="media-lightbox-dialog"
        onClick={(e) => e.stopPropagation()}
      >
        <button
          type="button"
          className="media-lightbox-close"
          onClick={onClose}
          aria-label="Close"
        >
          <CloseIcon />
        </button>
        <img
          className="media-lightbox-image"
          src={url}
          alt={name || ''}
        />
      </div>
    </div>,
    document.body
  )
}

function TextLightbox({ name, body, loading, error, onClose }) {
  useEffect(() => {
    const onKey = (e) => {
      if (e.key === 'Escape') onClose()
    }
    document.addEventListener('keydown', onKey)
    const prev = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => {
      document.removeEventListener('keydown', onKey)
      document.body.style.overflow = prev
    }
  }, [onClose])

  return createPortal(
    <div
      className="media-lightbox"
      role="dialog"
      aria-modal="true"
      aria-label={name || 'Attached file'}
      onClick={onClose}
    >
      <div
        className="media-lightbox-dialog media-lightbox-dialog--text"
        onClick={(e) => e.stopPropagation()}
      >
        <button
          type="button"
          className="media-lightbox-close"
          onClick={onClose}
          aria-label="Close"
        >
          <CloseIcon />
        </button>
        <div className="media-lightbox-text-header">{name || 'Attached file'}</div>
        <div className="media-lightbox-text-body">
          {loading ? (
            <div className="media-lightbox-text-status">Loading…</div>
          ) : error ? (
            <div className="media-lightbox-text-status media-lightbox-text-status--error">{error}</div>
          ) : (
            <pre className="media-lightbox-text-pre">{body ?? ''}</pre>
          )}
        </div>
      </div>
    </div>,
    document.body
  )
}

/**
 * @param {object} props
 * @param {string} props.type
 * @param {string} [props.url]
 * @param {string} [props.name]
 * @param {string} [props.mime]
 * @param {string} [props.textContent] Cached text body for text-file attachments.
 * @param {boolean} [props.downloadable] Show hover download control (assistant media only).
 * @param {boolean} [props.expandable] Click image/text for fullscreen (default true).
 */
export default function MediaContent({
  type,
  url,
  name,
  mime,
  textContent,
  downloadable = false,
  expandable = true,
}) {
  const displayUrl = url
  const downloadName = name || 'download'
  const [lightbox, setLightbox] = useState(false)
  const [fetchedText, setFetchedText] = useState(null)
  const [textLoading, setTextLoading] = useState(false)
  const [textError, setTextError] = useState('')
  const closeLightbox = useCallback(() => setLightbox(false), [])

  const openTextLightbox = useCallback(async () => {
    if (!expandable) return
    setLightbox(true)
    setTextError('')
    if (textContent != null) {
      setFetchedText(textContent)
      return
    }
    if (!displayUrl) {
      setTextError('File content unavailable')
      return
    }
    if (fetchedText != null) return
    setTextLoading(true)
    try {
      const response = await fetch(displayUrl)
      if (!response.ok) {
        throw new Error(response.statusText || 'Failed to load file')
      }
      setFetchedText(await response.text())
    } catch (err) {
      setTextError(err.message || 'Failed to load file')
    } finally {
      setTextLoading(false)
    }
  }, [expandable, textContent, displayUrl, fetchedText])

  const downloadBtn = downloadable && displayUrl ? (
    <button
      type="button"
      className="media-download-icon"
      title="Download"
      aria-label={`Download ${downloadName}`}
      onClick={(e) => {
        e.stopPropagation()
        downloadMedia(displayUrl, downloadName)
      }}
    >
      <DownloadIcon />
    </button>
  ) : null

  if (type === 'video') {
    return (
      <div className={`media-block${downloadable ? ' media-block--hoverable' : ''}`}>
        <div className="media-frame">
          <video className="media-video" src={displayUrl} controls playsInline />
          {downloadBtn}
        </div>
      </div>
    )
  }

  if (type === 'audio') {
    return (
      <div className={`media-block${downloadable ? ' media-block--hoverable' : ''}`}>
        <div className="media-frame media-frame--audio">
          <audio className="media-audio" src={displayUrl} controls />
          {downloadBtn}
        </div>
      </div>
    )
  }

  if (type === 'file' || type === 'text') {
    const fileKind = type === 'text' ? 'text' : 'file'
    const canPreview = expandable && (type === 'text' || textContent != null
      || /\.(txt|md|csv|json)$/i.test(downloadName)
      || (mime || '').startsWith('text/')
      || (mime || '').includes('json'))

    return (
      <>
        <div className={`media-block media-file${downloadable ? ' media-block--hoverable' : ''}`}>
          <div
            className={`media-frame media-frame--file${canPreview ? ' media-frame--file-clickable' : ''}`}
            role={canPreview ? 'button' : undefined}
            tabIndex={canPreview ? 0 : undefined}
            title={canPreview ? 'Click to view file' : undefined}
            onClick={() => canPreview && openTextLightbox()}
            onKeyDown={(e) => {
              if (canPreview && (e.key === 'Enter' || e.key === ' ')) {
                e.preventDefault()
                openTextLightbox()
              }
            }}
          >
            <FileTypeIcon kind={fileKind} name={downloadName} mime={mime || ''} className="media-file-icon" />
            <span className="media-filename">{downloadName}</span>
            {downloadBtn}
          </div>
        </div>
        {lightbox && (
          <TextLightbox
            name={downloadName}
            body={textContent != null ? textContent : fetchedText}
            loading={textLoading}
            error={textError}
            onClose={closeLightbox}
          />
        )}
      </>
    )
  }

  return (
    <>
      <div className={`media-block${downloadable ? ' media-block--hoverable' : ''}`}>
        <div className="media-frame">
          <img
            className={`media-image${expandable ? ' media-image--clickable' : ''}`}
            src={displayUrl}
            alt={downloadName}
            loading="lazy"
            role={expandable ? 'button' : undefined}
            tabIndex={expandable ? 0 : undefined}
            title={expandable ? 'Click to view full size' : undefined}
            onClick={() => expandable && setLightbox(true)}
            onKeyDown={(e) => {
              if (expandable && (e.key === 'Enter' || e.key === ' ')) {
                e.preventDefault()
                setLightbox(true)
              }
            }}
          />
          {downloadBtn}
        </div>
      </div>
      {lightbox && (
        <ImageLightbox url={displayUrl} name={downloadName} onClose={closeLightbox} />
      )}
    </>
  )
}

export function MarkdownImage({ src, alt, downloadable = true }) {
  const url = src || ''
  return (
    <span className="markdown-media">
      <MediaContent
        type="image"
        url={url}
        name={alt || 'image'}
        downloadable={downloadable}
      />
    </span>
  )
}

export function MarkdownLink({ href, children, downloadable = true }) {
  const url = href || ''
  const lower = url.toLowerCase()
  if (/\.(mp4|webm|mov)(\?|$)/.test(lower) || lower.startsWith('data:video')) {
    return (
      <MediaContent
        type="video"
        url={url}
        name={String(children)}
        downloadable={downloadable}
      />
    )
  }
  if (/\.(mp3|wav|ogg|m4a)(\?|$)/.test(lower) || lower.startsWith('data:audio')) {
    return (
      <MediaContent
        type="audio"
        url={url}
        name={String(children)}
        downloadable={downloadable}
      />
    )
  }
  if (isInternalMediaUrl(url) || /\.(png|jpe?g|gif|webp)(\?|$)/.test(lower)) {
    return (
      <MediaContent
        type="image"
        url={url}
        name={String(children)}
        downloadable={downloadable}
      />
    )
  }
  return <a href={url} target="_blank" rel="noopener noreferrer">{children}</a>
}
