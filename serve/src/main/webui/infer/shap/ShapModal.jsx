/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Serve is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * SMILE Serve is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with SMILE Serve. If not, see
 * <https://www.gnu.org/licenses/>.
 */
import React, { useEffect, useMemo, useRef, useState } from "react";
import WaterfallPlot from "./WaterfallPlot";
import ForcePlot from "./ForcePlot";
import DecisionPlot from "./DecisionPlot";
import { extractShapMetadata, prepareExplanation } from "./shapUtils";
import { toPng } from "html-to-image";
import "./ShapModal.css";

/**
 * Embedded bottom pane in the Predictions panel displaying interactive SHAP
 * explanations with tabs for:
 * - Waterfall Plot
 * - Force Plot
 * - Decision Plot
 *
 * Includes target class selection for multi-class classifiers,
 * top-K feature ranking filter, and PNG export.
 *
 * @param {object} props
 * @param {object|null} props.row the selected result row
 * @param {number} [props.rowNumber] 1-based row number in the predictions table
 * @param {string[]} props.featureNames array of feature names
 * @param {string} [props.shapMethod] SHAP explainer method label
 * @param {() => void} [props.onClose] optional callback to close pane
 */
export default function ShapModal({
  row,
  rowNumber,
  featureNames = [],
  shapMethod = "SHAP",
  onClose,
}) {
  const [activeTab, setActiveTab] = useState("waterfall"); // "waterfall" | "force" | "decision"
  const [targetClass, setTargetClass] = useState(0);
  const [topK, setTopK] = useState(10);
  const [plotWidth, setPlotWidth] = useState(640);
  const plotRef = useRef(null);

  const meta = useMemo(() => {
    return extractShapMetadata(row?.rawPrediction || row);
  }, [row]);

  // Default targetClass to the predicted class when the selected row changes
  useEffect(() => {
    if (meta.isMultiClass && row?.values?.prediction != null) {
      const pred = Number(row.values.prediction);
      if (!Number.isNaN(pred) && pred >= 0 && pred < meta.numClasses) {
        setTargetClass(pred);
      }
    }
  }, [row, meta]);

  // Measure available width so SVG plots fit cleanly inside the bottom pane
  useEffect(() => {
    const el = plotRef.current;
    if (!el) {
      return undefined;
    }
    const updateWidth = () => {
      const w = el.clientWidth - 28;
      if (w > 240) {
        setPlotWidth(Math.max(420, Math.floor(w)));
      }
    };
    updateWidth();
    const observer = new ResizeObserver(updateWidth);
    observer.observe(el);
    return () => observer.disconnect();
  }, [row]);

  const explanation = useMemo(() => {
    if (!row) return null;
    return prepareExplanation({
      prediction: row.rawPrediction || row,
      featureNames,
      featureValues: row.values?.input || row.values,
      targetClass,
    });
  }, [row, featureNames, targetClass]);

  if (!row || !meta.supported || !explanation?.valid) {
    return null;
  }

  const displayRowNum = rowNumber ?? row.id ?? 1;

  const handleExportPng = async () => {
    if (!plotRef.current) return;
    try {
      const dataUrl = await toPng(plotRef.current, { backgroundColor: "#ffffff" });
      const link = document.createElement("a");
      link.download = `shap-${activeTab}-row-${displayRowNum}.png`;
      link.href = dataUrl;
      link.click();
    } catch (err) {
      console.error("Failed to export SHAP plot PNG", err);
    }
  };

  return (
    <div className="infer-shap-pane">
      <div className="infer-shap-pane-header">
        <div className="infer-shap-pane-title">
          Feature Explanations ({shapMethod}) — Row #{displayRowNum}
        </div>
        <div className="infer-shap-pane-actions">
          <button
            type="button"
            className="infer-btn"
            onClick={handleExportPng}
            title="Export plot as PNG"
          >
            Export PNG
          </button>
          {onClose && (
            <button type="button" className="infer-btn" onClick={onClose}>
              Close
            </button>
          )}
        </div>
      </div>

      <div className="shap-pane-toolbar">
        <div className="shap-plot-tabs">
          <button
            type="button"
            className={`shap-tab-btn ${activeTab === "waterfall" ? "active" : ""}`}
            onClick={() => setActiveTab("waterfall")}
          >
            Waterfall
          </button>
          <button
            type="button"
            className={`shap-tab-btn ${activeTab === "force" ? "active" : ""}`}
            onClick={() => setActiveTab("force")}
          >
            Force
          </button>
          <button
            type="button"
            className={`shap-tab-btn ${activeTab === "decision" ? "active" : ""}`}
            onClick={() => setActiveTab("decision")}
          >
            Decision
          </button>
        </div>

        <div className="shap-controls-group">
          {meta.isMultiClass && (
            <label className="shap-control-item">
              <span>Class:</span>
              <select
                className="shap-select"
                value={targetClass}
                onChange={(e) => setTargetClass(Number(e.target.value))}
              >
                {Array.from({ length: meta.numClasses }).map((_, c) => (
                  <option key={c} value={c}>
                    Class {c}
                  </option>
                ))}
              </select>
            </label>
          )}

          <label className="shap-control-item">
            <span>Top Features:</span>
            <select
              className="shap-select"
              value={topK}
              onChange={(e) => setTopK(Number(e.target.value))}
            >
              <option value={5}>Top 5</option>
              <option value={10}>Top 10</option>
              <option value={15}>Top 15</option>
              <option value={20}>Top 20</option>
              <option value={100}>All</option>
            </select>
          </label>
        </div>
      </div>

      <div className="shap-pane-body" ref={plotRef}>
        {activeTab === "waterfall" && (
          <WaterfallPlot
            baseValue={explanation.baseValue}
            predictionValue={explanation.predictionValue}
            features={explanation.features}
            topK={topK}
            width={plotWidth}
          />
        )}

        {activeTab === "force" && (
          <ForcePlot
            baseValue={explanation.baseValue}
            predictionValue={explanation.predictionValue}
            features={explanation.features}
            topK={topK}
            width={plotWidth}
          />
        )}

        {activeTab === "decision" && (
          <DecisionPlot
            baseValue={explanation.baseValue}
            predictionValue={explanation.predictionValue}
            features={explanation.features}
            topK={topK}
            width={plotWidth}
          />
        )}
      </div>
    </div>
  );
}
