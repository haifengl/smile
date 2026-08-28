/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
import { useCallback, useState } from 'react'

/**
 * Persists sidebar expanded/collapsed state in {@code localStorage}.
 *
 * @param {string} storageKey unique key per panel
 * @param {boolean} defaultExpanded initial state when no saved preference
 * @returns {[boolean, () => void, (boolean) => void]}
 */
export function useCollapsibleSidebar(storageKey, defaultExpanded = true) {
  const [expanded, setExpandedState] = useState(() => {
    try {
      const saved = localStorage.getItem(storageKey)
      if (saved !== null) {
        return saved === 'true'
      }
    } catch {
      /* private browsing or blocked storage */
    }
    return defaultExpanded
  })

  const setExpanded = useCallback(
    (value) => {
      setExpandedState(value)
      try {
        localStorage.setItem(storageKey, String(value))
      } catch {
        /* ignore */
      }
    },
    [storageKey],
  )

  const toggle = useCallback(() => {
    setExpandedState((prev) => {
      const next = !prev
      try {
        localStorage.setItem(storageKey, String(next))
      } catch {
        /* ignore */
      }
      return next
    })
  }, [storageKey])

  return [expanded, toggle, setExpanded]
}
