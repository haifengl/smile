/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { useCallback, useEffect, useState } from 'react'
import CollapsiblePanel from '../shared/CollapsiblePanel'
import { AuthProvider } from './auth/AuthProvider'
import { createConversation } from './api'
import ChatApp from './ChatApp'
import ChatNavSidebar from './nav/ChatNavSidebar'
import './ChatShell.css'

const NAV_STORAGE_KEY = 'smile.chat.nav.expanded'
const NAV_WIDTH = 300
const NAV_COLLAPSED_WIDTH = 44

/**
 * Chat layout: main transcript + collapsible right nav with auth and history.
 *
 * @param {object} [props]
 * @param {string} [props.model] chat model id
 * @param {string} [props.title] header title
 * @param {boolean} [props.embedded] infer embed mode
 * @param {Array<object>} [props.tools] optional tools
 */
function ChatShellInner({ model, title, embedded = false, tools }) {
  const [conversationId, setConversationId] = useState(null)
  const [chatEpoch, setChatEpoch] = useState(0)
  const [ready, setReady] = useState(false)

  const startNewChat = useCallback(async () => {
    const conv = await createConversation()
    setConversationId(conv.id)
    setChatEpoch((e) => e + 1)
    return conv.id
  }, [])

  useEffect(() => {
    let cancelled = false
    startNewChat()
      .catch((e) => console.error('Failed to create conversation', e))
      .finally(() => {
        if (!cancelled) setReady(true)
      })
    return () => {
      cancelled = true
    }
  }, [startNewChat])

  const handleSelectConversation = useCallback((conv) => {
    setConversationId(conv.id)
    setChatEpoch((e) => e + 1)
  }, [])

  const handleMutated = useCallback(
    (deletedId) => {
      if (deletedId && deletedId === conversationId) {
        startNewChat()
      }
    },
    [conversationId, startNewChat],
  )

  return (
    <div
      className={[
        'chat-shell',
        embedded ? 'chat-shell--embedded' : 'chat-shell--standalone',
      ].join(' ')}
    >
      <div className="chat-shell__main">
        {ready && conversationId && (
          <ChatApp
            key={`${conversationId}-${chatEpoch}`}
            model={model}
            title={title}
            embedded={embedded}
            tools={tools}
            conversationId={conversationId}
            onActivity={handleMutated}
          />
        )}
      </div>
      <CollapsiblePanel
        side="right"
        storageKey={NAV_STORAGE_KEY}
        defaultExpanded={!embedded}
        width={NAV_WIDTH}
        collapsedWidth={NAV_COLLAPSED_WIDTH}
        className="chat-shell__nav"
        ariaLabel="Chat navigation"
      >
        <ChatNavSidebar
          activeConversationId={conversationId}
          onNewChat={startNewChat}
          onSelectConversation={handleSelectConversation}
          onConversationMutated={handleMutated}
        />
      </CollapsiblePanel>
    </div>
  )
}

export default function ChatShell(props) {
  return (
    <AuthProvider>
      <ChatShellInner {...props} />
    </AuthProvider>
  )
}
