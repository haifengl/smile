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
import React, { memo, useCallback, useEffect, useId, useRef, useState } from 'react'
import mermaid from 'mermaid'
import { downloadMermaidDiagram } from '../mermaidExport.js'
import './MermaidDiagram.css'

let mermaidReady = false

/** Completed diagrams keyed by chart source — survives remounts while streaming. */
const svgCache = new Map()

const DOWNLOAD_FORMATS = [
  { id: 'svg', label: 'SVG' },
  { id: 'png', label: 'PNG' },
  { id: 'pdf', label: 'PDF' },
]

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

function DownloadIcon() {
  return (
    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="16" height="16" aria-hidden="true">
      <path
        fill="currentColor"
        d="M12 3a1 1 0 0 1 1 1v9.59l2.3-2.3a1 1 0 1 1 1.4 1.42l-4 4a1 1 0 0 1-1.4 0l-4-4a1 1 0 1 1 1.4-1.42L11 13.59V4a1 1 0 0 1 1-1zm-7 14a1 1 0 0 1 1 1v1h12v-1a1 1 0 1 1 2 0v2a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1v-2a1 1 0 0 1 1-1z"
      />
    </svg>
  )
}

function MermaidDownloadMenu({ svg, diagramElement, onClose }) {
  const menuRef = useRef(null)
  const [busy, setBusy] = useState(false)
  const [exportError, setExportError] = useState('')

  useEffect(() => {
    const onKey = (e) => {
      if (e.key === 'Escape') onClose()
    }
    const onPointer = (e) => {
      if (menuRef.current && !menuRef.current.contains(e.target)) {
        onClose()
      }
    }
    document.addEventListener('keydown', onKey)
    document.addEventListener('mousedown', onPointer)
    return () => {
      document.removeEventListener('keydown', onKey)
      document.removeEventListener('mousedown', onPointer)
    }
  }, [onClose])

  const onDownload = async (format) => {
    if (busy) return
    setBusy(true)
    setExportError('')
    try {
      await downloadMermaidDiagram(svg, format, 'mermaid-diagram', diagramElement)
      onClose()
    } catch (err) {
      setExportError(err?.message || 'Download failed')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="mermaid-download-menu" ref={menuRef} role="menu" aria-label="Download diagram">
      {DOWNLOAD_FORMATS.map((fmt) => (
        <button
          key={fmt.id}
          type="button"
          className="mermaid-download-item"
          role="menuitem"
          disabled={busy}
          onClick={() => onDownload(fmt.id)}
        >
          Download {fmt.label}
        </button>
      ))}
      {exportError ? <div className="mermaid-download-error">{exportError}</div> : null}
    </div>
  )
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
  const [menuOpen, setMenuOpen] = useState(false)
  const renderGen = useRef(0)
  const diagramRef = useRef(null)
  const closeMenu = useCallback(() => setMenuOpen(false), [])

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
    <div className="mermaid-block mermaid-block--ready" data-mermaid="">
      <div className="mermaid-toolbar">
        <button
          type="button"
          className="mermaid-download-btn"
          title="Download diagram"
          aria-label="Download diagram"
          aria-haspopup="menu"
          aria-expanded={menuOpen}
          onClick={() => setMenuOpen((open) => !open)}
        >
          <DownloadIcon />
        </button>
        {menuOpen ? (
          <MermaidDownloadMenu
            svg={svg}
            diagramElement={diagramRef.current}
            onClose={closeMenu}
          />
        ) : null}
      </div>
      <div
        ref={diagramRef}
        className="mermaid-svg"
        dangerouslySetInnerHTML={{ __html: svg }}
      />
    </div>
  )
}

export default memo(MermaidDiagram)
