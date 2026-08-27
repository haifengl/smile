/** Remove the minimum shared leading whitespace from a code block. */
function dedent(text) {
  if (text == null) return '';
  const lines = String(text).replace(/\r\n/g, '\n').split('\n');
  while (lines.length && !lines[0].trim()) lines.shift();
  while (lines.length && !lines[lines.length - 1].trim()) lines.pop();
  const indents = lines
    .filter((line) => line.trim())
    .map((line) => (line.match(/^(\s*)/) || ['', ''])[1].length);
  if (!indents.length) return lines.join('\n');
  const min = Math.min(...indents);
  if (min === 0) return lines.join('\n');
  return lines
    .map((line) => {
      if (!line.trim()) return '';
      return line.length >= min ? line.slice(min) : line;
    })
    .join('\n');
}

module.exports = dedent;
