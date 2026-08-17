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
  prepareCsv,
  readSseStream,
  toJsonLines,
  tryParseJson,
} from "./inferStream";
import { normalizePrediction } from "./predictionRows";
import PredictionResults from "./PredictionResults";
import InferActionFooter from "./InferActionFooter";
import "./InferPanel.css";

function OnnxForm({ modelId }) {
  const [info, setInfo] = useState(null);
  const [mode, setMode] = useState("auto");
  const [rows, setRows] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [submitError, setSubmitError] = useState(null);
  const [imageFiles, setImageFiles] = useState([]);
  const [previewUrls, setPreviewUrls] = useState([]);
  const [submitting, setSubmitting] = useState(false);
  const [submitProgress, setSubmitProgress] = useState(null);
  const [batchFile, setBatchFile] = useState(null);
  const [formData, setFormData] = useState({});
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
    setInfo(null);
    setRows([]);
    setImageFiles([]);
    setPreviewUrls([]);
    setBatchFile(null);
    setFormData({});
    setSubmitError(null);
    setMode("auto");
    setStartedAt(null);
    setFinishedAt(null);
    setSubmitProgress(null);

    fetch(`/smile/api/v1/onnx/${modelId}`)
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
      if (flushRafRef.current) {
        cancelAnimationFrame(flushRafRef.current);
      }
    };
  }, [modelId]);

  useEffect(() => {
    if (!imageFiles.length) {
      setPreviewUrls([]);
      return undefined;
    }
    const urls = imageFiles.map((file) => URL.createObjectURL(file));
    setPreviewUrls(urls);
    return () => {
      urls.forEach((url) => URL.revokeObjectURL(url));
    };
  }, [imageFiles]);

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
    setRows((prev) => [
      ...prev,
      entry.error
        ? { id, values: {}, error: entry.error }
        : { id, ...normalizePrediction(entry.data) },
    ]);
  };

  const beginRun = () => {
    if (flushRafRef.current) {
      cancelAnimationFrame(flushRafRef.current);
      flushRafRef.current = 0;
    }
    pendingRef.current = [];
    setRows([]);
    setStartedAt(Date.now());
    setFinishedAt(null);
  };

  const endRun = () => {
    setFinishedAt(Date.now());
  };

  const runPredict = async (body) => {
    setSubmitting(true);
    setSubmitError(null);
    beginRun();
    try {
      const res = await fetch(`/smile/api/v1/onnx/${modelId}`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body),
      });
      if (!res.ok) {
        const text = await res.text();
        throw new Error(text || "Failed to make an inference");
      }
      const data = await res.json();
      appendRow({ data });
    } catch (err) {
      setSubmitError(err.message);
    } finally {
      setSubmitting(false);
      endRun();
    }
  };

  const buildImageBody = async (file) => {
    const inputMeta = info.inputs.find((i) => i.name === vision.name);
    const tensor = await imageFileToTensor(
      file,
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
    return body;
  };

  const handleNumericSubmit = ({ formData }) => {
    if (batchFile) {
      return;
    }
    try {
      const body = formDataToOnnxBody(formData, info);
      runPredict(body);
    } catch (err) {
      setSubmitError(err.message);
    }
  };

  const handleImageSubmit = async (event) => {
    event.preventDefault();
    if (!vision || imageFiles.length === 0) {
      setSubmitError("Select one or more image files first");
      return;
    }
    setSubmitting(true);
    setSubmitError(null);
    setSubmitProgress({ done: 0, total: imageFiles.length });
    beginRun();
    try {
      for (let i = 0; i < imageFiles.length; i++) {
        const file = imageFiles[i];
        setSubmitProgress({ done: i, total: imageFiles.length });
        try {
          const body = await buildImageBody(file);
          const res = await fetch(`/smile/api/v1/onnx/${modelId}`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(body),
          });
          if (!res.ok) {
            const text = await res.text();
            throw new Error(text || "Failed to make an inference");
          }
          const data = await res.json();
          const id = ++resultIdRef.current;
          const normalized = normalizePrediction(data);
          setRows((prev) => [
            ...prev,
            {
              id,
              values: { file: file.name, ...normalized.values },
            },
          ]);
        } catch (err) {
          const id = ++resultIdRef.current;
          setRows((prev) => [
            ...prev,
            {
              id,
              values: { file: file.name },
              error: err.message || String(err),
            },
          ]);
        }
        setSubmitProgress({ done: i + 1, total: imageFiles.length });
      }
    } finally {
      setSubmitting(false);
      setSubmitProgress(null);
      endRun();
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
    beginRun();
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

      const res = await fetch(`/smile/api/v1/onnx/${modelId}/stream`, {
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
                <span>Upload images</span>
                <input
                  type="file"
                  accept="image/*"
                  multiple
                  onChange={(e) =>
                    setImageFiles(Array.from(e.target.files || []))
                  }
                  disabled={busy}
                />
              </label>
              {imageFiles.length > 0 && (
                <p className="infer-file-name">
                  {imageFiles.length} file{imageFiles.length === 1 ? "" : "s"}{" "}
                  selected
                </p>
              )}
              {previewUrls.length > 0 && (
                <div className="image-preview-grid">
                  {previewUrls.map((url, i) => (
                    <div key={`${imageFiles[i]?.name}-${i}`} className="image-preview">
                      <img src={url} alt={imageFiles[i]?.name || "Selected"} />
                      <p className="infer-muted" title={imageFiles[i]?.name}>
                        {imageFiles[i]?.name}
                      </p>
                    </div>
                  ))}
                </div>
              )}
              {previewUrls.length > 0 && (
                <p className="infer-muted">
                  Each image is resized to {vision.analysis.width}×
                  {vision.analysis.height},{" "}
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
              )}
              <button
                type="submit"
                className="infer-submit"
                disabled={imageFiles.length === 0 || busy}
              >
                {submitting
                  ? submitProgress
                    ? `Submitting… (${submitProgress.done}/${submitProgress.total})`
                    : "Submitting…"
                  : "Submit"}
              </button>
            </form>
          ) : (
            numericSchema && (
              <Form
                schema={numericSchema.schema}
                uiSchema={numericSchema.uiSchema}
                validator={validator}
                formData={formData}
                onChange={({ formData: next }) => setFormData(next)}
                onSubmit={handleNumericSubmit}
                disabled={busy}
              >
                <InferActionFooter
                  file={batchFile}
                  formData={formData}
                  schema={numericSchema.schema}
                  onFileChange={setBatchFile}
                  onClearFile={() => setBatchFile(null)}
                  onRunFile={handleFilePredict}
                  streaming={streaming}
                  streamCount={streamCount}
                  onStop={() => abortRef.current?.abort()}
                  disabled={busy}
                  batchHint={
                    inputNames.length === 1 ? (
                      <>
                        CSV floats for input <code>{inputNames[0]}</code>, or
                        JSON / JSON-lines with named tensors. With a file
                        selected, the primary button runs the batch; otherwise
                        it submits the form.
                      </>
                    ) : (
                      <>
                        Use JSON / JSON-lines with inputs{" "}
                        <code>{inputNames.join(", ") || "…"}</code>. CSV is only
                        for single-input models. With a file selected, the
                        primary button runs the batch; otherwise it submits the
                        form.
                      </>
                    )
                  }
                />
              </Form>
            )
          )}

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

export default OnnxForm;
