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

/** Client-side limits aligned with serve blob upload defaults. */
export const SIZE_LIMITS = {
  image: 20 * 1024 * 1024,
  video: 100 * 1024 * 1024,
  audio: 25 * 1024 * 1024,
  text: 2 * 1024 * 1024,
  default: 20 * 1024 * 1024,
}

const MEDIA_API = /^\/api\/v1\/media\/[0-9a-f-]{36}/i
const MARKDOWN_IMAGE = /!\[[^\]]*]\(([^)]+)\)/g
const MEDIA_LINK = /\(([^)]+\.(?:png|jpe?g|gif|webp|mp4|webm|mov|mp3|wav|ogg|m4a)(?:\?[^)]*)?)\)/gi

/**
 * @param {File} file
 * @returns {'image'|'video'|'audio'|'text'|'file'}
 */
export function classifyFile(file) {
  const mime = file.type || ''
  if (mime.startsWith('image/')) return 'image'
  if (mime.startsWith('video/')) return 'video'
  if (mime.startsWith('audio/')) return 'audio'
  if (mime.startsWith('text/') || /\.(txt|md|csv|json)$/i.test(file.name)) return 'text'
  return 'file'
}

/**
 * @param {'image'|'video'|'audio'|'text'|'file'} kind
 * @returns {number}
 */
export function sizeLimitFor(kind) {
  return SIZE_LIMITS[kind] ?? SIZE_LIMITS.default
}

/**
 * @param {string} text
 * @returns {Array<{url: string, kind: 'image'|'video'|'audio'}>}
 */
