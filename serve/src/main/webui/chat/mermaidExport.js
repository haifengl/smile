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

/**
 * Ensures the SVG markup has an XML namespace for standalone download.
 *
 * @param {string} svg
 * @returns {string}
 */
export function normalizeSvg(svg) {
  let out = (svg ?? '').trim()
  if (!out) return out
  if (!/\sxmlns=/.test(out)) {
    out = out.replace(/<svg\b/, '<svg xmlns="http://www.w3.org/2000/svg"')
  }
  if (!/^<\?xml/i.test(out)) {
    out = `<?xml version="1.0" encoding="UTF-8"?>\n${out}`
  }
  return out
}

/**
 * @param {Blob} blob
 * @param {string} filename
 */
export function downloadBlob(blob, filename) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  link.rel = 'noopener'
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
}

/**
 * @param {HTMLCanvasElement} source
 * @param {string} type
 * @param {number} [quality]
 * @returns {Promise<Blob>}
 */
function canvasElementToBlob(source, type, quality) {
  return new Promise((resolve, reject) => {
    source.toBlob((blob) => {
      if (blob) resolve(blob)
      else reject(new Error('Canvas export failed'))
    }, type, quality)
  })
}

/**
 * Builds a one-page PDF embedding a JPEG image (no external PDF library).
 *
 * @param {Uint8Array} jpeg
 * @param {number} widthPx
 * @param {number} heightPx
 * @returns {Uint8Array}
 */
function jpegToPdf(jpeg, widthPx, heightPx) {
  const w = widthPx
  const h = heightPx
  const encoder = new TextEncoder()
  const parts = []
  /** @type {number[]} */ const offsets = [0]

  const add = (data) => {
    parts.push(typeof data === 'string' ? encoder.encode(data) : data)
  }
  const sizeSoFar = () => parts.reduce((n, p) => n + p.length, 0)
  const addObject = (body) => {
    offsets.push(sizeSoFar())
    add(body)
  }

  add('%PDF-1.4\n')
  addObject('1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n')
  addObject('2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n')
  addObject(
    `3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${w} ${h}] `
      + `/Contents 4 0 R /Resources << /XObject << /Im0 5 0 R >> >> >>\nendobj\n`
  )
  const content = `q\n${w} 0 0 ${h} 0 0 cm\n/Im0 Do\nQ\n`
  addObject(`4 0 obj\n<< /Length ${content.length} >>\nstream\n${content}endstream\nendobj\n`)
  offsets.push(sizeSoFar())
  add(
    `5 0 obj\n<< /Type /XObject /Subtype /Image /Width ${widthPx} /Height ${heightPx} `
      + `/ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode `
      + `/Length ${jpeg.length} >>\nstream\n`
  )
  add(jpeg)
  add('\nendstream\nendobj\n')

  const xrefStart = sizeSoFar()
  add(`xref\n0 ${offsets.length}\n`)
  add('0000000000 65535 f \n')
  for (let i = 1; i < offsets.length; i++) {
    add(`${String(offsets[i]).padStart(10, '0')} 00000 n \n`)
  }
  add(`trailer\n<< /Size ${offsets.length} /Root 1 0 R >>\nstartxref\n${xrefStart}\n%%EOF\n`)

  const total = sizeSoFar()
  const out = new Uint8Array(total)
  let offset = 0
  for (const p of parts) {
    out.set(p, offset)
    offset += p.length
  }
  return out
}

/**
 * @param {string} svgMarkup
 * @param {'svg'|'png'|'pdf'} format
 * @param {string} [basename]
 * @param {HTMLElement|null} [diagramElement] Live DOM node for PNG/PDF (preserves labels).
 */
export async function downloadMermaidDiagram(
  svgMarkup,
  format,
  basename = 'diagram',
  diagramElement = null,
) {
  const name = basename.replace(/[^\w.-]+/g, '_') || 'diagram'
  if (format === 'svg') {
    const blob = new Blob([normalizeSvg(svgMarkup)], { type: 'image/svg+xml;charset=utf-8' })
    downloadBlob(blob, `${name}.svg`)
    return
  }

  if (!diagramElement) {
    throw new Error('Diagram element is required for PNG/PDF export')
  }

  const { toCanvas } = await import('html-to-image')
  const options = {
    backgroundColor: '#ffffff',
    pixelRatio: 2,
    cacheBust: true,
  }
  const canvas = await toCanvas(diagramElement, options)

  if (format === 'png') {
    const blob = await canvasElementToBlob(canvas, 'image/png')
    downloadBlob(blob, `${name}.png`)
    return
  }

  if (format === 'pdf') {
    const jpegBlob = await canvasElementToBlob(canvas, 'image/jpeg', 0.92)
    const jpeg = new Uint8Array(await jpegBlob.arrayBuffer())
    const pdf = jpegToPdf(jpeg, canvas.width, canvas.height)
    downloadBlob(new Blob([pdf], { type: 'application/pdf' }), `${name}.pdf`)
    return
  }

  throw new Error(`Unsupported format: ${format}`)
}
