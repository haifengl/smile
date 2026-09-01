/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

const JSON_HEADERS = { 'Content-Type': 'application/json', Accept: 'application/json' }

async function parseJson(response) {
  if (!response.ok) {
    const text = await response.text()
    throw new Error(text || response.statusText)
  }
  return response.json()
}

/** @returns {Promise<{logged_in: boolean, user?: object}>} */
export async function fetchAuthMe() {
  const response = await fetch('/api/v1/auth/me', { credentials: 'include' })
  return parseJson(response)
}

/** Session key used to restore the selected infer model after OAuth on {@code /}. */
export const INFER_RESUME_MODEL_KEY = 'smile.infer.resumeModel'

function defaultOAuthReturnTo() {
  const path = window.location.pathname
  if (path === '/chat' || path.startsWith('/chat/')) {
    return '/chat/'
  }
  return '/'
}

/**
 * Redirects the browser to Google OAuth.
 *
 * @param {{ returnTo?: string, inferModelId?: string }} [options]
 */
export function loginWithGoogle(options = {}) {
  const returnTo = options.returnTo ?? defaultOAuthReturnTo()
  if (options.inferModelId) {
    sessionStorage.setItem(INFER_RESUME_MODEL_KEY, options.inferModelId)
  }
  const params = new URLSearchParams({ return_to: returnTo })
  window.location.href = `/api/v1/auth/login/google?${params}`
}

/** @returns {Promise<void>} */
export async function logout() {
  const response = await fetch('/api/v1/auth/logout', {
    method: 'POST',
    credentials: 'include',
  })
  if (!response.ok && response.status !== 204) {
    throw new Error('Logout failed')
  }
}

/** @returns {Promise<object>} */
export async function createConversation() {
  const response = await fetch('/api/v1/conversations', {
    method: 'POST',
    credentials: 'include',
    headers: JSON_HEADERS,
    body: '{}',
  })
  return parseJson(response)
}

/**
 * @param {{ q?: string, pinned?: boolean, pageSize?: number }} [opts]
 * @returns {Promise<object[]>}
 */
export async function listConversations(opts = {}) {
  const params = new URLSearchParams()
  params.set('pageSize', String(opts.pageSize ?? 50))
  if (opts.q) params.set('q', opts.q)
  if (opts.pinned != null) params.set('pinned', String(opts.pinned))
  const response = await fetch(`/api/v1/conversations?${params}`, {
    credentials: 'include',
  })
  return parseJson(response)
}

/** @returns {Promise<object[]>} */
export async function fetchConversationItems(conversationId) {
  const response = await fetch(
    `/api/v1/conversations/${conversationId}/items?pageSize=500`,
    { credentials: 'include' },
  )
  return parseJson(response)
}

/**
 * @param {string} conversationId
 * @param {{ title?: string, pinned?: boolean }} patch
 */
export async function patchConversation(conversationId, patch) {
  const response = await fetch(`/api/v1/conversations/${conversationId}`, {
    method: 'PATCH',
    credentials: 'include',
    headers: JSON_HEADERS,
    body: JSON.stringify(patch),
  })
  return parseJson(response)
}

/** @param {string} conversationId */
export async function deleteConversation(conversationId) {
  const response = await fetch(`/api/v1/conversations/${conversationId}`, {
    method: 'DELETE',
    credentials: 'include',
  })
  if (!response.ok) {
    throw new Error('Delete failed')
  }
}

/**
 * @param {{ display_name?: string, avatar_url?: string, personal_instructions?: string }} body
 */
export async function updateUserProfile(body) {
  const response = await fetch('/api/v1/users/me', {
    method: 'PATCH',
    credentials: 'include',
    headers: JSON_HEADERS,
    body: JSON.stringify(body),
  })
  return parseJson(response)
}
