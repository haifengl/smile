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
import React, { useEffect, useRef, useState } from "react";
import Form from "@rjsf/core";
import validator from "@rjsf/validator-ajv8";
import {
  detectBatchFileKind,
  formatResult,
  prepareCsv,
  readSseStream,
  toJsonLines,
  tryParseJson,
} from "./inferStream";
import "./InferPanel.css";

function typeOf(type) {
  switch (type) {
    case "float":
    case "double":
      return "number";
    case "byte":
    case "short":
    case "char":
    case "integer":
    case "long":
      return "integer";
    case "bool":
      return "boolean";
    default:
      return type;
  }
}

function toJsonSchema(model) {
  const jsonSchema = {
    title: model.id,
    type: "object",
    required: [],
    properties: {},
  };
  for (const key in model.schema) {
    const field = model.schema[key];
    jsonSchema.properties[key] = { type: typeOf(field.type) };
    if (!field.nullable) {
      jsonSchema.required.push(key);
    }
  }
  return jsonSchema;
}

function schemaKeys(model) {
  return model?.schema ? Object.keys(model.schema) : [];
}

function SmileForm({ modelId }) {
  const [modelMeta, setModelMeta] = useState(null);
  const [schema, setSchema] = useState(null);
  const [results, setResults] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [submitError, setSubmitError] = useState(null);
  const [file, setFile] = useState(null);
  const [streaming, setStreaming] = useState(false);
  const [streamCount, setStreamCount] = useState(0);
  const resultsEndRef = useRef(null);
  const abortRef = useRef(null);
  const resultIdRef = useRef(0);

  useEffect(() => {
    if (!modelId) {
      return;
    }
    setLoading(true);
    setError(null);
    setSchema(null);
    setModelMeta(null);
    setResults([]);
    setFile(null);
    setSubmitError(null);

    fetch(`/api/v1/ml/models/${modelId}`)
      .then((res) => {
        if (!res.ok) {
          throw new Error("Failed to fetch model schema");
        }
        return res.json();
      })
      .then((data) => {
        setModelMeta(data);
        setSchema(toJsonSchema(data));
        setLoading(false);
      })
      .catch((err) => {
        setError(err.message);
        setLoading(false);
      });

    return () => {
      abortRef.current?.abort();
    };
  }, [modelId]);

  useEffect(() => {
    resultsEndRef.current?.scrollIntoView({ behavior: "smooth", block: "end" });
  }, [results, streaming]);

  const appendResult = (entry) => {
    const id = ++resultIdRef.current;
    setResults((prev) => [...prev, { id, ...entry }]);
  };

  const handleSubmit = ({ formData }) => {
    setSubmitError(null);
    fetch(`/api/v1/ml/models/${modelId}`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(formData),
    })
      .then((res) => {
        if (!res.ok) {
          throw new Error("Failed to make an inference");
        }
        return res.json();
      })
      .then((data) => {
        appendResult({ source: "form", data });
      })
      .catch((err) => setSubmitError(err.message));
  };

  const handleFilePredict = async () => {
    if (!file || streaming) {
      return;
    }
    setSubmitError(null);
    const keys = schemaKeys(modelMeta);
    const { isCsv, isJson } = detectBatchFileKind(file);

    if (!isCsv && !isJson) {
      setSubmitError("Choose a .csv, .json, or .jsonl file");
      return;
    }

    abortRef.current?.abort();
    const controller = new AbortController();
    abortRef.current = controller;

    setStreaming(true);
    setStreamCount(0);

    try {
      const text = await file.text();
      let body;
      let contentType;
      if (isCsv) {
        body = prepareCsv(text, keys);
        contentType = "text/plain";
      } else {
        body = toJsonLines(text);
        contentType = "application/json";
      }
      if (!body.trim()) {
        throw new Error("File has no data rows");
      }

      const res = await fetch(`/api/v1/ml/models/${modelId}/stream`, {
        method: "POST",
        headers: { "Content-Type": contentType },
        body,
        signal: controller.signal,
      });
      if (!res.ok) {
        const msg = await res.text();
        throw new Error(msg || `Stream failed (${res.status})`);
      }

      let n = 0;
      await readSseStream(
        res,
        (payload) => {
          n += 1;
          setStreamCount(n);
          appendResult({
            source: "file",
            index: n,
            data: tryParseJson(payload),
          });
        },
        controller.signal
      );
    } catch (err) {
      if (err.name !== "AbortError") {
        setSubmitError(err.message || String(err));
      }
    } finally {
      setStreaming(false);
    }
  };

  if (loading) return <p className="toast">Loading form…</p>;
  if (error) return <p className="toast">Error: {error}</p>;
  if (!schema) return null;

  const keys = schemaKeys(modelMeta);

  return (
    <div className="infer-form">
      <div className="infer-layout">
        <section className="infer-inputs">
          <Form schema={schema} validator={validator} onSubmit={handleSubmit} />

          <div className="infer-batch">
            <h3>Batch from file</h3>
            <p className="infer-hint">
              Upload CSV (column order:{" "}
              <code>{keys.join(", ") || "schema fields"}</code>) or JSON /
              JSON-lines. Predictions stream into the panel on the right.
            </p>
            <label className="infer-file-label">
              <span>CSV or JSON file</span>
              <input
                type="file"
                accept=".csv,.json,.jsonl,text/csv,application/json"
                onChange={(e) => setFile(e.target.files?.[0] || null)}
                disabled={streaming}
              />
            </label>
            {file && (
              <p className="infer-file-name">
                {file.name}{" "}
                <span className="infer-muted">
                  ({Math.max(1, Math.round(file.size / 1024))} KB)
                </span>
              </p>
            )}
            <div className="infer-batch-actions">
              <button
                type="button"
                onClick={handleFilePredict}
                disabled={!file || streaming}
              >
                {streaming ? `Streaming… (${streamCount})` : "Run"}
              </button>
              {streaming && (
                <button
                  type="button"
                  className="infer-btn"
                  onClick={() => abortRef.current?.abort()}
                >
                  Stop
                </button>
              )}
            </div>
          </div>

          {submitError && <p className="infer-error">{submitError}</p>}
        </section>

        <aside className="infer-results" aria-live="polite">
          <header className="infer-results-header">
            <div>
              <h3>Predictions</h3>
              <p className="infer-muted">
                {results.length === 0
                  ? "Submit the form or run a file"
                  : `${results.length} result${results.length === 1 ? "" : "s"}`}
                {streaming ? " · receiving…" : ""}
              </p>
            </div>
            <button
              type="button"
              className="infer-btn"
              onClick={() => setResults([])}
              disabled={results.length === 0 || streaming}
            >
              Clear
            </button>
          </header>

          <div className="infer-results-scroll">
            {results.length === 0 && !streaming && (
              <div className="infer-results-empty">
                Results appear here after each prediction.
              </div>
            )}
            {results.map((item) => (
              <article key={item.id} className="infer-result-card">
                <div className="infer-result-meta">
                  <span className="infer-result-badge">
                    {item.source === "file" ? `#${item.index}` : "Single"}
                  </span>
                </div>
                <pre className="infer-result-body">{formatResult(item.data)}</pre>
              </article>
            ))}
            {streaming && (
              <div className="infer-streaming-indicator">Streaming…</div>
            )}
            <div ref={resultsEndRef} />
          </div>
        </aside>
      </div>
    </div>
  );
}

export default SmileForm;
