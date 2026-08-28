/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import { fetchAuthMe, loginWithGoogle, logout as apiLogout } from '../api'

const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null)
  const [loggedIn, setLoggedIn] = useState(false)
  const [loading, setLoading] = useState(true)

  const refresh = useCallback(async () => {
    try {
      const data = await fetchAuthMe()
      setLoggedIn(Boolean(data.logged_in))
      setUser(data.user ?? null)
    } catch {
      setLoggedIn(false)
      setUser(null)
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
      loading,
      refresh,
      loginWithGoogle,
      logout,
    }),
    [user, loggedIn, loading, refresh, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

/** @returns {{ user, loggedIn, loading, refresh, loginWithGoogle, logout }} */
export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) {
    throw new Error('useAuth must be used within AuthProvider')
  }
  return ctx
}
