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
import React, { memo, useEffect, useId, useRef, useState } from 'react'
import mermaid from 'mermaid'
import './MermaidDiagram.css'

let mermaidReady = false

/** Completed diagrams keyed by chart source — survives remounts while streaming. */
const svgCache = new Map()

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
    const result = await mermaid.parse(source, { suppressErrors: true })
    return result !== false
  } catch {
    return false
  }
}

/**
 * Renders a {@code ```mermaid} fenced code block as an SVG diagram.
 * Once a chart source has been rendered successfully it is locked/cached so
 * later stream tokens (outside the fence) do not re-trigger Mermaid.
 *
 * @param {{ chart: string, streaming?: boolean }} props
 */
function MermaidDiagram({ chart, streaming = false }) {
  const reactId = useId().replace(/:/g, '')
  const source = (chart ?? '').trim()
  const lockedSourceRef = useRef('')
  const [svg, setSvg] = useState(() => (source ? svgCache.get(source) ?? '' : ''))
  const [error, setError] = useState('')
  const [pending, setPending] = useState(false)
  const renderGen = useRef(0)

  useEffect(() => {
    if (!source) {
      lockedSourceRef.current = ''
      setSvg('')
      setError('')
      setPending(false)
      return undefined
    }

    // Already rendered this exact chart — reuse cache / lock; skip Mermaid.
    const cached = svgCache.get(source)
    if (cached) {
      lockedSourceRef.current = source
      setSvg(cached)
      setError('')
      setPending(false)
      return undefined
    }
    if (lockedSourceRef.current === source) {
      return undefined
    }

    ensureMermaid()
    const gen = ++renderGen.current
    let cancelled = false

    ;(async () => {
      if (streaming) {
        const ok = await canParse(source)
        if (cancelled || gen !== renderGen.current) return
        if (!ok) {
          setSvg('')
          setError('')
          setPending(true)
          return
        }
      }

      const renderId = `mermaid-${reactId}-${gen}`
      try {
        const { svg: rendered } = await mermaid.render(renderId, source)
        if (cancelled || gen !== renderGen.current) return
        svgCache.set(source, rendered)
        lockedSourceRef.current = source
        setSvg(rendered)
        setError('')
        setPending(false)
      } catch (err) {
        if (cancelled || gen !== renderGen.current) return
        setSvg('')
        setPending(false)
        if (streaming) {
          setError('')
          setPending(true)
        } else {
          setError(err?.message || 'Failed to render Mermaid diagram')
        }
      }
    })()

    return () => {
      cancelled = true
    }
  }, [source, reactId, streaming])

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

export default memo(MermaidDiagram)
