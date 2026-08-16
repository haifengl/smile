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
  prepareCsv,
  readSseStream,
  toJsonLines,
  tryParseJson,
} from "./inferStream";
import { normalizePrediction } from "./predictionRows";
import PredictionResults from "./PredictionResults";
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
  const [rows, setRows] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [submitError, setSubmitError] = useState(null);
  const [file, setFile] = useState(null);
  const [streaming, setStreaming] = useState(false);
  const [streamCount, setStreamCount] = useState(0);
  const [startedAt, setStartedAt] = useState(null);
  const [finishedAt, setFinishedAt] = useState(null);
  const abortRef = useRef(null);
  const resultIdRef = useRef(0);
  const pendingRef = useRef([]);
  const flushRafRef = useRef(0);

  useEffect(() => {
    if (!modelId) {
      return;
    }
    setLoading(true);
    setError(null);
    setSchema(null);
    setModelMeta(null);
    setRows([]);
    setFile(null);
    setSubmitError(null);
    setStartedAt(null);
    setFinishedAt(null);

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
      if (flushRafRef.current) {
        cancelAnimationFrame(flushRafRef.current);
      }
    };
  }, [modelId]);

  const flushPending = () => {
    flushRafRef.current = 0;
    const batch = pendingRef.current;
    if (batch.length === 0) {
      return;
    }
    pendingRef.current = [];
    setRows((prev) => [...prev, ...batch]);
  };

  const queueRows = (entries) => {
    pendingRef.current.push(...entries);
    if (!flushRafRef.current) {
      flushRafRef.current = requestAnimationFrame(flushPending);
    }
  };

  const appendRow = (entry) => {
    const id = ++resultIdRef.current;
    const normalized = entry.error
      ? { id, values: {}, error: entry.error }
      : { id, ...normalizePrediction(entry.data) };
    setRows((prev) => [...prev, normalized]);
  };

  const beginRun = () => {
    setStartedAt(Date.now());
    setFinishedAt(null);
  };

  const endRun = () => {
    setFinishedAt(Date.now());
  };

  const handleSubmit = ({ formData }) => {
    setSubmitError(null);
    beginRun();
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
        appendRow({ data });
        endRun();
      })
      .catch((err) => {
        setSubmitError(err.message);
        endRun();
      });
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

    beginRun();
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
          const id = ++resultIdRef.current;
          queueRows([{ id, ...normalizePrediction(tryParseJson(payload)) }]);
        },
        controller.signal
      );
      flushPending();
    } catch (err) {
      if (err.name !== "AbortError") {
        setSubmitError(err.message || String(err));
      }
    } finally {
      if (flushRafRef.current) {
        cancelAnimationFrame(flushRafRef.current);
        flushRafRef.current = 0;
      }
      flushPending();
      setStreaming(false);
      endRun();
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
                className="infer-btn-run"
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

        <PredictionResults
          rows={rows}
          streaming={streaming}
          startedAt={startedAt}
          finishedAt={finishedAt}
          onClear={() => {
            setRows([]);
            setStartedAt(null);
            setFinishedAt(null);
          }}
        />
      </div>
    </div>
  );
}

export default SmileForm;
