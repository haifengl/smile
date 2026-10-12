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
 * SHAP Decision Plot
 *
 * Traces a line upward from the baseline E[f(x)] at the bottom to the
 * final prediction f(x) at the top, adding feature effects cumulatively.
 * Helps inspect single prediction trajectories clearly.
 *
 * @param {object} props
 * @param {number} props.baseValue
 * @param {number} props.predictionValue
 * @param {Array<{ name: string, value: *, attribution: number, absAttribution: number }>} props.features
 * @param {number} [props.topK=10]
 * @param {number} [props.width=680]
 */
export default function DecisionPlot({
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

  // Bottom-up trajectory:
  // Step 0 at Y = baseline level (bottom), value = baseValue
  // For each feature i from 0 to N-1 (ordered from least significant to most significant at top):
  // Let features be ordered so the highest impact feature is at the top.
  // In SHAP Decision plots:
  // - Bottom (Y=0): base value E[f(x)]
  // - Ascending Y steps add each feature's contribution
  // - Top (Y=N): final prediction f(x)
  const trajectory = useMemo(() => {
    // Reverse so top of plot (index 0) has highest impact
    const reversed = [...displayFeatures].reverse();
    let current = baseValue;
    const points = [{ name: "Base value", value: null, attribution: 0, val: current }];

    for (let i = 0; i < reversed.length; i++) {
      const feat = reversed[i];
      current += feat.attribution;
      points.push({
        ...feat,
        val: current,
      });
    }

    return points;
  }, [displayFeatures, baseValue]);

  // Compute scale bounds
  const { minX, maxX } = useMemo(() => {
    let min = Math.min(baseValue, predictionValue);
    let max = Math.max(baseValue, predictionValue);
    for (const p of trajectory) {
      min = Math.min(min, p.val);
      max = Math.max(max, p.val);
    }
    const pad = Math.max(0.01, (max - min) * 0.1);
    return { minX: min - pad, maxX: max + pad };
  }, [trajectory, baseValue, predictionValue]);

  const rowHeight = 36;
  const marginTop = 50;
  const marginBottom = 50;
  const marginLeft = 200;
  const marginRight = 50;
  const plotWidth = width - marginLeft - marginRight;
  const numSteps = trajectory.length;
  const height = marginTop + numSteps * rowHeight + marginBottom;

  const scaleX = (val) => {
    if (maxX === minX) return marginLeft + plotWidth / 2;
    return marginLeft + ((val - minX) / (maxX - minX)) * plotWidth;
  };

  const scaleY = (stepIdx) => {
    // stepIdx = 0 (Base value) at bottom:
    // stepIdx = numSteps - 1 (Top feature) at top:
    const topY = marginTop;
    const bottomY = height - marginBottom;
    if (numSteps <= 1) return bottomY;
    return bottomY - (stepIdx / (numSteps - 1)) * (bottomY - topY);
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

  // Build SVG path d string
  const pathD = useMemo(() => {
    return trajectory
      .map((p, i) => {
        const x = scaleX(p.val);
        const y = scaleY(i);
        return `${i === 0 ? "M" : "L"} ${x} ${y}`;
      })
      .join(" ");
  }, [trajectory, minX, maxX, width]);

  return (
    <div className="shap-plot-container">
      <div className="shap-plot-header">
        <div>
          <h4 className="shap-plot-title">Decision Plot</h4>
          <p className="shap-plot-subtitle">
            Traces prediction path from base value{" "}
            <span className="shap-badge-base">{formatShapNumber(baseValue)}</span> to output{" "}
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
          {/* Vertical Grid lines */}
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
            y1={marginTop - 10}
            x2={scaleX(baseValue)}
            y2={height - marginBottom + 10}
            stroke="#94a3b8"
            strokeWidth="1.5"
            strokeDasharray="4, 3"
          />

          {/* Horizontal feature level guide lines and feature labels */}
          {trajectory.map((point, i) => {
            const y = scaleY(i);
            const isBase = i === 0;
            return (
              <g key={i}>
                <line
                  x1={marginLeft - 8}
                  y1={y}
                  x2={width - marginRight}
                  y2={y}
                  stroke="#f1f5f9"
                  strokeWidth="1"
                />
                <text
                  x={marginLeft - 14}
                  y={y + 4}
                  textAnchor="end"
                  className="shap-wf-text-label"
                  fontWeight={isBase ? "600" : "500"}
                  fill={isBase ? "#64748b" : "#374151"}
                >
                  {point.name}
                  {point.value != null && (
                    <tspan className="shap-wf-text-val"> = {String(point.value)}</tspan>
                  )}
                </text>
              </g>
            );
          })}

          {/* Decision Path Line */}
          <path
            d={pathD}
            fill="none"
            stroke="#6366f1"
            strokeWidth="2.5"
            strokeLinejoin="round"
            strokeLinecap="round"
          />

          {/* Trajectory Points */}
          {trajectory.map((point, i) => {
            const x = scaleX(point.val);
            const y = scaleY(i);
            const isBase = i === 0;
            const isFinal = i === trajectory.length - 1;
            const color = isBase ? "#64748b" : isFinal ? "#111827" : "#6366f1";

            return (
              <circle
                key={i}
                cx={x}
                cy={y}
                r={isFinal || isBase ? 5 : 4}
                fill={color}
                stroke="#ffffff"
                strokeWidth="1.5"
                style={{ cursor: "pointer" }}
                onMouseEnter={(e) => {
                  setTooltip({
                    x: e.clientX,
                    y: e.clientY,
                    text: isBase
                      ? `Base value: ${formatShapNumber(point.val)}`
                      : `${point.name}: ${formatSignedShap(point.attribution)} (cumulative: ${formatShapNumber(point.val)})`,
                  });
                }}
                onMouseMove={(e) => {
                  setTooltip((prev) => (prev ? { ...prev, x: e.clientX, y: e.clientY } : null));
                }}
              />
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
