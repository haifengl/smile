/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Serve is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 */
import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import {
  fetchAuthMe,
  loginWithGoogle,
  loginWithMicrosoft,
  loginWithOidc,
  logout as apiLogout,
} from '../api'

const AuthContext = createContext(null)

function extractAuthErrorMessage(err) {
  if (!err) return 'Authentication service unavailable.'
  try {
    const parsed = JSON.parse(err.message)
    if (parsed?.message) return parsed.message
    if (parsed?.error) return parsed.error
  } catch {
    // Plain text message
  }
  return err.message || 'Authentication service unavailable.'
}

export function AuthProvider({ children }) {
  const existing = useContext(AuthContext)
  if (existing) {
    return children
  }
  return <AuthProviderRoot>{children}</AuthProviderRoot>
}

function AuthProviderRoot({ children }) {
  const [user, setUser] = useState(null)
  const [loggedIn, setLoggedIn] = useState(false)
  const [agentMode, setAgentMode] = useState(false)
  const [googleEnabled, setGoogleEnabled] = useState(true)
  const [microsoftEnabled, setMicrosoftEnabled] = useState(true)
  const [oidcEnabled, setOidcEnabled] = useState(false)
  const [ssoRequired, setSsoRequired] = useState(false)
  const [oidcLabel, setOidcLabel] = useState('SSO')
  const [authError, setAuthError] = useState(null)
  const [loading, setLoading] = useState(true)

  const refresh = useCallback(async () => {
    try {
      const data = await fetchAuthMe()
      setAuthError(null)
      setLoggedIn(Boolean(data.logged_in))
      setUser(data.user ?? null)
      setAgentMode(Boolean(data.agent_mode))
      if (data.google_enabled != null) {
        setGoogleEnabled(Boolean(data.google_enabled))
      }
      if (data.microsoft_enabled != null) {
        setMicrosoftEnabled(Boolean(data.microsoft_enabled))
      }
      const oidc = Boolean(data.oidc_enabled)
      const sso = Boolean(data.sso_required)
      setOidcEnabled(oidc)
      setSsoRequired(sso)
      setOidcLabel(data.oidc_label && data.oidc_label.trim() ? data.oidc_label.trim() : 'SSO')
    } catch (err) {
      setLoggedIn(false)
      setUser(null)
      setAgentMode(false)
      if (err && (err.status === 402 || err.status === 403 || err.status === 503)) {
        setSsoRequired(true)
        setAuthError(extractAuthErrorMessage(err))
      }
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    refresh()
  }, [refresh])

  const logout = useCallback(async () => {
    const result = await apiLogout()
    if (result?.logoutUrl) {
      window.location.href = result.logoutUrl
      return
    }
    await refresh()
  }, [refresh])

  const value = useMemo(
    () => ({
      user,
      loggedIn,
      agentMode,
      googleEnabled,
      microsoftEnabled,
      oidcEnabled,
      ssoRequired,
      oidcLabel,
      authError,
      loading,
      refresh,
      loginWithOidc,
      loginWithGoogle,
      loginWithMicrosoft,
      logout,
    }),
    [
      user,
      loggedIn,
      agentMode,
      googleEnabled,
      microsoftEnabled,
      oidcEnabled,
      ssoRequired,
      oidcLabel,
      authError,
      loading,
      refresh,
      logout,
    ],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

/**
 * @returns {{
 *   user, loggedIn, agentMode, googleEnabled, microsoftEnabled,
 *   oidcEnabled, ssoRequired, oidcLabel, authError, loading,
 *   refresh, loginWithOidc, loginWithGoogle, loginWithMicrosoft, logout
 * }}
 */
export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) {
    throw new Error('useAuth must be used within AuthProvider')
  }
  return ctx
}
