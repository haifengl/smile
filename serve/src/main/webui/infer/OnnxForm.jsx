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
import React, { useEffect, useMemo, useRef, useState } from "react";
import Form from "@rjsf/core";
import validator from "@rjsf/validator-ajv8";
import {
  findVisionInput,
  formDataToOnnxBody,
  imageFileToTensor,
  onnxToJsonSchema,
} from "./onnxUtils";
import {
  detectBatchFileKind,
  formatResult,
  prepareCsv,
  readSseStream,
  toJsonLines,
  tryParseJson,
} from "./inferStream";
import "./InferPanel.css";

function OnnxForm({ modelId }) {
  const [info, setInfo] = useState(null);
  const [mode, setMode] = useState("auto"); // auto | numeric | image
  const [results, setResults] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [submitError, setSubmitError] = useState(null);
  const [imageFile, setImageFile] = useState(null);
  const [previewUrl, setPreviewUrl] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const [batchFile, setBatchFile] = useState(null);
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
    setInfo(null);
    setResults([]);
    setImageFile(null);
    setPreviewUrl(null);
    setBatchFile(null);
    setSubmitError(null);
    setMode("auto");

    fetch(`/api/v1/onnx/${modelId}`)
      .then((res) => {
        if (!res.ok) {
          throw new Error("Failed to fetch ONNX model info");
        }
        return res.json();
      })
      .then((data) => {
        setInfo(data);
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
    if (!imageFile) {
      setPreviewUrl(null);
      return;
    }
    const url = URL.createObjectURL(imageFile);
    setPreviewUrl(url);
    return () => URL.revokeObjectURL(url);
  }, [imageFile]);

  useEffect(() => {
    resultsEndRef.current?.scrollIntoView({ behavior: "smooth", block: "end" });
  }, [results, streaming]);

  const vision = useMemo(() => (info ? findVisionInput(info) : null), [info]);
  const effectiveMode =
    mode === "auto" ? (vision ? "image" : "numeric") : mode;

  const numericSchema = useMemo(
    () => (info ? onnxToJsonSchema(info) : null),
    [info]
  );

  const inputNames = useMemo(
    () => (info?.inputs || []).map((i) => i.name),
    [info]
  );

  const appendResult = (entry) => {
    const id = ++resultIdRef.current;
    setResults((prev) => [...prev, { id, ...entry }]);
  };

  const runPredict = async (body, source = "form") => {
    setSubmitting(true);
    setSubmitError(null);
    try {
      const res = await fetch(`/api/v1/onnx/${modelId}`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body),
      });
      if (!res.ok) {
        const text = await res.text();
        throw new Error(text || "Failed to make an inference");
      }
      const data = await res.json();
      appendResult({ source, data });
    } catch (err) {
      setSubmitError(err.message);
    } finally {
      setSubmitting(false);
    }
  };

  const handleNumericSubmit = ({ formData }) => {
    try {
      const body = formDataToOnnxBody(formData, info);
      runPredict(body, "form");
    } catch (err) {
      setSubmitError(err.message);
    }
  };

  const handleImageSubmit = async (event) => {
    event.preventDefault();
    if (!vision || !imageFile) {
      setSubmitError("Select an image file first");
      return;
    }
    try {
      const inputMeta = info.inputs.find((i) => i.name === vision.name);
      const tensor = await imageFileToTensor(
        imageFile,
        vision.analysis,
        inputMeta?.elementType
      );
      const body = { [vision.name]: tensor };
      for (const input of info.inputs) {
        if (input.name === vision.name) {
          continue;
        }
        const n = (input.shape || []).reduce(
          (acc, d) => (d > 0 ? acc * d : acc),
          1
        );
        body[input.name] = Array(Math.max(n, 1)).fill(0);
      }
      await runPredict(body, "image");
    } catch (err) {
      setSubmitError(err.message);
    }
  };

  const handleFilePredict = async () => {
    if (!batchFile || streaming) {
      return;
    }
    setSubmitError(null);
    const { isCsv, isJson } = detectBatchFileKind(batchFile);
    if (!isCsv && !isJson) {
      setSubmitError("Choose a .csv, .json, or .jsonl file");
      return;
    }
    if (isCsv && inputNames.length !== 1) {
      setSubmitError(
        "CSV streaming is only for single-input ONNX models; use JSON-lines for multiple inputs"
      );
      return;
    }

    abortRef.current?.abort();
    const controller = new AbortController();
    abortRef.current = controller;
    setStreaming(true);
    setStreamCount(0);

    try {
      const text = await batchFile.text();
      let body;
      let contentType;
      if (isCsv) {
        body = prepareCsv(text, inputNames);
        contentType = "text/plain";
      } else {
        body = toJsonLines(text);
        contentType = "application/json";
      }
      if (!body.trim()) {
        throw new Error("File has no data rows");
      }

      const res = await fetch(`/api/v1/onnx/${modelId}/stream`, {
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
  if (error && !info) return <p className="toast">Error: {error}</p>;
  if (!info) return null;

  const busy = submitting || streaming;

  return (
    <div className="infer-form onnx-form">
      <div className="infer-layout">
        <section className="infer-inputs">
          <div className="model-meta">
            <h2>{info.id}</h2>
            <p className="infer-muted">
              {info.graphName || "ONNX"}
              {info.version != null ? ` · v${info.version}` : ""}
            </p>
            <div className="mode-toggle">
              <label>
                Input mode{" "}
                <select
                  value={mode}
                  onChange={(e) => setMode(e.target.value)}
                  disabled={busy}
                >
                  <option value="auto">
                    Auto{vision ? " (image)" : " (numeric)"}
                  </option>
                  <option value="numeric">Numeric</option>
                  <option value="image" disabled={!vision}>
                    Image{vision ? "" : " (not detected)"}
                  </option>
                </select>
              </label>
            </div>
            {vision && (
              <p className="infer-hint">
                Detected image input <code>{vision.name}</code>{" "}
                {vision.analysis.layout.toUpperCase()}{" "}
                {vision.analysis.height}×{vision.analysis.width}×
                {vision.analysis.channels}
              </p>
            )}
          </div>

          {effectiveMode === "image" && vision ? (
            <form className="image-form" onSubmit={handleImageSubmit}>
              <label className="infer-file-label">
                <span>Upload image</span>
                <input
                  type="file"
                  accept="image/*"
                  onChange={(e) => setImageFile(e.target.files?.[0] || null)}
                  disabled={busy}
                />
              </label>
              {previewUrl && (
                <div className="image-preview">
                  <img src={previewUrl} alt="Selected input" />
                  <p className="infer-muted">
                    Resized to {vision.analysis.width}×{vision.analysis.height},{" "}
                    {vision.analysis.layout.toUpperCase()}, values{" "}
                    {(() => {
                      const t =
                        info.inputs.find((i) => i.name === vision.name)
                          ?.elementType || "FLOAT";
                      return t === "FLOAT" || t === "DOUBLE"
                        ? "[0, 1]"
                        : "[0, 255]";
                    })()}
                  </p>
                </div>
              )}
              <button type="submit" disabled={!imageFile || busy}>
                {submitting ? "Running…" : "Predict"}
              </button>
            </form>
          ) : (
            numericSchema && (
              <Form
                schema={numericSchema.schema}
                uiSchema={numericSchema.uiSchema}
                validator={validator}
                onSubmit={handleNumericSubmit}
                disabled={busy}
              />
            )
          )}

          <div className="infer-batch">
            <h3>Batch from file</h3>
            <p className="infer-hint">
              {inputNames.length === 1 ? (
                <>
                  CSV floats for input <code>{inputNames[0]}</code>, or JSON /
                  JSON-lines with named tensors.
                </>
              ) : (
                <>
                  Use JSON / JSON-lines with inputs{" "}
                  <code>{inputNames.join(", ") || "…"}</code>. CSV is only for
                  single-input models.
                </>
              )}{" "}
              Results stream into the panel on the right.
            </p>
            <label className="infer-file-label">
              <span>CSV or JSON file</span>
              <input
                type="file"
                accept=".csv,.json,.jsonl,text/csv,application/json"
                onChange={(e) => setBatchFile(e.target.files?.[0] || null)}
                disabled={busy}
              />
            </label>
            {batchFile && (
              <p className="infer-file-name">
                {batchFile.name}{" "}
                <span className="infer-muted">
                  ({Math.max(1, Math.round(batchFile.size / 1024))} KB)
                </span>
              </p>
            )}
            <div className="infer-batch-actions">
              <button
                type="button"
                onClick={handleFilePredict}
                disabled={!batchFile || busy}
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
                    {item.source === "file"
                      ? `#${item.index}`
                      : item.source === "image"
                        ? "Image"
                        : "Single"}
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

export default OnnxForm;
