/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Serve is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * SMILE Serve is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with SMILE Serve. If not, see
 * <https://www.gnu.org/licenses/>.
 */

// Built without a single HTML-like literal so editors/tooling cannot strip them.
const THINK_OPEN = '<' + 'think>'
const THINK_CLOSE = '</' + 'think>'

/**
 * Splits assistant text into optional thinking and answer spans.
 *
 * @param {string} rawText
 * @returns {{ thinking: string, answer: string }}
 */
export function splitThinking(rawText) {
  const text = rawText ?? ''
  const start = text.indexOf(THINK_OPEN)
  if (start === -1) {
    return { thinking: '', answer: text }
  }

  const end = text.indexOf(THINK_CLOSE, start + THINK_OPEN.length)
  let thinking = ''
  let answer = ''
  if (end !== -1) {
    thinking = text.substring(start + THINK_OPEN.length, end)
    answer = text.substring(end + THINK_CLOSE.length)
  } else {
    // Still streaming the thinking span.
    thinking = text.substring(start + THINK_OPEN.length)
  }

  // Preserve any text that appeared before the opening tag.
  const prefix = text.substring(0, start)
  if (prefix) {
    answer = prefix + answer
  }

  return { thinking, answer }
}

/**
 * Returns true if the character at {@code index} in {@code text} is escaped
 * by an odd number of preceding backslashes.
 *
 * @param {string} text
 * @param {number} index
 * @returns {boolean}
 */
function isEscaped(text, index) {
  let backslashes = 0
  for (let i = index - 1; i >= 0 && text[i] === '\\'; i--) {
    backslashes++
  }
  return backslashes % 2 === 1
}

/**
 * Escapes single {@code $} tokens (such as currency {@code $100 and $200})
 * that do not form a valid Pandoc-style single-dollar inline math span,
 * preventing {@code remark-math} v6's backtick-style tokenizer from pairing
 * currency amounts as LaTeX math.
 *
 * @param {string} segment
 * @returns {string}
 */
