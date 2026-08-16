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
import React, { useRef } from "react";

/**
 * Returns true when {@code value} is a non-empty form field value.
 */
function hasFieldValue(value) {
  if (value == null || value === "") {
    return false;
  }
  if (typeof value === "number") {
    return !Number.isNaN(value);
  }
  if (typeof value === "boolean") {
    return true;
  }
  if (Array.isArray(value)) {
    return value.length > 0;
  }
  if (typeof value === "object") {
    return Object.values(value).some(hasFieldValue);
  }
  return String(value).trim().length > 0;
}

/**
 * Returns true when every mandatory (schema {@code required}) field has a value.
 *
 * @param {object|null} formData current RJSF form data
 * @param {object|null} schema JSON Schema with optional {@code required} array
 */
export function formHasRequiredValues(formData, schema) {
  const required = Array.isArray(schema?.required) ? schema.required : [];
  if (required.length === 0) {
    // No mandatory fields — treat as ready only if something was entered.
    if (formData == null || typeof formData !== "object") {
      return false;
    }
    return Object.values(formData).some(hasFieldValue);
  }
  if (formData == null || typeof formData !== "object") {
    return false;
  }
  return required.every((key) => hasFieldValue(formData[key]));
}

/**
 * Shared footer for SMILE / ONNX numeric forms: optional batch file + one
 * primary action. File selected → Run (batch); otherwise Submit (form).
 *
 * Must be rendered as a child of {@code @rjsf/core} {@code Form} so the
 * Submit button can use {@code type="submit"}.
 */
export default function InferActionFooter({
  file,
  onFileChange,
  onClearFile,
  onRunFile,
  formData = null,
  schema = null,
  streaming = false,
  streamCount = 0,
  onStop,
  disabled = false,
  batchHint,
}) {
  const fileMode = Boolean(file);
  const inputRef = useRef(null);
  const canSubmit = formHasRequiredValues(formData, schema);

  const clearFile = () => {
    if (inputRef.current) {
      inputRef.current.value = "";
    }
    onClearFile();
  };

  return (
    <div className="infer-action-footer">
      <div className="infer-batch">
        <h3>Batch from file</h3>
        <p className="infer-hint">{batchHint}</p>
        <label className="infer-file-label">
          <span>CSV or JSON file</span>
          <input
            ref={inputRef}
            type="file"
            accept=".csv,.json,.jsonl,text/csv,application/json"
            onChange={(e) => onFileChange(e.target.files?.[0] || null)}
            disabled={disabled || streaming}
          />
        </label>
        {file && (
          <div className="infer-file-row">
            <p className="infer-file-name">
              {file.name}{" "}
              <span className="infer-muted">
                ({Math.max(1, Math.round(file.size / 1024))} KB)
              </span>
            </p>
            <button
              type="button"
              className="infer-btn"
              onClick={clearFile}
              disabled={streaming}
            >
              Clear file
            </button>
          </div>
        )}
      </div>

      <div className="infer-primary-actions">
        {fileMode ? (
          <button
            type="button"
            className="infer-btn-run"
            title={`Using ${file.name}`}
            onClick={onRunFile}
            disabled={disabled || streaming}
          >
            {streaming ? `Streaming… (${streamCount})` : "Run"}
          </button>
        ) : (
          <button
            type="submit"
            className="infer-submit"
            title="Using form"
            disabled={disabled || streaming || !canSubmit}
          >
            Submit
          </button>
        )}
        {streaming && (
          <button type="button" className="infer-btn" onClick={onStop}>
            Stop
          </button>
        )}
      </div>
    </div>
  );
}
