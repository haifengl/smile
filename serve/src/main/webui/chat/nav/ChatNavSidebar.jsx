/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { useEffect, useState } from 'react'
import { useAuth } from '../auth/AuthProvider'
import { useConversations } from '../hooks/useConversations'
import { deleteConversation, patchConversation } from '../api'
import ConversationRow from './ConversationRow'
import GoogleIcon from './GoogleIcon'
import SettingsPanel from './SettingsPanel'
import './ChatNavSidebar.css'

export default function ChatNavSidebar({
  activeConversationId,
  onNewChat,
  onSelectConversation,
  onConversationMutated,
  sidebarRefreshKey = 0,
}) {
  const { user, loggedIn, loading: authLoading, loginWithGoogle, logout, refresh: refreshAuth } = useAuth()
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
            <button type="button" className="chat-nav__new-chat" onClick={onNewChat}>
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
                    <span className="chat-nav__user-name">{user?.display_name}</span>
                    {user?.email && (
                      <span className="chat-nav__user-email">{user.email}</span>
                    )}
                  </div>
                  <button type="button" className="chat-nav__logout" onClick={logout}>
                    Log out
                  </button>
                </div>
              ) : (
                <>
                  <p className="chat-nav__hint">
                    Sign in to browse history, pin chats, and manage settings.
                  </p>
                  <button type="button" className="chat-nav__login" onClick={loginWithGoogle}>
                    <GoogleIcon className="chat-nav__login-icon" />
                    Login with Google
                  </button>
                </>
              )}
            </div>
          </div>
        </>
      )}
    </nav>
  )
}