export function extractMarkdownMedia(text) {
  if (!text) return []
  const found = []
  const seen = new Set()

  const add = (rawUrl, kind) => {
    const url = rawUrl.trim().replace(/^["']|["']$/g, '')
    if (!url || seen.has(url)) return
    seen.add(url)
    found.push({ url, kind })
  }

  let match
  MARKDOWN_IMAGE.lastIndex = 0
  while ((match = MARKDOWN_IMAGE.exec(text)) !== null) {
    add(match[1], inferMediaKind(match[1]))
  }

  MEDIA_LINK.lastIndex = 0
  while ((match = MEDIA_LINK.exec(text)) !== null) {
    add(match[1], inferMediaKind(match[1]))
  }

  return found
}

/**
 * @param {string} url
 * @returns {'image'|'video'|'audio'}
 */
function inferMediaKind(url) {
  const lower = url.toLowerCase()
  if (lower.startsWith('data:video') || /\.(mp4|webm|mov)(\?|$)/.test(lower)) return 'video'
  if (lower.startsWith('data:audio') || /\.(mp3|wav|ogg|m4a)(\?|$)/.test(lower)) return 'audio'
  return 'image'
}

/**
 * @param {string} conversationId
 * @param {File} file
 * @returns {Promise<object>}
 */
export async function uploadMedia(conversationId, file) {
  const form = new FormData()
  form.append('file', file, file.name)
  form.append('role', 'user')
  const response = await fetch(`/api/v1/conversations/${conversationId}/content`, {
    method: 'POST',
    body: form,
  })
  if (!response.ok) {
    const detail = await response.text()
    throw new Error(detail || response.statusText)
  }
  return response.json()
}

/**
 * @param {string} url
 * @param {string} [filename]
 */
export function downloadMedia(url, filename) {
  const href = url.startsWith('http') || url.startsWith('/')
    ? url
    : url
  const link = document.createElement('a')
  link.href = href.includes('?') ? `${href}&download=true` : `${href}?download=true`
  if (filename) link.download = filename
  link.rel = 'noopener'
  document.body.appendChild(link)
  link.click()
  link.remove()
}

/**
 * @param {string} url
 * @returns {boolean}
 */
export function isInternalMediaUrl(url) {
  return MEDIA_API.test(url)
}

/**
 * Concatenates typed message text parts from a UI message (excludes file attachments).
 *
 * @param {{parts?: Array<{type: string, text?: string, url?: string, contentId?: string}>, text?: string}} message
 * @returns {string}
 */
export function messageText(message) {
  if (message?.parts?.length) {
    return message.parts
      .filter((p) => p.type === 'text' && typeof p.text === 'string' && !p.url && !p.contentId)
      .map((p) => p.text ?? '')
      .join('')
  }
  return message?.text ?? ''
}

/**
 * Message body text parts (typed by the user), not text-file attachments.
 *
 * @param {object} part
 * @returns {boolean}
 */
function isMessageTextPart(part) {
  return part?.type === 'text'
    && typeof part.text === 'string'
    && !part.url
    && !part.contentId
}

/**
 * Attached text files whose body should be inlined into the prompt.
 *
 * @param {object} part
 * @returns {boolean}
 */
function isTextFileAttachment(part) {
  if (!part || isMessageTextPart(part)) return false
  if (part.type === 'text') return true
  if (part.type !== 'file') return false
  const mime = part.mime || ''
  const name = part.name || ''
  return mime.startsWith('text/')
    || mime.includes('json')
    || /\.(txt|md|csv|json)$/i.test(name)
}

/**
 * Formats an attached text file for the model prompt.
 *
 * @param {string} name
 * @param {string} body
 * @returns {string}
 */
function formatAttachedText(name, body) {
  const label = name || 'file'
  return `\n\n--- Attached file: ${label} ---\n${body ?? ''}`
}

/**
 * Builds OpenAI-style content for the completions API from a UI message.
 *
 * Text-file attachments are inlined as additional {@code text} parts so models
 * without file-tooling can still read them. Image/video/audio stay multimodal.
 *
 * @param {{parts?: Array<object>}} message
 * @returns {string|Array<object>}
 */
export function buildApiContent(message) {
  const parts = message.parts ?? []
  const textParts = parts.filter((p) => isMessageTextPart(p) && p.text.trim())
  const textFiles = parts.filter((p) => isTextFileAttachment(p) && p.textContent != null)
  const mediaParts = parts.filter((p) =>
    (p.type === 'image' || p.type === 'video' || p.type === 'audio') && p.url
  )

  if (mediaParts.length === 0 && textFiles.length === 0) {
    return textParts.map((p) => p.text).join('') || ''
  }

  const content = []
  for (const part of textParts) {
    content.push({ type: 'text', text: part.text })
  }
  for (const part of textFiles) {
    content.push({
      type: 'text',
      text: formatAttachedText(part.name, part.textContent),
    })
  }
  for (const part of mediaParts) {
    if (part.type === 'image') {
      content.push({ type: 'image_url', image_url: { url: part.url } })
    } else if (part.type === 'video') {
      content.push({ type: 'video_url', video_url: { url: part.url } })
    } else if (part.type === 'audio') {
      content.push({ type: 'audio_url', audio_url: { url: part.url } })
    }
  }
  return content.length ? content : ''
}

/**
 * @param {Array<object>} messages UI thread messages
 * @param {string} userId
 * @param {string} botId
 * @returns {Array<{role: string, content: string|Array<object>}>}
 */
export function buildChatHistory(messages, userId, botId) {
  const history = []
  for (const msg of messages) {
    if (msg.streaming) continue
    if (msg.role === 'tool') {
      history.push({
        role: 'tool',
        content: messageText(msg) || '',
        tool_call_id: msg.toolCallId,
      })
      continue
    }
    const id = msg.user?.id
    if (id !== userId && id !== botId) continue
    const role = id === userId ? 'user' : 'assistant'
    if (role === 'assistant' && msg.toolCalls?.length) {
      history.push({
        role: 'assistant',
        content: messageText(msg) || null,
        tool_calls: msg.toolCalls,
      })
      continue
    }
    const content = buildApiContent(msg)
    if (content === '' || content === null) continue
    history.push({ role, content })
  }
  return history
}

/**
 * @param {string} text
 * @returns {Array<{type: 'text'|'image'|'video'|'audio', text?: string, url?: string}>}
 */
export function partsFromAssistantText(text) {
  const media = extractMarkdownMedia(text)
  if (!media.length) {
    return [{ type: 'text', text }]
  }
  const parts = [{ type: 'text', text }]
  for (const item of media) {
    parts.push({
      type: item.kind,
      url: item.url,
      name: item.url.split('/').pop()?.split('?')[0] ?? 'media',
    })
  }
  return parts
}
