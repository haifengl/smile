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
import React, { useEffect, useMemo, useState } from "react";
import Form from "@rjsf/core";
import validator from "@rjsf/validator-ajv8";
import {
  findVisionInput,
  formDataToOnnxBody,
  imageFileToTensor,
  onnxToJsonSchema,
} from "./onnxUtils";

function ResultDialog({ prediction }) {
  return (
    <dialog id="output">
      <h3 style={{ marginTop: "0px" }}>Output</h3>
      <div className="json-container">
        <pre>{JSON.stringify(prediction, null, 2)}</pre>
      </div>
      <button type="button" onClick={() => document.getElementById("output").close()}>
        Close
      </button>
    </dialog>
  );
}

function showResult(setPrediction, data) {
  setPrediction(data);
  const dialog = document.getElementById("output");
  dialog.showModal();
  setTimeout(() => dialog.close(), 10000);
}

function OnnxForm({ modelId }) {
  const [info, setInfo] = useState(null);
  const [mode, setMode] = useState("auto"); // auto | numeric | image
  const [prediction, setPrediction] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [imageFile, setImageFile] = useState(null);
  const [previewUrl, setPreviewUrl] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (!modelId) {
      return;
    }
    setLoading(true);
    setError(null);
    setInfo(null);
    setPrediction(null);
    setImageFile(null);
    setPreviewUrl(null);
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

  const vision = useMemo(() => (info ? findVisionInput(info) : null), [info]);
  const effectiveMode =
    mode === "auto" ? (vision ? "image" : "numeric") : mode;

  const numericSchema = useMemo(
    () => (info ? onnxToJsonSchema(info) : null),
    [info]
  );

  const runPredict = async (body) => {
    setSubmitting(true);
    setError(null);
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
      showResult(setPrediction, data);
    } catch (err) {
      setError(err.message);
    } finally {
      setSubmitting(false);
    }
  };

  const handleNumericSubmit = ({ formData }) => {
    try {
      const body = formDataToOnnxBody(formData, info);
      runPredict(body);
    } catch (err) {
      setError(err.message);
    }
  };

  const handleImageSubmit = async (event) => {
    event.preventDefault();
    if (!vision || !imageFile) {
      setError("Select an image file first");
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
      // Fill any remaining inputs with zeros if present (rare for vision models).
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
      await runPredict(body);
    } catch (err) {
      setError(err.message);
    }
  };

  if (loading) return <p className="toast">Loading form…</p>;
  if (error && !info) return <p className="toast">Error: {error}</p>;
  if (!info) return null;

  return (
    <div className="onnx-form">
      <div className="model-meta">
        <h2>{info.id}</h2>
        <p className="muted">
          {info.graphName || "ONNX"}
          {info.version != null ? ` · v${info.version}` : ""}
        </p>
        <div className="mode-toggle">
          <label>
            Input mode{" "}
            <select value={mode} onChange={(e) => setMode(e.target.value)}>
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
          <p className="hint">
            Detected image input <code>{vision.name}</code>{" "}
            {vision.analysis.layout.toUpperCase()}{" "}
            {vision.analysis.height}×{vision.analysis.width}×
            {vision.analysis.channels}
          </p>
        )}
      </div>

      {error && <p className="form-error">{error}</p>}

      {effectiveMode === "image" && vision ? (
        <form className="image-form" onSubmit={handleImageSubmit}>
          <label className="file-label">
            Upload image
            <input
              type="file"
              accept="image/*"
              onChange={(e) => setImageFile(e.target.files?.[0] || null)}
            />
          </label>
          {previewUrl && (
            <div className="image-preview">
              <img src={previewUrl} alt="Selected input" />
              <p className="muted">
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
          <button type="submit" disabled={!imageFile || submitting}>
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
            disabled={submitting}
          />
        )
      )}

      <ResultDialog prediction={prediction} />
    </div>
  );
}

export default OnnxForm;
