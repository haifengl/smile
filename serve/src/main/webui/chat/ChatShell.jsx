/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Serve is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 */
import { useCallback, useEffect, useRef, useState } from 'react'
import CollapsiblePanel from '../shared/CollapsiblePanel'
import { AuthProvider, useAuth } from './auth/AuthProvider'
import SsoSignInGate from './auth/SsoSignInGate'
import { createConversation } from './api'
import ChatApp from './ChatApp'
import ChatNavSidebar from './nav/ChatNavSidebar'
import './ChatShell.css'

const NAV_STORAGE_KEY = 'smile.chat.nav.expanded'
const NAV_WIDTH = 300

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
  const {
    loggedIn,
    loading: authLoading,
    ssoRequired,
    oidcLabel,
    authError,
    loginWithOidc,
  } = useAuth()
  const wasLoggedInRef = useRef(loggedIn)
  const [conversationId, setConversationId] = useState(null)
  const [chatEpoch, setChatEpoch] = useState(0)
  const [sidebarRefreshKey, setSidebarRefreshKey] = useState(0)
  const [ready, setReady] = useState(false)

  const blockedBySso = Boolean(!authLoading && (authError || (ssoRequired && !loggedIn)))

  const startNewChat = useCallback(async () => {
    const conv = await createConversation()
    setConversationId(conv.id)
    setChatEpoch((e) => e + 1)
    return conv.id
  }, [])

  useEffect(() => {
    if (authLoading) return
    if (authError || (ssoRequired && !loggedIn)) {
      setConversationId(null)
      setReady(false)
      return
    }
    let cancelled = false
    startNewChat()
      .catch((e) => console.error('Failed to create conversation', e))
      .finally(() => {
        if (!cancelled) setReady(true)
      })
    return () => {
      cancelled = true
    }
  }, [authLoading, authError, ssoRequired, loggedIn, startNewChat])

  useEffect(() => {
    if (wasLoggedInRef.current && !loggedIn) {
      if (ssoRequired) {
        setConversationId(null)
        setReady(false)
      } else {
        startNewChat().catch((e) => console.error('Failed to start guest chat after logout', e))
      }
    }
    wasLoggedInRef.current = loggedIn
  }, [loggedIn, ssoRequired, startNewChat])

  const handleSelectConversation = useCallback((conv) => {
    setConversationId(conv.id)
    setChatEpoch((e) => e + 1)
  }, [])

  const handleMutated = useCallback(
    (deletedId) => {
      setSidebarRefreshKey((key) => key + 1)
      if (deletedId && deletedId === conversationId && (!ssoRequired || loggedIn)) {
        startNewChat()
      }
    },
    [conversationId, ssoRequired, loggedIn, startNewChat],
  )

  const handleSsoSignIn = useCallback(() => {
    if (embedded) {
      loginWithOidc({ returnTo: '/', inferModelId: model })
      return
    }
    loginWithOidc({ returnTo: '/chat/' })
  }, [embedded, model, loginWithOidc])

  return (
    <div
      className={[
        'chat-shell',
        embedded ? 'chat-shell--embedded' : 'chat-shell--standalone',
      ].join(' ')}
    >
      <div className="chat-shell__main">
        {blockedBySso ? (
          <SsoSignInGate
            label={oidcLabel || 'SSO'}
            error={authError}
            onSignIn={handleSsoSignIn}
          />
        ) : (
          ready &&
          conversationId && (
            <ChatApp
              key={`${conversationId}-${chatEpoch}`}
              model={model}
              title={title}
              embedded={embedded}
              tools={tools}
              conversationId={conversationId}
              onActivity={handleMutated}
            />
          )
        )}
      </div>
      <CollapsiblePanel
        side="right"
        storageKey={NAV_STORAGE_KEY}
        defaultExpanded={!embedded}
        width={NAV_WIDTH}
        className="chat-shell__nav"
        ariaLabel="Chat navigation"
      >
        <ChatNavSidebar
          activeConversationId={conversationId}
          onNewChat={startNewChat}
          onSelectConversation={handleSelectConversation}
          onConversationMutated={handleMutated}
          sidebarRefreshKey={sidebarRefreshKey}
          embedded={embedded}
          model={model}
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
