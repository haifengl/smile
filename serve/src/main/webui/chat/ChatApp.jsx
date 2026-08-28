/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import OpenAI from 'openai'
import Chat from './components/Chat'
import InternetIcon from './assets/internet.svg'
import LlamaIcon from './assets/llama.svg'
import { useAuth } from './auth/AuthProvider'
import { fetchConversationItems } from './api'
import {
  buildApiContent,
  buildChatHistory,
  itemsToUiMessages,
  messageText,
  partsFromAssistantText,
} from './mediaUtils'
import './App.css'

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

const DEFAULT_SYSTEM = 'You are a helpful, respectful and honest assistant.'

/** Max wait for the first stream chunk after the request is accepted. */
const STREAM_FIRST_TOKEN_TIMEOUT_MS = 180_000
/** Max idle gap between successive stream chunks. */
const STREAM_IDLE_TIMEOUT_MS = 90_000

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
      ...(att.textContent != null ? { textContent: att.textContent } : {}),
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
  if (msg.includes('stream timed out') || msg.includes('The operation was aborted')) {
    return 'The server took too long to respond. Please try again.'
  }
  return "Sorry, the service isn't available right now. Please try again later."
}

async function* withStreamIdleTimeout(stream, controller, timeouts) {
  const iterator = stream[Symbol.asyncIterator]()
  let first = true
  while (true) {
    const timeoutMs = first ? timeouts.firstMs : timeouts.idleMs
    let timer
    const timeoutPromise = new Promise((_, reject) => {
      timer = setTimeout(() => {
        const err = new Error(
          first
            ? `stream timed out waiting for first token (${timeoutMs} ms)`
            : `stream timed out waiting for next token (${timeoutMs} ms)`,
        )
        err.name = 'TimeoutError'
        try {
          controller.abort(err)
        } catch {
          controller.abort()
        }
        reject(err)
      }, timeoutMs)
    })
    let result
    try {
      result = await Promise.race([iterator.next(), timeoutPromise])
    } finally {
      clearTimeout(timer)
    }
    if (result.done) {
      return
    }
    first = false
    yield result.value
  }
}

/**
 * Reusable chat panel used by `/chat` and the `/infer` shell.
 */
