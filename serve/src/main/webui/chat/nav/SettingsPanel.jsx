/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { useEffect, useState } from 'react'
import { updateUserProfile } from '../api'
import './SettingsPanel.css'

export default function SettingsPanel({ user, onClose, onSaved }) {
  const [displayName, setDisplayName] = useState(user?.display_name ?? '')
  const [avatarUrl, setAvatarUrl] = useState(user?.avatar_url ?? '')
  const [instructions, setInstructions] = useState(user?.personal_instructions ?? '')
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState(null)

  useEffect(() => {
    setDisplayName(user?.display_name ?? '')
    setAvatarUrl(user?.avatar_url ?? '')
    setInstructions(user?.personal_instructions ?? '')
  }, [user])

  async function handleSave(e) {
    e.preventDefault()
    setSaving(true)
    setError(null)
    try {
      await updateUserProfile({
        display_name: displayName,
        avatar_url: avatarUrl,
        personal_instructions: instructions,
      })
      onSaved?.()
      onClose?.()
    } catch (err) {
      setError(err.message || 'Save failed')
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="settings-panel">
      <div className="settings-panel__header">
        <h2>Settings</h2>
        <button type="button" className="settings-panel__close" onClick={onClose}>
          ×
        </button>
      </div>
      <form className="settings-panel__form" onSubmit={handleSave}>
        <label className="settings-panel__field">
          Display name
          <input
            type="text"
            value={displayName}
            onChange={(e) => setDisplayName(e.target.value)}
            maxLength={128}
          />
        </label>
        <label className="settings-panel__field">
          Avatar URL
          <input
            type="url"
            value={avatarUrl}
            onChange={(e) => setAvatarUrl(e.target.value)}
            placeholder="https://..."
          />
        </label>
        <label className="settings-panel__field">
          Personal instructions
          <span className="settings-panel__hint">Used as the system prompt for your chats.</span>
          <textarea
            value={instructions}
            onChange={(e) => setInstructions(e.target.value)}
            rows={5}
            placeholder="You are a helpful assistant..."
          />
        </label>
        {error && <p className="settings-panel__error">{error}</p>}
        <button type="submit" className="settings-panel__save" disabled={saving}>
          {saving ? 'Saving…' : 'Save'}
        </button>
      </form>
    </div>
  )
}
