/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { useCallback, useEffect, useState } from 'react'
import { listConversations } from '../api'

function hasMessageHistory(conversation) {
  return (conversation.message_count ?? 0) > 0
}

/**
 * Loads and refreshes the user's conversation list (auth required).
 *
 * @param {boolean} enabled
 */
export function useConversations(enabled) {
  const [conversations, setConversations] = useState([])
  const [loading, setLoading] = useState(false)
  const [search, setSearch] = useState('')

  const refresh = useCallback(async (query) => {
    if (!enabled) {
      setConversations([])
      return
    }
    setLoading(true)
    try {
      const q = query ?? search
      const pinned = await listConversations({ pinned: true, q: q || undefined })
      const recent = await listConversations({ pinned: false, q: q || undefined })
      const seen = new Set()
      const merged = []
      for (const c of [...pinned, ...recent]) {
        if (!seen.has(c.id) && hasMessageHistory(c)) {
          seen.add(c.id)
          merged.push(c)
        }
      }
      setConversations(merged)
    } catch (e) {
      console.error('Failed to load conversations', e)
    } finally {
      setLoading(false)
    }
  }, [enabled, search])

  useEffect(() => {
    refresh('')
  }, [enabled])

  useEffect(() => {
    if (!enabled) return undefined
    const timer = setTimeout(() => refresh(search), 300)
    return () => clearTimeout(timer)
  }, [search, enabled, refresh])

  const pinned = conversations.filter((c) => c.pinned)
  const recent = conversations.filter((c) => !c.pinned)

  return {
    conversations,
    pinned,
    recent,
    loading,
    search,
    setSearch,
    refresh,
  }
}