export default function ChatApp({
  model,
  title,
  embedded = false,
  tools,
  conversationId,
  onActivity,
}) {
  const { user, loggedIn } = useAuth()

  const userPersona = useMemo(
    () => ({
      id: 'user',
      name: user?.display_name || 'You',
      avatar: user?.avatar_url || undefined,
    }),
    [user],
  )

  const welcomeMessage = useMemo(
    () => ({
      parts: [{ type: 'text', text: WELCOME_TEXT }],
      user: bot,
      createdAt: new Date(),
    }),
    [],
  )

  const [messages, setMessages] = useState([welcomeMessage])
  const [showTypingIndicator, setShowTypingIndicator] = useState(false)
  const [sending, setSending] = useState(false)
  const [loadingHistory, setLoadingHistory] = useState(true)
  const sendingRef = useRef(false)
  const sentSystemPromptRef = useRef(false)

  const systemPrompt =
    loggedIn && user?.personal_instructions?.trim()
      ? user.personal_instructions.trim()
      : DEFAULT_SYSTEM

  useEffect(() => {
    sentSystemPromptRef.current = false
    setLoadingHistory(true)
    let cancelled = false

    fetchConversationItems(conversationId)
      .then((items) => {
        if (cancelled) return
        if (items?.length > 0) {
          const loaded = itemsToUiMessages(items, userPersona, bot)
          setMessages(loaded)
        } else {
          setMessages([{ ...welcomeMessage, createdAt: new Date() }])
        }
      })
      .catch((err) => {
        console.error('Failed to load history', err)
        if (!cancelled) {
          setMessages([{ ...welcomeMessage, createdAt: new Date() }])
        }
      })
      .finally(() => {
        if (!cancelled) setLoadingHistory(false)
      })

    return () => {
      cancelled = true
    }
  }, [conversationId, userPersona, welcomeMessage])

  const sendMessage = useCallback(
    async ({ text, attachments }) => {
      const trimmed = text?.trim() ?? ''
      const hasAttachments = attachments?.length > 0
      if (sendingRef.current || loadingHistory || (!trimmed && !hasAttachments)) {
        return
      }
      sendingRef.current = true
      setSending(true)

      const userParts = buildUserParts(trimmed, attachments)
      const userMessage = {
        user: userPersona,
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
        chatMessages.push({ role: 'system', content: systemPrompt })
      }

      const history = buildChatHistory(messages, userPersona.id, bot.id)
      chatMessages.push(...history)
      chatMessages.push({
        role: 'user',
        content: buildApiContent(userMessage),
      })

      try {
        const request = {
          messages: chatMessages,
          stream: true,
          max_tokens: 8192,
          conversation: conversationId,
        }
        if (model) request.model = model
        if (tools?.length) {
          request.tools = tools
          request.tool_choice = 'auto'
        }

        const controller = new AbortController()
        const stream = await client.chat.completions.create(request, {
          signal: controller.signal,
        })

        let receivedToken = false
        const assembledToolCalls = []
        for await (const chunk of withStreamIdleTimeout(stream, controller, {
          firstMs: STREAM_FIRST_TOKEN_TIMEOUT_MS,
          idleMs: STREAM_IDLE_TIMEOUT_MS,
        })) {
          const choice = chunk.choices?.[0]
          if (choice?.finish_reason) break

          const toolDeltas = choice?.delta?.tool_calls
          if (toolDeltas?.length) {
            if (!receivedToken) {
              receivedToken = true
              setShowTypingIndicator(false)
            }
            for (const td of toolDeltas) {
              const idx = td.index ?? 0
              while (assembledToolCalls.length <= idx) {
                assembledToolCalls.push({
                  id: '',
                  type: 'function',
                  function: { name: '', arguments: '' },
                })
              }
              const target = assembledToolCalls[idx]
              if (td.id) target.id = td.id
              if (td.type) target.type = td.type
              if (td.function?.name) {
                target.function.name = (target.function.name || '') + td.function.name
              }
              if (td.function?.arguments) {
                target.function.arguments =
                  (target.function.arguments || '') + td.function.arguments
              }
            }
            setMessages((prev) => {
              const next = prev.slice()
              const last = next[next.length - 1]
              if (last?.streaming && last.user?.id === bot.id) {
                next[next.length - 1] = {
                  ...last,
                  toolCalls: assembledToolCalls.map((c) => ({
                    ...c,
                    function: { ...c.function },
                  })),
                }
              }
              return next
            })
          }

          const delta = choice?.delta?.content
          if (!delta) continue
          if (!receivedToken) {
            receivedToken = true
            setShowTypingIndicator(false)
          }
          setMessages((prev) => {
            const next = prev.slice()
            const last = next[next.length - 1]
            if (last?.streaming && last.user?.id === bot.id) {
              const parts = last.parts?.length
                ? last.parts.map((p, i) =>
                    p.type === 'text' && i === 0
                      ? { ...p, text: (p.text ?? '') + delta }
                      : p,
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
            next[next.length - 1] = {
              ...last,
              parts,
              streaming: false,
              toolCalls: assembledToolCalls.length ? assembledToolCalls : last.toolCalls,
            }
          }
          return next
        })
        onActivity?.()
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
    },
    [conversationId, model, messages, tools, userPersona, systemPrompt, loadingHistory, onActivity],
  )

  const headerTitle = title || model || 'Smile Assistant'
  const className = embedded
    ? 'chat-app chat-app--embedded'
    : 'chat-app chat-app--standalone'

  return (
    <div className={className}>
      <Chat
        userId={userPersona.id}
        messages={messages}
        onSendMessage={sendMessage}
        showTypingIndicator={showTypingIndicator}
        conversationId={conversationId}
        disabled={sending || loadingHistory}
        title={headerTitle}
        placeholder={loadingHistory ? 'Loading conversation…' : 'Type prompt here'}
        theme="#8dd4e8"
      />
    </div>
  )
}
