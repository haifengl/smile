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
import React, { useMemo, useState } from "react";
import {
  formatShapNumber,
  formatSignedShap,
  rankAndTruncateFeatures,
} from "./shapUtils";
import "./ShapPlots.css";

/**
 * SHAP Waterfall Plot
 *
 * Displays a step-by-step breakdown starting from baseline E[f(x)]
 * at the bottom, accumulating each feature contribution, up to
 * the final prediction f(x) at the top.
 *
 * @param {object} props
 * @param {number} props.baseValue model expected value E[f(x)]
 * @param {number} props.predictionValue final prediction f(x)
 * @param {Array<{ name: string, value: *, attribution: number, absAttribution: number }>} props.features
 * @param {number} [props.topK=10] number of top features to show
 * @param {number} [props.width=680]
 */
export default function WaterfallPlot({
  baseValue,
  predictionValue,
  features = [],
  topK = 10,
  width = 680,
}) {
  const [tooltip, setTooltip] = useState(null);

  const displayFeatures = useMemo(() => {
    return rankAndTruncateFeatures(features, topK);
  }, [features, topK]);

  // Bottom-up accumulation:
  // Row 0 is at bottom (or top? Standard waterfall has bottom = baseValue, top = f(x))
  // In SHAP Python waterfall plot, features are listed from top to bottom (highest impact at top),
  // starting at base value at bottom-ish. Let's trace from bottom to top:
  // Index 0 in displayFeatures is the highest impact feature.
  // Rendering from top row to bottom row:
  // Let cumulative trace start from baseValue at the bottom row up to top row.
  const rowData = useMemo(() => {
    // Reverse so that the flow starts from bottom (baseValue) and steps upwards to f(x)
    const reversed = [...displayFeatures].reverse();
    let currentVal = baseValue;
    const computed = [];

    for (let i = 0; i < reversed.length; i++) {
      const feat = reversed[i];
      const start = currentVal;
      const end = currentVal + feat.attribution;
      currentVal = end;
      computed.push({
        ...feat,
        start,
        end,
      });
    }

    // Now reverse back so row 0 is at the top of the plot
    return computed.reverse();
  }, [displayFeatures, baseValue]);

  // Compute X-scale bounds
  const { minX, maxX } = useMemo(() => {
    let min = Math.min(baseValue, predictionValue);
    let max = Math.max(baseValue, predictionValue);
    for (const d of rowData) {
      min = Math.min(min, d.start, d.end);
      max = Math.max(max, d.start, d.end);
    }
    const pad = Math.max(0.01, (max - min) * 0.1);
    return { minX: min - pad, maxX: max + pad };
  }, [rowData, baseValue, predictionValue]);

  const rowHeight = 36;
  const marginTop = 50;
  const marginBottom = 50;
  const marginLeft = 200; // room for feature name and value
  const marginRight = 50;
  const plotWidth = width - marginLeft - marginRight;
  const height = marginTop + rowData.length * rowHeight + marginBottom;

  const scaleX = (val) => {
    if (maxX === minX) return marginLeft + plotWidth / 2;
    return marginLeft + ((val - minX) / (maxX - minX)) * plotWidth;
  };

  const ticks = useMemo(() => {
    const count = 5;
    const res = [];
    const step = (maxX - minX) / (count - 1);
    for (let i = 0; i < count; i++) {
      res.push(minX + i * step);
    }
    return res;
  }, [minX, maxX]);

  return (
    <div className="shap-plot-container">
      <div className="shap-plot-header">
        <div>
          <h4 className="shap-plot-title">Waterfall Plot</h4>
          <p className="shap-plot-subtitle">
            Base value <span className="shap-badge-base">{formatShapNumber(baseValue)}</span> → Prediction{" "}
            <span className="shap-badge-pred">{formatShapNumber(predictionValue)}</span>
          </p>
        </div>
      </div>

      <div className="shap-svg-wrapper">
        <svg
          className="shap-plot-svg"
          width={width}
          height={height}
          viewBox={`0 0 ${width} ${height}`}
          onMouseLeave={() => setTooltip(null)}
        >
          {/* Grid lines */}
          {ticks.map((tick, i) => {
            const x = scaleX(tick);
            return (
              <g key={i}>
                <line
                  x1={x}
                  y1={marginTop - 10}
                  x2={x}
                  y2={height - marginBottom + 10}
                  className="shap-wf-grid"
                />
                <text
                  x={x}
                  y={height - marginBottom + 25}
                  textAnchor="middle"
                  className="shap-wf-text-val"
                >
                  {formatShapNumber(tick)}
                </text>
              </g>
            );
          })}

          {/* Baseline vertical line */}
          <line
            x1={scaleX(baseValue)}
            y1={marginTop - 20}
            x2={scaleX(baseValue)}
            y2={height - marginBottom + 10}
            stroke="#94a3b8"
            strokeWidth="1.5"
            strokeDasharray="4, 3"
          />
          <text
            x={scaleX(baseValue)}
            y={marginTop - 26}
            textAnchor="middle"
            className="shap-wf-text-val"
            fontWeight="600"
          >
            E[f(x)] = {formatShapNumber(baseValue)}
          </text>

          {/* Prediction vertical line */}
          <line
            x1={scaleX(predictionValue)}
            y1={marginTop - 20}
            x2={scaleX(predictionValue)}
            y2={height - marginBottom + 10}
            stroke="#111827"
            strokeWidth="1.5"
          />
          <text
            x={scaleX(predictionValue)}
            y={marginTop - 26}
            textAnchor="middle"
            className="shap-wf-text-val"
            fontWeight="700"
            fill="#111827"
          >
            f(x) = {formatShapNumber(predictionValue)}
          </text>

          {/* Rows */}
          {rowData.map((row, i) => {
            const y = marginTop + i * rowHeight;
            const barY = y + 8;
            const barH = rowHeight - 16;

            const xStart = scaleX(row.start);
            const xEnd = scaleX(row.end);
            const barX = Math.min(xStart, xEnd);
            const barW = Math.max(2, Math.abs(xEnd - xStart));

            const isPos = row.attribution >= 0;
            const barClass = isPos ? "shap-wf-bar-pos" : "shap-wf-bar-neg";

            // Next row connector
            const nextRow = rowData[i + 1];

            return (
              <g key={i}>
                {/* Feature Label on Left */}
                <text
                  x={marginLeft - 12}
                  y={barY + barH / 2 + 4}
                  textAnchor="end"
                  className="shap-wf-text-label"
                  fontWeight={row.isOther ? "normal" : "500"}
                >
                  {row.name}
                  {row.value != null && (
                    <tspan className="shap-wf-text-val"> = {String(row.value)}</tspan>
                  )}
                </text>

                {/* Bar */}
                <rect
                  x={barX}
                  y={barY}
                  width={barW}
                  height={barH}
                  rx="3"
                  className={barClass}
                  onMouseEnter={(e) => {
                    setTooltip({
                      x: e.clientX,
                      y: e.clientY,
                      text: `${row.name}: ${formatSignedShap(row.attribution)} (from ${formatShapNumber(row.start)} to ${formatShapNumber(row.end)})`,
                    });
                  }}
                  onMouseMove={(e) => {
                    setTooltip((prev) => (prev ? { ...prev, x: e.clientX, y: e.clientY } : null));
                  }}
                />

                {/* Attribution delta text next to bar */}
                <text
                  x={isPos ? barX + barW + 6 : barX - 6}
                  y={barY + barH / 2 + 4}
                  textAnchor={isPos ? "start" : "end"}
                  className="shap-wf-text-delta"
                  fill={isPos ? "#ff0051" : "#008bfb"}
                >
                  {formatSignedShap(row.attribution)}
                </text>

                {/* Connector line to row below */}
                {nextRow && (
                  <line
                    x1={xEnd}
                    y1={barY + barH}
                    x2={xEnd}
                    y2={barY + rowHeight + 8}
                    className="shap-wf-connector"
                  />
                )}
              </g>
            );
          })}
        </svg>
      </div>

      {tooltip && (
        <div
          className="shap-tooltip"
          style={{
            left: `${tooltip.x + 12}px`,
            top: `${tooltip.y + 12}px`,
          }}
        >
          {tooltip.text}
        </div>
      )}
    </div>
  );
}
