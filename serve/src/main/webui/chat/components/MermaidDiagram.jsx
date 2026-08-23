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
    // Avoid injecting "Syntax error in text" SVGs into the document.
    suppressErrorRendering: true,
  })
  mermaidReady = true
}

/**
 * @param {string} source
 * @returns {Promise<boolean>}
 */
async function canParse(source) {
  try {
    // mermaid.parse may return a boolean or throw, depending on version.
    const result = await mermaid.parse(source, { suppressErrors: true })
    return result !== false
  } catch {
    return false
  }
}

/**
 * Renders a {@code ```mermaid} fenced code block as an SVG diagram.
 * While the parent message is still streaming, incomplete fences are shown
 * as source until Mermaid can parse them (closing fence / valid chart).
 *
 * @param {{ chart: string, streaming?: boolean }} props
 */
export default function MermaidDiagram({ chart, streaming = false }) {
  const reactId = useId().replace(/:/g, '')
  const [svg, setSvg] = useState('')
  const [error, setError] = useState('')
  const [pending, setPending] = useState(false)

  useEffect(() => {
    let cancelled = false
    const source = (chart ?? '').trim()
    if (!source) {
      setSvg('')
      setError('')
      setPending(false)
      return undefined
    }

    ensureMermaid()

    ;(async () => {
      if (streaming) {
        const ok = await canParse(source)
        if (cancelled) return
        if (!ok) {
          setSvg('')
          setError('')
          setPending(true)
          return
        }
      }

      const renderId = `mermaid-${reactId}-${Math.random().toString(36).slice(2, 8)}`
      try {
        const { svg: rendered } = await mermaid.render(renderId, source)
        if (!cancelled) {
          setSvg(rendered)
          setError('')
          setPending(false)
        }
      } catch (err) {
        if (!cancelled) {
          setSvg('')
          setPending(false)
          // Incomplete stream: keep waiting; finished message: show error.
          if (streaming) {
            setError('')
            setPending(true)
          } else {
            setError(err?.message || 'Failed to render Mermaid diagram')
          }
        }
      }
    })()

    return () => {
      cancelled = true
    }
  }, [chart, reactId, streaming])

  if (pending || (streaming && !svg && !error)) {
    return (
      <div className="mermaid-block mermaid-block--pending" data-mermaid="">
        <div className="mermaid-loading">Waiting for complete diagram…</div>
        <pre className="mermaid-fallback">{chart}</pre>
      </div>
    )
  }

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