function escapeInvalidSingleDollars(segment) {
  const singleDollars = []
  for (let i = 0; i < segment.length; i++) {
    if (segment[i] !== '$' || isEscaped(segment, i)) continue
    let runEnd = i
    while (runEnd + 1 < segment.length && segment[runEnd + 1] === '$') {
      runEnd++
    }
    if (runEnd === i) {
      singleDollars.push(i)
    }
    i = runEnd
  }

  if (singleDollars.length === 0) return segment

  const toEscape = new Set()
  let idx = 0
  while (idx < singleDollars.length) {
    const openPos = singleDollars[idx]
    if (idx + 1 >= singleDollars.length) {
      toEscape.add(openPos)
      idx++
      continue
    }
    const closePos = singleDollars[idx + 1]
    const between = segment.slice(openPos + 1, closePos)
    const afterClose = segment[closePos + 1] ?? ''
    const valid =
      between.length > 0 &&
      !between.includes('\n') &&
      !between.includes('\r') &&
      !/^\s/.test(between) &&
      !/\s$/.test(between) &&
      !/^\d/.test(afterClose) &&
      !/^\d[\d,.]*[kKmMbBtT]?\s+(?:[a-zA-Z]{2,}|[a-zA-Z]\s|[(\[])/.test(between)

    if (valid) {
      idx += 2
    } else {
      toEscape.add(openPos)
      idx += 1
    }
  }

  if (toEscape.size === 0) return segment
  let out = ''
  let last = 0
  for (const pos of singleDollars) {
    if (toEscape.has(pos)) {
      out += segment.slice(last, pos) + '\\$'
      last = pos + 1
    }
  }
  out += segment.slice(last)
  return out
}

/**
 * Converts LaTeX {@code \[ ... \]} display and {@code \( ... \)} inline math
 * delimiters into {@code $$ ... $$} and {@code $ ... $} syntax understood by
 * {@code remark-math}.
 *
 * @param {string} segment
 * @returns {string}
 */
function normalizeLatexDelimiters(segment) {
  let out = ''
  let i = 0
  while (i < segment.length) {
    if (segment[i] === '\\' && !isEscaped(segment, i)) {
      const next = segment[i + 1]
      if (next === '[' || next === '(') {
        const closeChar = next === '[' ? ']' : ')'
        let closeIdx = -1
        for (let j = i + 2; j < segment.length - 1; j++) {
          if (segment[j] === '\\' && segment[j + 1] === closeChar && !isEscaped(segment, j)) {
            closeIdx = j
            break
          }
        }
        if (closeIdx !== -1) {
          const body = segment.slice(i + 2, closeIdx)
          const trimmed = body.trim()
          if (next === '[') {
            const lineStart = segment.lastIndexOf('\n', i - 1) + 1
            const lineEndIdx = segment.indexOf('\n', closeIdx + 2)
            const lineEnd = lineEndIdx === -1 ? segment.length : lineEndIdx
            const linePrefix = segment.slice(lineStart, i)
            const lineSuffix = segment.slice(closeIdx + 2, lineEnd).replace(/\r$/, '')
            const prefixOnlyBlankOrQuote = /^[ \t]*(?:>[ \t]?)*$/.test(linePrefix)
            const suffixOnlyBlank = /^[ \t]*$/.test(lineSuffix)
            if (prefixOnlyBlankOrQuote && suffixOnlyBlank) {
              out += `$$${body}$$`
            } else {
              const quoteMatch = linePrefix.match(/^([ \t]*(?:>[ \t]?)+)/)
              const quotePrefix = quoteMatch ? quoteMatch[1] : ''
              const leadBreak = prefixOnlyBlankOrQuote ? '' : `\n${quotePrefix}`
              const trailBreak = suffixOnlyBlank ? '' : `\n${quotePrefix}`
              out += `${leadBreak}$$\n${quotePrefix}${trimmed}\n${quotePrefix}$$${trailBreak}`
            }
          } else if (!trimmed) {
            out += segment.slice(i, closeIdx + 2)
          } else if (/[\r\n]/.test(trimmed)) {
            out += `$$${body}$$`
          } else {
            out += `$${trimmed}$`
          }
          i = closeIdx + 2
          continue
        }
      }
    }
    out += segment[i]
    i++
  }
  return out
}

/**
 * Formats the interior of a multi-line {@code $$ ... $$} block so that each
 * line preserves any enclosing blockquote prefix.
 *
 * @param {string} rawBody
 * @param {string} quotePrefix
 * @returns {string}
 */
function formatMathFlowBody(rawBody, quotePrefix) {
  const lines = rawBody.split(/\r?\n/)
  while (lines.length > 0 && /^[ \t]*(?:>[ \t]?)*$/.test(lines[0])) {
    lines.shift()
  }
  while (lines.length > 0 && /^[ \t]*(?:>[ \t]?)*$/.test(lines[lines.length - 1])) {
    lines.pop()
  }
  if (!quotePrefix) {
    return lines.join('\n').trim()
  }
  return lines
    .map((line) => (/^[ \t]*>/.test(line) ? line : `${quotePrefix}${line}`))
    .join('\n')
}

/**
 * Normalizes math delimiters in a non-code markdown segment for
 * {@code remark-math} v6 and {@code rehype-katex} v7:
 * <ul>
 *   <li>Converts {@code \[ ... \]} and {@code \( ... \)} delimiters.</li>
 *   <li>Ensures multi-line and standalone {@code $$ ... $$} display blocks
 *       place opening and closing {@code $$} fences on their own lines so
 *       {@code remark-math} v6 does not treat the first line as fence
 *       metadata or swallow subsequent paragraphs.</li>
 *   <li>Escapes non-math currency {@code $} signs.</li>
 * </ul>
 *
 * @param {string} segment
 * @returns {string}
 */
function normalizeMathInNonCodeSegment(segment) {
  const text = normalizeLatexDelimiters(segment)
  let out = ''
  let last = 0
  let i = 0

  while (i < text.length) {
    if (text[i] === '$' && !isEscaped(text, i)) {
      let openEnd = i
      while (openEnd + 1 < text.length && text[openEnd + 1] === '$') {
        openEnd++
      }
      const openLen = openEnd - i + 1
      if (openLen >= 2) {
        let closeStart = -1
        let closeEnd = -1
        let j = openEnd + 1
        while (j < text.length) {
          if (text[j] === '$' && !isEscaped(text, j)) {
            let k = j
            while (k + 1 < text.length && text[k + 1] === '$') {
              k++
            }
            const closeLen = k - j + 1
            if (
              closeLen === openLen ||
              (closeLen >= openLen && text.slice(openEnd + 1, j).includes('\n'))
            ) {
              closeStart = j
              closeEnd = k
              break
            }
            j = k + 1
            continue
          }
          j++
        }

        const lineStart = text.lastIndexOf('\n', i - 1) + 1
        const linePrefix = text.slice(lineStart, i)
        const quoteMatch = linePrefix.match(/^([ \t]*(?:>[ \t]?)+)/)
        const quotePrefix = quoteMatch ? quoteMatch[1] : ''
        const prefixOnlyBlankOrQuote = /^[ \t]*(?:>[ \t]?)*$/.test(linePrefix)
        const fence = '$'.repeat(openLen)

        if (closeStart !== -1) {
          out += escapeInvalidSingleDollars(text.slice(last, i))
          const rawBody = text.slice(openEnd + 1, closeStart)
          const lineEndIdx = text.indexOf('\n', closeEnd + 1)
          const lineEnd = lineEndIdx === -1 ? text.length : lineEndIdx
          const lineSuffix = text.slice(closeEnd + 1, lineEnd).replace(/\r$/, '')
          const suffixOnlyBlank = /^[ \t]*$/.test(lineSuffix)
          const isMultiline = /[\r\n]/.test(rawBody)

          if (isMultiline || (prefixOnlyBlankOrQuote && suffixOnlyBlank)) {
            const formattedBody = formatMathFlowBody(rawBody, quotePrefix)
            const leadBreak = prefixOnlyBlankOrQuote ? '' : `\n${quotePrefix}`
            const trailBreak = suffixOnlyBlank ? '' : `\n${quotePrefix}`
            const bodyPrefix =
              quotePrefix && !/^[ \t]*>/.test(formattedBody) ? quotePrefix : ''
            out += `${leadBreak}${fence}\n${bodyPrefix}${formattedBody}\n${quotePrefix}${fence}${trailBreak}`
          } else {
            out += text.slice(i, closeEnd + 1)
          }

          last = closeEnd + 1
          i = closeEnd + 1
          continue
        } else if (prefixOnlyBlankOrQuote) {
          // Unclosed streaming $$ block at start of line: ensure any text
          // after opening $$ is on the next line so remark-math v6 does not
          // discard it as fence meta.
          const lineEndIdx = text.indexOf('\n', openEnd + 1)
          const restOfOpenLine =
            lineEndIdx === -1
              ? text.slice(openEnd + 1)
              : text.slice(openEnd + 1, lineEndIdx).replace(/\r$/, '')
          if (restOfOpenLine.trim().length > 0) {
            out += escapeInvalidSingleDollars(text.slice(last, i))
            out += `${fence}\n${quotePrefix}${restOfOpenLine.trimStart()}`
            if (lineEndIdx !== -1) {
              out += text.slice(lineEndIdx)
            }
            last = text.length
            break
          }
        }
      }
      i = openEnd + 1
      continue
    }
    i++
  }

  out += escapeInvalidSingleDollars(text.slice(last))
  return out
}

/**
 * Splits a non-fenced markdown chunk around inline backtick code spans and
 * normalizes math only outside inline code.
 *
 * @param {string} chunk
 * @returns {string}
 */
function normalizeOutsideInlineCode(chunk) {
  let out = ''
  let last = 0
  let i = 0
  while (i < chunk.length) {
    if (chunk[i] === '`' && !isEscaped(chunk, i)) {
      let openEnd = i
      while (openEnd + 1 < chunk.length && chunk[openEnd + 1] === '`') {
        openEnd++
      }
      const tickLen = openEnd - i + 1
      let closeStart = -1
      let closeEnd = -1
      let j = openEnd + 1
      while (j < chunk.length) {
        if (chunk[j] === '`') {
          let k = j
          while (k + 1 < chunk.length && chunk[k + 1] === '`') {
            k++
          }
          if (k - j + 1 === tickLen) {
            closeStart = j
            closeEnd = k
            break
          }
          j = k + 1
          continue
        }
        j++
      }

      if (closeStart !== -1 && !/\r?\n[ \t]*\r?\n/.test(chunk.slice(openEnd + 1, closeStart))) {
        out += normalizeMathInNonCodeSegment(chunk.slice(last, i))
        out += chunk.slice(i, closeEnd + 1)
        last = closeEnd + 1
        i = closeEnd + 1
        continue
      }
      i = openEnd + 1
      continue
    }
    i++
  }
  out += normalizeMathInNonCodeSegment(chunk.slice(last))
  return out
}

/**
 * Normalizes markdown math syntax for compatibility with {@code remark-math} v6
 * and {@code rehype-katex} v7 while leaving fenced code blocks and inline code
 * spans untouched.
 *
 * @param {string} rawText
 * @returns {string}
 */
export function normalizeMathMarkdown(rawText) {
  const text = rawText ?? ''
  if (!text) return ''

  const lines = text.split(/(\r?\n)/)
  let out = ''
  let nonCodeBuffer = ''
  let inFence = false
  let fenceChar = ''
  let fenceLen = 0

  for (let i = 0; i < lines.length; i += 2) {
    const line = lines[i]
    const newline = lines[i + 1] ?? ''

    if (!inFence) {
      const openMatch = line.match(/^([ \t]*(?:>[ \t]?)*)[ \t]{0,3}(`{3,}|~{3,})([^\r\n]*)$/)
      if (openMatch && (openMatch[2][0] !== '`' || !openMatch[3].includes('`'))) {
        if (nonCodeBuffer) {
          out += normalizeOutsideInlineCode(nonCodeBuffer)
          nonCodeBuffer = ''
        }
        inFence = true
        fenceChar = openMatch[2][0]
        fenceLen = openMatch[2].length
        out += line + newline
        continue
      }
      nonCodeBuffer += line + newline
    } else {
      out += line + newline
      const closeMatch = line.match(/^([ \t]*(?:>[ \t]?)*)[ \t]{0,3}(`{3,}|~{3,})[ \t]*$/)
      if (
        closeMatch &&
        closeMatch[2][0] === fenceChar &&
        closeMatch[2].length >= fenceLen
      ) {
        inFence = false
        fenceChar = ''
        fenceLen = 0
      }
    }
  }

  if (nonCodeBuffer) {
    out += normalizeOutsideInlineCode(nonCodeBuffer)
  }

  return out
}

/**
 * Converts thinking spans into markdown blockquotes so react-markdown
 * renders notebook-style {@code <blockquote>} chrome. Prefer
 * {@link splitThinking} + an explicit blockquote when possible.
 *
 * @param {string} rawText
 * @returns {string}
 */
export function formatThinkingAsMarkdown(rawText) {
  const { thinking, answer } = splitThinking(rawText)
  if (!thinking) {
    return normalizeMathMarkdown(rawText ?? '')
  }

  const normalizedThinking = normalizeMathMarkdown(
    thinking.replace(/^\n+/, '').replace(/\n+$/, ''),
  )
  const lines = normalizedThinking.split('\n')
  const quoted = lines.map((line) => `> ${line}`).join('\n') + '\n\n'
  return quoted + normalizeMathMarkdown(answer ?? '')
}
