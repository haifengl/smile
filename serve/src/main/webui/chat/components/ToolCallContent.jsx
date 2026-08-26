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
import React from 'react'
import './ToolCallContent.css'

function formatArgs(argumentsJson) {
  if (!argumentsJson) return '{}'
  try {
    return JSON.stringify(JSON.parse(argumentsJson), null, 2)
  } catch {
    return String(argumentsJson)
  }
}

/**
 * Renders assistant tool_calls as compact cards.
 */
export default function ToolCallContent({ toolCalls }) {
  if (!toolCalls?.length) return null
  return (
    <div className="tool-call-list">
      {toolCalls.map((call) => (
        <div key={call.id || call.function?.name} className="tool-call-card">
          <div className="tool-call-name">
            {call.function?.name || 'function'}
          </div>
          <pre className="tool-call-args">{formatArgs(call.function?.arguments)}</pre>
        </div>
      ))}
    </div>
  )
}
