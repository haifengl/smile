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
import React, { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  collectColumns,
  formatCell,
  formatElapsed,
  isExpandableCell,
  rowsToCsv,
} from "./predictionRows";

const ROW_HEIGHT = 34;
const OVERSCAN = 12;

/**
 * Shared predictions pane: virtualized table, status line, copy/download CSV.
 *
 * @param {object} props
 * @param {Array<{ id: number, values: Record<string, *>, error?: string }>} props.rows
 * @param {boolean} [props.streaming]
 * @param {number|null} [props.startedAt] epoch ms when the current run began
 * @param {number|null} [props.finishedAt] epoch ms when the run finished
 * @param {() => void} props.onClear
 */
export default function PredictionResults({
  rows,
  streaming = false,
  startedAt = null,
  finishedAt = null,
  onClear,
}) {
  const scrollRef = useRef(null);
  const [scrollTop, setScrollTop] = useState(0);
  const [viewportHeight, setViewportHeight] = useState(400);
  const [expandedId, setExpandedId] = useState(null);
  const [copied, setCopied] = useState(false);
  const [now, setNow] = useState(() => Date.now());

  const columns = useMemo(() => {
    const cols = collectColumns(rows);
    return cols.length > 0 ? cols : ["result"];
  }, [rows]);

  useEffect(() => {
    if (!streaming || startedAt == null) {
      return undefined;
    }
    const id = window.setInterval(() => setNow(Date.now()), 200);
    return () => window.clearInterval(id);
  }, [streaming, startedAt]);

  useEffect(() => {
    const el = scrollRef.current;
    if (!el) {
      return undefined;
    }
    const update = () => setViewportHeight(el.clientHeight);
    update();
    const observer = new ResizeObserver(update);
    observer.observe(el);
    return () => observer.disconnect();
  }, []);

  // Keep the viewport near the bottom while streaming.
  useEffect(() => {
    if (!streaming) {
      return;
    }
    const el = scrollRef.current;
    if (!el) {
      return;
    }
    const distance = el.scrollHeight - el.scrollTop - el.clientHeight;
    if (distance < ROW_HEIGHT * 4) {
      el.scrollTop = el.scrollHeight;
    }
  }, [rows.length, streaming]);

  const elapsedMs = useMemo(() => {
    if (startedAt == null) {
      return null;
    }
    const end = streaming ? now : finishedAt ?? now;
    return Math.max(0, end - startedAt);
  }, [startedAt, finishedAt, streaming, now]);

  const statusText = useMemo(() => {
    if (rows.length === 0 && !streaming) {
      return "Submit the form or run a file";
    }
    const count = `${rows.length.toLocaleString()} row${rows.length === 1 ? "" : "s"}`;
    const parts = [count];
    if (elapsedMs != null && (streaming || finishedAt != null || rows.length > 0)) {
      parts.push(formatElapsed(elapsedMs));
    }
    if (streaming) {
      parts.push("receiving…");
    }
    return parts.join(" · ");
  }, [rows.length, streaming, elapsedMs, finishedAt]);

  const totalHeight = rows.length * ROW_HEIGHT;
  const startIndex = Math.max(0, Math.floor(scrollTop / ROW_HEIGHT) - OVERSCAN);
  const endIndex = Math.min(
    rows.length,
    Math.ceil((scrollTop + viewportHeight) / ROW_HEIGHT) + OVERSCAN
  );
  const visible = rows.slice(startIndex, endIndex);

  const onScroll = useCallback((event) => {
    setScrollTop(event.currentTarget.scrollTop);
  }, []);

  const exportCsv = useCallback(() => {
    return rowsToCsv(columns, rows);
  }, [columns, rows]);

  const handleCopy = async () => {
    const csv = exportCsv();
    try {
      await navigator.clipboard.writeText(csv);
      setCopied(true);
      window.setTimeout(() => setCopied(false), 1500);
    } catch {
      // Fallback for older browsers
      const area = document.createElement("textarea");
      area.value = csv;
      document.body.appendChild(area);
      area.select();
      document.execCommand("copy");
      document.body.removeChild(area);
      setCopied(true);
      window.setTimeout(() => setCopied(false), 1500);
    }
  };

  const handleDownload = () => {
    const csv = exportCsv();
    const blob = new Blob([csv], { type: "text/csv;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = `predictions-${Date.now()}.csv`;
    anchor.click();
    URL.revokeObjectURL(url);
  };

  const expandedRow = expandedId != null ? rows.find((r) => r.id === expandedId) : null;

  return (
    <aside className="infer-results" aria-live="polite">
      <header className="infer-results-header">
        <div>
          <h3>Predictions</h3>
          <p className="infer-muted infer-status">{statusText}</p>
        </div>
        <div className="infer-results-actions">
          <button
            type="button"
            className="infer-btn"
            onClick={handleCopy}
            disabled={rows.length === 0}
          >
            {copied ? "Copied" : "Copy CSV"}
          </button>
          <button
            type="button"
            className="infer-btn"
            onClick={handleDownload}
            disabled={rows.length === 0}
          >
            Download
          </button>
          <button
            type="button"
            className="infer-btn"
            onClick={onClear}
            disabled={rows.length === 0 || streaming}
          >
            Clear
          </button>
        </div>
      </header>

      {rows.length === 0 && !streaming ? (
        <div className="infer-results-empty">
          Results appear here after each prediction.
        </div>
      ) : (
        <>
          <div className="infer-table-header-wrap">
            <table className="infer-table infer-table-header">
              <colgroup>
                <col className="infer-col-index" />
                {columns.map((col) => (
                  <col key={col} />
                ))}
              </colgroup>
              <thead>
                <tr>
                  <th scope="col">#</th>
                  {columns.map((col) => (
                    <th key={col} scope="col">
                      {col}
                    </th>
                  ))}
                </tr>
              </thead>
            </table>
          </div>

          <div
            className="infer-table-scroll"
            ref={scrollRef}
            onScroll={onScroll}
          >
            <div
              className="infer-table-spacer"
              style={{ height: totalHeight }}
            >
              <table
                className="infer-table infer-table-body"
                style={{
                  transform: `translateY(${startIndex * ROW_HEIGHT}px)`,
                }}
              >
                <colgroup>
                  <col className="infer-col-index" />
                  {columns.map((col) => (
                    <col key={col} />
                  ))}
                </colgroup>
                <tbody>
                  {visible.map((row, offset) => {
                    const index = startIndex + offset;
                    const hasError = Boolean(row.error);
                    const expandable = columns.some((col) =>
                      isExpandableCell(row.values?.[col])
                    );
                    return (
                      <tr
                        key={row.id}
                        className={
                          hasError
                            ? "infer-row-error"
                            : expandable
                              ? "infer-row-expandable"
                              : undefined
                        }
                        style={{ height: ROW_HEIGHT }}
                        onClick={() => {
                          if (!expandable && !hasError) {
                            return;
                          }
                          setExpandedId((prev) =>
                            prev === row.id ? null : row.id
                          );
                        }}
                      >
                        <td className="infer-col-index">{index + 1}</td>
                        {hasError ? (
                          <td colSpan={Math.max(columns.length, 1)}>
                            {row.error}
                          </td>
                        ) : (
                          columns.map((col) => {
                            const value = row.values?.[col];
                            const open =
                              expandedId === row.id && isExpandableCell(value);
                            return (
                              <td key={col} title={formatCell(value, true)}>
                                {formatCell(value, open)}
                              </td>
                            );
                          })
                        )}
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </div>

          {expandedRow && !expandedRow.error && (
            <div className="infer-row-detail">
              <div className="infer-row-detail-title">
                Row detail
                <button
                  type="button"
                  className="infer-btn"
                  onClick={() => setExpandedId(null)}
                >
                  Close
                </button>
              </div>
              <pre>
                {columns
                  .map(
                    (col) =>
                      `${col}: ${formatCell(expandedRow.values?.[col], true)}`
                  )
                  .join("\n")}
              </pre>
            </div>
          )}
        </>
      )}
    </aside>
  );
}
