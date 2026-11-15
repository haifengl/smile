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
import assert from 'node:assert/strict'
import { describe, it } from 'node:test'
import React from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import Markdown from 'react-markdown'
import rehypeKatex from 'rehype-katex'
import remarkGemoji from 'remark-gemoji'
import remarkGfm from 'remark-gfm'
import remarkMath from 'remark-math'
import {
  formatThinkingAsMarkdown,
  normalizeMathMarkdown,
  splitThinking,
} from './thinkingUtils.js'

function renderMarkdown(rawText) {
  const markdown = formatThinkingAsMarkdown(rawText)
  return renderToStaticMarkup(
    React.createElement(
      Markdown,
      {
        remarkPlugins: [remarkGfm, remarkGemoji, remarkMath],
        rehypePlugins: [rehypeKatex],
      },
      markdown,
    ),
  )
}

describe('thinkingUtils and markdown math rendering (remark-math v6 + rehype-katex v7)', () => {
  it('splits thinking tags and formats thinking as blockquote', () => {
    const raw = '<think>\nStep 1: check $x = 1$\n</think>Final answer: $x = 1$'
    const { thinking, answer } = splitThinking(raw)
    assert.equal(thinking.trim(), 'Step 1: check $x = 1$')
    assert.equal(answer, 'Final answer: $x = 1$')

    const html = renderMarkdown(raw)
    assert.match(html, /<blockquote>/)
    assert.match(html, /class="katex"/)
    assert.match(html, /Final answer:/)
  })

  it('renders inline single-dollar and double-dollar math with KaTeX', () => {
    const html = renderMarkdown(
      'Lift ($L$) and coefficient ($$C_L$$) satisfy $1 + 1 = 2$.',
    )
    assert.match(html, /class="katex"/)
    assert.doesNotMatch(html, /class="katex-display"/)
    assert.match(html, /<annotation encoding="application\/x-tex">L<\/annotation>/)
    assert.match(html, /<annotation encoding="application\/x-tex">C_L<\/annotation>/)
    assert.match(html, /<annotation encoding="application\/x-tex">1 \+ 1 = 2<\/annotation>/)
  })

  it('renders fenced $$ block math and standalone single-line $$ block math in display mode', () => {
    const multiLineHtml = renderMarkdown('$$\nL = \\frac{1}{2} \\rho v^2 S C_L\n$$')
    assert.match(multiLineHtml, /class="katex-display"/)
    assert.match(multiLineHtml, /display="block"/)

    const singleLineHtml = renderMarkdown('$$E = mc^2$$')
    assert.match(singleLineHtml, /class="katex-display"/)
    assert.match(singleLineHtml, /display="block"/)
    assert.match(singleLineHtml, /<annotation encoding="application\/x-tex">E = mc\^2<\/annotation>/)
  })

  it('normalizes multiline $$ blocks with content on fence lines without swallowing subsequent paragraphs', () => {
    const raw = [
      'Equation:',
      '',
      '$$\\begin{aligned}',
      'a &= b \\\\[6pt]',
      'c &= d',
      '\\end{aligned}$$',
      '',
      'Following paragraph with **bold** text.',
    ].join('\n')

    const html = renderMarkdown(raw)
    assert.match(html, /class="katex-display"/)
    assert.doesNotMatch(html, /katex-error/)
    assert.match(html, /\\begin\{aligned\}/)
    assert.match(html, /<p>Following paragraph with <strong>bold<\/strong> text\.<\/p>/)
  })

  it('preserves first-line content in unclosed streaming $$ blocks', () => {
    const raw = '$$\\begin{aligned}\na &= b'
    const normalized = normalizeMathMarkdown(raw)
    assert.equal(normalized, '$$\n\\begin{aligned}\na &= b')
  })

  it('converts LaTeX \\[ ... \\] and \\( ... \\) delimiters to display and inline KaTeX', () => {
    const html = renderMarkdown(
      'Inline \\(a^2 + b^2 = c^2\\) and block:\n\\[\n\\int_0^1 x^2 \\, dx = \\frac{1}{3}\n\\]',
    )
    assert.match(html, /<annotation encoding="application\/x-tex">a\^2 \+ b\^2 = c\^2<\/annotation>/)
    assert.match(html, /class="katex-display"/)
    assert.match(html, /\\int_0\^1 x\^2/)
  })

  it('does not misparse currency dollar amounts as inline math', () => {
    const html1 = renderMarkdown('The price is $100 and $200 today.')
    assert.doesNotMatch(html1, /class="katex"/)
    assert.match(html1, /<p>The price is \$100 and \$200 today\.<\/p>/)

    const html2 = renderMarkdown('It costs $5 each ($10 total).')
    assert.doesNotMatch(html2, /class="katex"/)
    assert.match(html2, /<p>It costs \$5 each \(\$10 total\)\.<\/p>/)

    const html3 = renderMarkdown('The book costs $20 and $x = 5$.')
    assert.match(html3, /The book costs \$20 and <span class="katex">/)
    assert.match(html3, /<annotation encoding="application\/x-tex">x = 5<\/annotation>/)

    const html4 = renderMarkdown('The item costs $10 ($x = 2$).')
    assert.match(html4, /The item costs \$10 \(<span class="katex">/)
    assert.match(html4, /<annotation encoding="application\/x-tex">x = 2<\/annotation>/)
  })

  it('leaves fenced code blocks and inline code spans untouched', () => {
    const raw = [
      'Inline `$100 and $200` and `\\[x\\]` code.',
      '',
      '```python',
      'price = "$100 and $200"',
      'formula = "$$E = mc^2$$"',
      'latex = "\\(a + b\\)"',
      '```',
    ].join('\n')

    const normalized = normalizeMathMarkdown(raw)
    assert.equal(normalized, raw)

    const html = renderMarkdown(raw)
    assert.doesNotMatch(html, /class="katex"/)
    assert.match(html, /<code>\$100 and \$200<\/code>/)
  })
})
