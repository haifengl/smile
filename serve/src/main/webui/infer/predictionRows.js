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

function formatScalar(value) {
  if (value == null) {
    return "";
  }
  if (typeof value === "number") {
    if (!Number.isFinite(value)) {
      return String(value);
    }
    if (Number.isInteger(value)) {
      return String(value);
    }
    const abs = Math.abs(value);
    if (abs !== 0 && (abs < 1e-3 || abs >= 1e6)) {
      return value.toExponential(3);
    }
    return String(Math.round(value * 1e6) / 1e6);
  }
  return String(value);
}

/**
 * Compact display for table cells. Long arrays are summarized.
 *
 * @param {*} value cell value
 * @param {boolean} [expanded]
 * @returns {string}
 */
export function formatCell(value, expanded = false) {
  if (value == null) {
    return "";
  }
  if (Array.isArray(value)) {
    if (expanded || value.length <= 8) {
      return value.map(formatScalar).join(", ");
    }
    const head = value.slice(0, 3).map(formatScalar).join(", ");
    return `[${head}, …] (${value.length})`;
  }
  if (typeof value === "object") {
    return JSON.stringify(value);
  }
  return formatScalar(value);
}

/**
 * Full cell text for CSV export (no summarization).
 *
 * @param {*} value cell value
 * @returns {string}
 */
export function formatCellExport(value) {
  if (value == null) {
    return "";
  }
  if (Array.isArray(value)) {
    return value.map(formatScalar).join(" ");
  }
  if (typeof value === "object") {
    return JSON.stringify(value);
  }
  return formatScalar(value);
}

export function isExpandableCell(value) {
  return Array.isArray(value) && value.length > 8;
}

/**
 * Turns a prediction payload into a flat column map for the results table.
 *
 * @param {*} data raw prediction (JSON object, number, or stream text)
 * @returns {{ values: Record<string, *> }}
 */
export function normalizePrediction(data) {
  if (data == null) {
    return { values: { result: "" } };
  }

  if (typeof data === "number" || typeof data === "boolean") {
    return { values: { prediction: data } };
  }

  if (typeof data === "string") {
    const trimmed = data.trim();
    if (!trimmed) {
      return { values: { result: "" } };
    }
    const parts = trimmed.split(/\s+/);
    const allNumeric = parts.every((p) => p !== "" && !Number.isNaN(Number(p)));
    if (allNumeric && parts.length >= 1) {
      const values = { prediction: Number(parts[0]) };
      for (let i = 1; i < parts.length; i++) {
        values[`p${i - 1}`] = Number(parts[i]);
      }
      return { values };
    }
    return { values: { result: trimmed } };
  }

  if (typeof data === "object") {
    const values = {};
    if (Object.prototype.hasOwnProperty.call(data, "prediction")) {
      values.prediction = data.prediction;
      if (Array.isArray(data.probabilities)) {
        data.probabilities.forEach((p, i) => {
          values[`p${i}`] = p;
        });
      }
      for (const [key, value] of Object.entries(data)) {
        if (key === "prediction" || key === "probabilities") {
          continue;
        }
        values[key] = value;
      }
      return { values };
    }
    for (const [key, value] of Object.entries(data)) {
      values[key] = value;
    }
    if (Object.keys(values).length === 0) {
      values.result = "";
    }
    return { values };
  }

  return { values: { result: String(data) } };
}

const PREFERRED_ORDER = ["file", "top1", "prob", "top5", "prediction", "result"];

/**
 * Stable column order across rows: prediction/result first, then p0..pn,
 * then remaining keys alphabetically.
 *
 * @param {Array<{ values: Record<string, *> }>} rows
 * @returns {string[]}
 */
export function collectColumns(rows) {
  const keys = new Set();
  for (const row of rows) {
    if (row?.values) {
      Object.keys(row.values).forEach((k) => keys.add(k));
    }
  }
  const list = [...keys];
  list.sort((a, b) => {
    const ai = PREFERRED_ORDER.indexOf(a);
    const bi = PREFERRED_ORDER.indexOf(b);
    if (ai !== -1 || bi !== -1) {
      if (ai === -1) return 1;
      if (bi === -1) return -1;
      return ai - bi;
    }
    const ap = /^p(\d+)$/.exec(a);
    const bp = /^p(\d+)$/.exec(b);
    if (ap && bp) {
      return Number(ap[1]) - Number(bp[1]);
    }
    if (ap) return -1;
    if (bp) return 1;
    return a.localeCompare(b);
  });
  return list;
}

/**
 * Builds a CSV string from table rows.
 *
 * @param {string[]} columns
 * @param {Array<{ values: Record<string, *> }>} rows
 * @returns {string}
 */
export function rowsToCsv(columns, rows) {
  const escape = (text) => {
    const s = String(text ?? "");
    if (/[",\n\r]/.test(s)) {
      return `"${s.replace(/"/g, '""')}"`;
    }
    return s;
  };
  const header = ["#", ...columns].map(escape).join(",");
  const lines = rows.map((row, i) => {
    const cells = columns.map((col) =>
      escape(formatCellExport(row.values?.[col]))
    );
    return [String(i + 1), ...cells].join(",");
  });
  return [header, ...lines].join("\n");
}

/**
 * Formats a duration for the status line.
 *
 * @param {number} ms elapsed milliseconds
 * @returns {string}
 */
export function formatElapsed(ms) {
  if (ms == null || !Number.isFinite(ms) || ms < 0) {
    return "";
  }
  if (ms < 1000) {
    return `${Math.round(ms)}ms`;
  }
  if (ms < 60_000) {
    return `${(ms / 1000).toFixed(1)}s`;
  }
  const minutes = Math.floor(ms / 60_000);
  const seconds = ((ms % 60_000) / 1000).toFixed(1);
  return `${minutes}m ${seconds}s`;
}
