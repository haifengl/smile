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

function iconFor(kind, name = '', mime = '') {
  const lower = name.toLowerCase()
  if (kind === 'image' || mime.startsWith('image/')) return 'image'
  if (kind === 'video' || mime.startsWith('video/')) return 'video'
  if (kind === 'audio' || mime.startsWith('audio/')) return 'audio'
  if (/\.json$/i.test(lower) || mime.includes('json')) return 'json'
  if (/\.csv$/i.test(lower) || mime.includes('csv')) return 'csv'
  if (/\.md$/i.test(lower) || mime.includes('markdown')) return 'markdown'
  if (kind === 'text' || mime.startsWith('text/')) return 'text'
  return 'file'
}

function ImageIcon() {
  return (
    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="18" height="18" aria-hidden="true">
      <path
        fill="currentColor"
        d="M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2zm0 2v10.17l3.59-3.59a1 1 0 0 1 1.41 0L14 15.17l2.59-2.58a1 1 0 0 1 1.41 0L19 15.17V5H5zm14 12v-1.83l-3.59-3.58a1 1 0 0 0-1.41 0L12 15.17l-2.59-2.58a1 1 0 0 0-1.41 0L5 15.17V17h14zM8.5 8a1.5 1.5 0 1 0 0-3 1.5 1.5 0 0 0 0 3z"
      />
    </svg>
  )
}

function VideoIcon() {
  return (
    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="18" height="18" aria-hidden="true">
      <path
        fill="currentColor"
        d="M4 5a2 2 0 0 1 2-2h8a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V5zm10 0v14h2V5h-2zM8 7.5v9l7-4.5-7-4.5z"
      />
    </svg>
  )
}

function AudioIcon() {
  return (
    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="18" height="18" aria-hidden="true">
      <path
        fill="currentColor"
        d="M12 3a5 5 0 0 0-5 5v6a5 5 0 1 0 10 0V8a5 5 0 0 0-5-5zm-3 5a3 3 0 1 1 6 0v6a3 3 0 1 1-6 0V8zm-4 4h2a1 1 0 1 1 0 2H5a1 1 0 1 1 0-2zm12 0h2a1 1 0 1 1 0 2h-2a1 1 0 1 1 0-2z"
      />
    </svg>
  )
}

function TextIcon() {
  return (
    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="18" height="18" aria-hidden="true">
      <path
        fill="currentColor"
        d="M6 4h12a1 1 0 0 1 1 1v2H5V5a1 1 0 0 1 1-1zm-1 5h14v2H5V9zm0 4h10v2H5v-2zm0 4h8v2H5v-2z"
      />
    </svg>
  )
}

function JsonIcon() {
  return (
    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="18" height="18" aria-hidden="true">
      <path
        fill="currentColor"
        d="M8 4a4 4 0 0 0-4 4v1H3v2h1v1a4 4 0 0 0 4 4h1v-2H8a2 2 0 0 1-2-2v-1h2v-2H6V8a2 2 0 0 1 2-2h1V4H8zm8 0v2h1a2 2 0 0 1 2 2v1h-2v2h2v1a2 2 0 0 1-2 2h-1v2h1a4 4 0 0 0 4-4v-1h1v-2h-1V8a4 4 0 0 0-4-4h-1z"
      />
    </svg>
  )
}

function CsvIcon() {
  return (
    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="18" height="18" aria-hidden="true">
      <path
        fill="currentColor"
        d="M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2zm2 4v2h10V7H7zm0 4v2h10v-2H7zm0 4v2h6v-2H7z"
      />
    </svg>
  )
}

function MarkdownIcon() {
  return (
    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="18" height="18" aria-hidden="true">
      <path
        fill="currentColor"
        d="M4 5h16a1 1 0 0 1 1 1v12a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1zm2 4v6h2l2-3 2 3h2V9h-2l-2 3-2-3H6z"
      />
    </svg>
  )
}

function FileIcon() {
  return (
    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="18" height="18" aria-hidden="true">
      <path
        fill="currentColor"
        d="M6 2h7l5 5v13a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2zm7 1.5V8h4.5L13 3.5zM8 11h8v2H8v-2zm0 4h8v2H8v-2z"
      />
    </svg>
  )
}

const ICONS = {
  image: ImageIcon,
  video: VideoIcon,
  audio: AudioIcon,
  text: TextIcon,
  json: JsonIcon,
  csv: CsvIcon,
  markdown: MarkdownIcon,
  file: FileIcon,
}

/**
 * @param {object} props
 * @param {'image'|'video'|'audio'|'text'|'file'} [props.kind]
 * @param {string} [props.name]
 * @param {string} [props.mime]
 * @param {string} [props.className]
 */
export default function FileTypeIcon({ kind = 'file', name = '', mime = '', className = '' }) {
  const key = iconFor(kind, name, mime)
  const Icon = ICONS[key] ?? FileIcon
  return (
    <span className={`file-type-icon${className ? ` ${className}` : ''}`} aria-hidden="true">
      <Icon />
    </span>
  )
}
