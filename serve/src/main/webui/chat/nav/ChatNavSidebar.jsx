/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Serve is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 */
import { useEffect, useState } from 'react'
import { useAuth } from '../auth/AuthProvider'
import { useConversations } from '../hooks/useConversations'
import { deleteConversation, patchConversation } from '../api'
import ConversationRow from './ConversationRow'
import GoogleIcon from './GoogleIcon'
import MicrosoftIcon from './MicrosoftIcon'
import SsoIcon from './SsoIcon'
import SettingsPanel from './SettingsPanel'
import './ChatNavSidebar.css'

export default function ChatNavSidebar({
  activeConversationId,
  onNewChat,
  onSelectConversation,
  onConversationMutated,
  sidebarRefreshKey = 0,
  embedded = false,
  model,
}) {
  const {
    user,
    loggedIn,
    agentMode,
    oidcEnabled,
    ssoRequired,
    oidcLabel,
    authError,
    loading: authLoading,
    loginWithOidc,
    loginWithGoogle,
    loginWithMicrosoft,
    logout,
    refresh: refreshAuth,
  } = useAuth()
  const { pinned, recent, loading, search, setSearch, refresh } = useConversations(loggedIn)
  const [showSettings, setShowSettings] = useState(false)

  useEffect(() => {
    if (!loggedIn || sidebarRefreshKey === 0) return
    refresh(search)
  }, [sidebarRefreshKey, loggedIn, refresh, search])

  async function handlePin(conv) {
    await patchConversation(conv.id, { pinned: !conv.pinned })
    refresh(search)
    onConversationMutated?.()
  }

  async function handleRename(conv) {
    const next = window.prompt('Rename conversation', conv.title || 'New chat')
    if (next == null || !next.trim()) return
    await patchConversation(conv.id, { title: next.trim() })
    refresh(search)
    onConversationMutated?.()
  }

  async function handleDelete(conv) {
    if (!window.confirm(`Delete "${conv.title || 'New chat'}"?`)) return
    await deleteConversation(conv.id)
    refresh(search)
    onConversationMutated?.(conv.id)
  }

  function handleOidcLogin() {
    if (embedded) {
      loginWithOidc({ returnTo: '/', inferModelId: model })
      return
    }
    loginWithOidc({ returnTo: '/chat/' })
  }

  function handleGoogleLogin() {
    if (embedded) {
      loginWithGoogle({ returnTo: '/', inferModelId: model })
      return
    }
    loginWithGoogle({ returnTo: '/chat/' })
  }

  function handleMicrosoftLogin() {
    if (embedded) {
      loginWithMicrosoft({ returnTo: '/', inferModelId: model })
      return
    }
    loginWithMicrosoft({ returnTo: '/chat/' })
  }

  const ssoMode = Boolean(oidcEnabled || ssoRequired)
  const isLocalMe = !ssoMode && loggedIn && user?.external_id === 'me'
  const newChatDisabled = ssoMode && !loggedIn

  return (
    <nav className="chat-nav" aria-label="Chat navigation">
      {showSettings && loggedIn ? (
        <SettingsPanel
          user={user}
          onClose={() => setShowSettings(false)}
          onSaved={() => {
            refreshAuth()
            onConversationMutated?.()
          }}
        />
      ) : (
        <>
          <div className="chat-nav__section">
            <button
              type="button"
              className="chat-nav__new-chat"
              onClick={onNewChat}
              disabled={newChatDisabled}
            >
              + New Chat
            </button>
          </div>

          <div
            className={`chat-nav__section ${loggedIn ? '' : 'chat-nav__section--disabled'}`}
            aria-disabled={!loggedIn}
          >
            <h3 className="chat-nav__heading">Pinned</h3>
            {loggedIn && pinned.length === 0 && (
              <p className="chat-nav__placeholder">No pinned chats</p>
            )}
            {loggedIn &&
              pinned.map((c) => (
                <ConversationRow
                  key={c.id}
                  conversation={c}
                  active={c.id === activeConversationId}
                  onSelect={onSelectConversation}
                  onPin={handlePin}
                  onRename={handleRename}
                  onDelete={handleDelete}
                />
              ))}
          </div>

          <div
            className={`chat-nav__section chat-nav__section--grow ${loggedIn ? '' : 'chat-nav__section--disabled'}`}
            aria-disabled={!loggedIn}
          >
            <h3 className="chat-nav__heading">Recent</h3>
            {loggedIn && (
              <input
                type="search"
                className="chat-nav__search"
                placeholder="Search conversations…"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                aria-label="Search conversations"
              />
            )}
            {loggedIn && loading && <p className="chat-nav__placeholder">Loading…</p>}
            {loggedIn && !loading && recent.length === 0 && (
              <p className="chat-nav__placeholder">No recent chats</p>
            )}
            {loggedIn &&
              recent.map((c) => (
                <ConversationRow
                  key={c.id}
                  conversation={c}
                  active={c.id === activeConversationId}
                  onSelect={onSelectConversation}
                  onPin={handlePin}
                  onRename={handleRename}
                  onDelete={handleDelete}
                />
              ))}
          </div>

          <div className="chat-nav__footer">
            <div className="chat-nav__account">
              {authLoading ? (
                <p className="chat-nav__hint">Loading…</p>
              ) : loggedIn ? (
                <>
                  <div className="chat-nav__user">
                    <button
                      type="button"
                      className="chat-nav__avatar-btn"
                      onClick={() => setShowSettings(true)}
                      aria-label="Account settings"
                      title="Account settings"
                    >
                      {user?.avatar_url ? (
                        <img src={user.avatar_url} alt="" className="chat-nav__avatar" />
                      ) : (
                        <span className="chat-nav__avatar chat-nav__avatar--placeholder">
                          {(user?.display_name || '?')[0]}
                        </span>
                      )}
                    </button>
                    <div className="chat-nav__user-info">
                      <span className="chat-nav__user-name">
                        {user?.display_name}
                        {agentMode && (
                          <span className="chat-nav__agent-badge" title="Kirin Personal Assistant Active">
                            Kirin
                          </span>
                        )}
                      </span>
                      {user?.email && (
                        <span className="chat-nav__user-email">{user.email}</span>
                      )}
                    </div>
                    <button type="button" className="chat-nav__logout" onClick={logout}>
                      Log out
                    </button>
                  </div>
                  {isLocalMe && (
                    <div className="chat-nav__login-group chat-nav__login-group--compact">
                      <button type="button" className="chat-nav__login" onClick={handleGoogleLogin}>
                        <GoogleIcon className="chat-nav__login-icon" />
                        Login with Google
                      </button>
                      <button type="button" className="chat-nav__login" onClick={handleMicrosoftLogin}>
                        <MicrosoftIcon className="chat-nav__login-icon" />
                        Login with Microsoft
                      </button>
                    </div>
                  )}
                </>
              ) : ssoMode ? (
                <>
                  {authError ? (
                    <p className="chat-nav__hint">{authError}</p>
                  ) : (
                    <>
                      <p className="chat-nav__hint">
                        Sign in with your organization account to start chatting.
                      </p>
                      <div className="chat-nav__login-group">
                        <button type="button" className="chat-nav__login" onClick={handleOidcLogin}>
                          <SsoIcon className="chat-nav__login-icon" />
                          Sign in with {oidcLabel || 'SSO'}
                        </button>
                      </div>
                    </>
                  )}
                </>
              ) : (
                <>
                  <p className="chat-nav__hint">
                    Sign in to browse history, pin chats, and activate Kirin personal assistant.
                  </p>
                  <div className="chat-nav__login-group">
                    <button type="button" className="chat-nav__login" onClick={handleGoogleLogin}>
                      <GoogleIcon className="chat-nav__login-icon" />
                      Login with Google
                    </button>
                    <button type="button" className="chat-nav__login" onClick={handleMicrosoftLogin}>
                      <MicrosoftIcon className="chat-nav__login-icon" />
                      Login with Microsoft
                    </button>
                  </div>
                </>
              )}
            </div>
          </div>
        </>
      )}
    </nav>
  )
}
