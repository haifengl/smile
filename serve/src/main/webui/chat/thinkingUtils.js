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
    return rawText ?? ''
  }

  const lines = thinking.replace(/^\n+/, '').replace(/\n+$/, '').split('\n')
  const quoted = lines.map((line) => `> ${line}`).join('\n') + '\n\n'
  return quoted + (answer ?? '')
}
