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
  baseURL: `${window.location.origin}/smile/api/v1`,
  apiKey: 'not-needed',
  dangerouslyAllowBrowser: true,
})

const WELCOME = {
  text: 'Hello! How are you today? As a helpful, respectful and honest assistant, I am happy to serve you.',
  user: bot,
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
  const [messages, setMessages] = useState([WELCOME])
  const [showTypingIndicator, setShowTypingIndicator] = useState(false)
  const [conversationId, setConversationId] = useState(null)
  const sendingRef = useRef(false)
  const sentSystemPromptRef = useRef(false)

  // Create a conversation once on mount. In /infer, each model keeps its own
  // ChatApp instance mounted, so switching models does not remount or reset.
  useEffect(() => {
    setMessages([WELCOME])
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
    fetch('/smile/api/v1/conversations', requestOptions)
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

  const sendMessage = useCallback(async (text) => {
    if (sendingRef.current || !text?.trim()) {
      return
    }
    sendingRef.current = true

    const userMessage = {
      user,
      text: text.trim(),
      createdAt: new Date(),
    }

    setMessages((prev) => [
      ...prev,
      userMessage,
      { text: '', user: bot, createdAt: new Date(), streaming: true },
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
    chatMessages.push({
      role: 'user',
      content: userMessage.text,
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
            next[next.length - 1] = { ...last, text: last.text + delta }
          }
          return next
        })
      }

      setMessages((prev) => {
        const next = prev.slice()
        const last = next[next.length - 1]
        if (last?.streaming) {
          next[next.length - 1] = { ...last, streaming: false }
        }
        return next
      })
    } catch (error) {
      console.error('SSE error:', error)
      setMessages((prev) => {
        const next = prev.slice()
        const last = next[next.length - 1]
        if (last?.streaming && last.user?.id === bot.id) {
          next[next.length - 1] = {
            text: "Sorry, the service isn't available right now. Please try again later.",
            user: server,
            createdAt: new Date(),
          }
        } else {
          next.push({
            text: "Sorry, the service isn't available right now. Please try again later.",
            user: server,
            createdAt: new Date(),
          })
        }
        return next
      })
    } finally {
      setShowTypingIndicator(false)
      sendingRef.current = false
    }
  }, [conversationId, model])

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
        title={headerTitle}
        placeholder="Type prompt here"
        theme="#8dd4e8"
      />
    </div>
  )
}
