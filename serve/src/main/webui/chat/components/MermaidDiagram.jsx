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
import React, { useEffect, useId, useState } from 'react'
import mermaid from 'mermaid'
import './MermaidDiagram.css'

let mermaidReady = false

function ensureMermaid() {
  if (mermaidReady) return
  mermaid.initialize({
    startOnLoad: false,
    securityLevel: 'strict',
    theme: 'neutral',
    fontFamily: 'ui-sans-serif, system-ui, sans-serif',
  })
  mermaidReady = true
}

/**
 * Renders a {@code ```mermaid} fenced code block as an SVG diagram.
 *
 * @param {{ chart: string }} props
 */
export default function MermaidDiagram({ chart }) {
  const reactId = useId().replace(/:/g, '')
  const [svg, setSvg] = useState('')
  const [error, setError] = useState('')

  useEffect(() => {
    let cancelled = false
    const source = (chart ?? '').trim()
    if (!source) {
      setSvg('')
      setError('')
      return undefined
    }

    ensureMermaid()
    const renderId = `mermaid-${reactId}-${Math.random().toString(36).slice(2, 8)}`

    mermaid
      .render(renderId, source)
      .then(({ svg: rendered }) => {
        if (!cancelled) {
          setSvg(rendered)
          setError('')
        }
      })
      .catch((err) => {
        if (!cancelled) {
          setSvg('')
          setError(err?.message || 'Failed to render Mermaid diagram')
        }
      })

    return () => {
      cancelled = true
    }
  }, [chart, reactId])

  if (error) {
    return (
      <div className="mermaid-block mermaid-block--error" data-mermaid="">
        <div className="mermaid-error" role="alert">
          Mermaid diagram error: {error}
        </div>
        <pre className="mermaid-fallback">{chart}</pre>
      </div>
    )
  }

  if (!svg) {
    return (
      <div className="mermaid-block mermaid-block--loading" data-mermaid="">
        <div className="mermaid-loading">Rendering diagram…</div>
      </div>
    )
  }

  return (
    <div
      className="mermaid-block"
      data-mermaid=""
      dangerouslySetInnerHTML={{ __html: svg }}
    />
  )
}
