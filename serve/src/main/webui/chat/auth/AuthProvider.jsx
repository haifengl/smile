/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Serve is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 */
import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import { fetchAuthMe, loginWithGoogle, loginWithMicrosoft, logout as apiLogout } from '../api'

const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null)
  const [loggedIn, setLoggedIn] = useState(false)
  const [agentMode, setAgentMode] = useState(false)
  const [googleEnabled, setGoogleEnabled] = useState(true)
  const [microsoftEnabled, setMicrosoftEnabled] = useState(true)
  const [loading, setLoading] = useState(true)

  const refresh = useCallback(async () => {
    try {
      const data = await fetchAuthMe()
      setLoggedIn(Boolean(data.logged_in))
      setUser(data.user ?? null)
      setAgentMode(Boolean(data.agent_mode))
      if (data.google_enabled != null) {
        setGoogleEnabled(Boolean(data.google_enabled))
      }
      if (data.microsoft_enabled != null) {
        setMicrosoftEnabled(Boolean(data.microsoft_enabled))
      }
    } catch {
      setLoggedIn(false)
      setUser(null)
      setAgentMode(false)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    refresh()
  }, [refresh])

  const logout = useCallback(async () => {
    await apiLogout()
    await refresh()
  }, [refresh])

  const value = useMemo(
    () => ({
      user,
      loggedIn,
      agentMode,
      googleEnabled,
      microsoftEnabled,
      loading,
      refresh,
      loginWithGoogle,
      loginWithMicrosoft,
      logout,
    }),
    [user, loggedIn, agentMode, googleEnabled, microsoftEnabled, loading, refresh, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

/** @returns {{ user, loggedIn, agentMode, googleEnabled, microsoftEnabled, loading, refresh, loginWithGoogle, loginWithMicrosoft, logout }} */
export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) {
    throw new Error('useAuth must be used within AuthProvider')
  }
  return ctx
}
