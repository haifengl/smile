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
 * Reads a Quarkus SSE / RestMulti stream and invokes {@code onData} for each
 * {@code data:} payload.
 */
export async function readSseStream(response, onData, signal) {
  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";

  while (true) {
    if (signal?.aborted) {
      await reader.cancel();
      throw new DOMException("Aborted", "AbortError");
    }
    const { done, value } = await reader.read();
    if (done) {
      break;
    }
    buffer += decoder.decode(value, { stream: true });
    const lines = buffer.split(/\r?\n/);
    buffer = lines.pop() ?? "";
    for (const line of lines) {
      if (!line.startsWith("data:")) {
        continue;
      }
      const payload = line.slice(5).replace(/^\s/, "");
      if (payload && payload !== "[DONE]") {
        onData(payload);
      }
    }
  }

  const trailing = buffer.trim();
  if (trailing.startsWith("data:")) {
    const payload = trailing.slice(5).replace(/^\s/, "");
    if (payload && payload !== "[DONE]") {
      onData(payload);
    }
  }
}

export function tryParseJson(text) {
  try {
    return JSON.parse(text);
  } catch {
    return text;
  }
}

/**
 * Converts uploaded JSON / JSONL text into JSON-lines for the stream API.
 * Accepts a JSON array, a single object, or existing JSONL.
 */
export function toJsonLines(text) {
  const trimmed = text.trim();
  if (!trimmed) {
    return "";
  }
  if (trimmed.startsWith("[")) {
    const arr = JSON.parse(trimmed);
    if (!Array.isArray(arr)) {
      throw new Error("JSON root must be an array of objects or JSON-lines");
    }
    return arr.map((row) => JSON.stringify(row)).join("\n");
  }
  if (trimmed.startsWith("{")) {
    try {
      const obj = JSON.parse(trimmed);
      return JSON.stringify(obj);
    } catch {
      // Fall through to JSONL
    }
  }
  return trimmed
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean)
    .join("\n");
}

/**
 * Prepares CSV body; drops a header row when it matches {@code keys}.
 */
export function prepareCsv(text, keys = []) {
  const lines = text
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean);
  if (lines.length === 0) {
    return "";
  }
  const cells = lines[0].split(",").map((c) => c.trim().replace(/^"|"$/g, ""));
  if (
    keys.length > 0 &&
    cells.length === keys.length &&
    cells.every((c) => keys.includes(c))
  ) {
    return lines.slice(1).join("\n");
  }
  return lines.join("\n");
}

export function detectBatchFileKind(file) {
  const name = file.name.toLowerCase();
  const isCsv = name.endsWith(".csv") || file.type === "text/csv";
  const isJson =
    name.endsWith(".json") ||
    name.endsWith(".jsonl") ||
    file.type === "application/json" ||
    file.type === "application/x-ndjson";
  return { isCsv, isJson };
}
