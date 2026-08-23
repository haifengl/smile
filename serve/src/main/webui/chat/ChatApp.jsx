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
import { useCallback, useEffect, useRef, useState } from 'react'
import OpenAI from 'openai'
import Chat from './components/Chat'
import InternetIcon from './assets/internet.svg'
import LlamaIcon from './assets/llama.svg'
import {
  buildApiContent,
  buildChatHistory,
  messageText,
  partsFromAssistantText,
} from './mediaUtils'
import './App.css'

const user = {
  id: 'user',
  name: 'You',
}

const bot = {
  id: 'smile',
  name: 'Kirin',
  avatar: LlamaIcon,
}

const server = {
  id: 'server',
  name: 'Server',
  avatar: InternetIcon,
}

const client = new OpenAI({
  baseURL: `${window.location.origin}/api/v1`,
  apiKey: 'not-needed',
  dangerouslyAllowBrowser: true,
})

const WELCOME_TEXT =
  'Hello! How are you today? As a helpful, respectful and honest assistant, I am happy to serve you.'

const WELCOME = {
  parts: [{ type: 'text', text: WELCOME_TEXT }],
  user: bot,
}

function buildUserParts(text, attachments) {
  const parts = []
  if (text?.trim()) {
    parts.push({ type: 'text', text: text.trim() })
  }
  for (const att of attachments ?? []) {
    parts.push({
      type: att.type,
      contentId: att.contentId,
      url: att.url,
      mime: att.mime,
      name: att.name,
      size: att.size,
    })
  }
  return parts
}

function errorMessage(error) {
  const msg = error?.error?.message || error?.message || String(error)
  if (msg.includes('Audio input is not supported')) {
    return 'Audio attachments are not supported by this model yet.'
  }
  if (msg.includes('Multimodal content requires')) {
    return 'Image and video require a vision-capable model (Qwen VL).'
  }
  return "Sorry, the service isn't available right now. Please try again later."
}

/**
 * Reusable chat panel used by `/chat` and the `/infer` shell.
 *
 * @param {object} props
 * @param {string} [props.model] Public chat model id for completions (omit to use server default).
 * @param {string} [props.title] Header title; defaults to model id or "Smile Assistant".
 * @param {boolean} [props.embedded] When true, fills the parent pane (infer); otherwise standalone layout.
 */
export default function ChatApp({ model, title, embedded = false }) {
  const [messages, setMessages] = useState([{ ...WELCOME, createdAt: new Date() }])
  const [showTypingIndicator, setShowTypingIndicator] = useState(false)
  const [conversationId, setConversationId] = useState(null)
  const [sending, setSending] = useState(false)
  const sendingRef = useRef(false)
  const sentSystemPromptRef = useRef(false)

  useEffect(() => {
    setMessages([{ ...WELCOME, createdAt: new Date() }])
    setShowTypingIndicator(false)
    setConversationId(null)
    sendingRef.current = false
    sentSystemPromptRef.current = false

    const requestOptions = {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({}),
    }

    let cancelled = false
    fetch('/api/v1/conversations', requestOptions)
      .then((response) => {
        if (!response.ok) {
          throw new Error(response.statusText)
        }
        return response.json()
      })
      .then((conversation) => {
        if (!cancelled) {
          setConversationId(conversation.id)
        }
      })
      .catch((error) => {
        console.error(error)
      })

    return () => {
      cancelled = true
    }
  }, [])

  const sendMessage = useCallback(async ({ text, attachments }) => {
    const trimmed = text?.trim() ?? ''
    const hasAttachments = attachments?.length > 0
    if (sendingRef.current || (!trimmed && !hasAttachments)) {
      return
    }
    sendingRef.current = true
    setSending(true)

    const userParts = buildUserParts(trimmed, attachments)
    const userMessage = {
      user,
      parts: userParts,
      createdAt: new Date(),
    }

    setMessages((prev) => [
      ...prev,
      userMessage,
      {
        parts: [{ type: 'text', text: '' }],
        user: bot,
        createdAt: new Date(),
        streaming: true,
      },
    ])
    setShowTypingIndicator(true)

    const chatMessages = []
    if (!sentSystemPromptRef.current) {
      sentSystemPromptRef.current = true
      chatMessages.push({
        role: 'system',
        content: 'You are a helpful, respectful and honest assistant.',
      })
    }

    const history = buildChatHistory(messages, user.id, bot.id)
    chatMessages.push(...history)
    chatMessages.push({
      role: 'user',
      content: buildApiContent(userMessage),
    })

    try {
      const request = {
        messages: chatMessages,
        stream: true,
        max_tokens: 512,
        conversation: conversationId,
      }
      if (model) {
        request.model = model
      }

      const stream = await client.chat.completions.create(request)

      for await (const chunk of stream) {
        const choice = chunk.choices?.[0]
        if (choice?.finish_reason) {
          break
        }
        const delta = choice?.delta?.content
        if (!delta) {
          continue
        }
        setMessages((prev) => {
          const next = prev.slice()
          const last = next[next.length - 1]
          if (last?.streaming && last.user?.id === bot.id) {
            const parts = last.parts?.length
              ? last.parts.map((p, i) =>
                  p.type === 'text' && i === 0
                    ? { ...p, text: (p.text ?? '') + delta }
                    : p
                )
              : [{ type: 'text', text: delta }]
            next[next.length - 1] = { ...last, parts }
          }
          return next
        })
      }

      setMessages((prev) => {
        const next = prev.slice()
        const last = next[next.length - 1]
        if (last?.streaming) {
          const finalText = messageText(last)
          const parts = partsFromAssistantText(finalText)
          next[next.length - 1] = { ...last, parts, streaming: false }
        }
        return next
      })
    } catch (error) {
      console.error('SSE error:', error)
      const display = errorMessage(error)
      setMessages((prev) => {
        const next = prev.slice()
        const last = next[next.length - 1]
        const errorMsg = {
          parts: [{ type: 'text', text: display }],
          user: server,
          createdAt: new Date(),
        }
        if (last?.streaming && last.user?.id === bot.id) {
          next[next.length - 1] = errorMsg
        } else {
          next.push(errorMsg)
        }
        return next
      })
    } finally {
      setShowTypingIndicator(false)
      sendingRef.current = false
      setSending(false)
    }
  }, [conversationId, model, messages])

  const headerTitle = title || model || 'Smile Assistant'
  const className = embedded
    ? 'chat-app chat-app--embedded'
    : 'chat-app chat-app--standalone'

  return (
    <div className={className}>
      <Chat
        userId={user.id}
        messages={messages}
        onSendMessage={sendMessage}
        showTypingIndicator={showTypingIndicator}
        conversationId={conversationId}
        disabled={sending}
        title={headerTitle}
        placeholder="Type prompt here"
        theme="#8dd4e8"
      />
    </div>
  )
}
