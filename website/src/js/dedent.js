/*
 * Remove the minimum shared leading whitespace from a code block.
 */
(function (root) {
  'use strict';

  function dedent(text) {
    if (text == null) return '';
    var lines = String(text).replace(/\r\n/g, '\n').split('\n');
    while (lines.length && !lines[0].trim()) lines.shift();
    while (lines.length && !lines[lines.length - 1].trim()) lines.pop();
    var indents = lines
      .filter(function (line) {
        return line.trim();
      })
      .map(function (line) {
        var match = line.match(/^(\s*)/);
        return match ? match[1].length : 0;
      });
    if (!indents.length) return lines.join('\n');
    var min = Math.min.apply(null, indents);
    if (min === 0) return lines.join('\n');
    return lines
      .map(function (line) {
        if (!line.trim()) return '';
        return line.length >= min ? line.slice(min) : line;
      })
      .join('\n');
  }

  root.smileDedent = dedent;
})(typeof globalThis !== 'undefined' ? globalThis : window);
