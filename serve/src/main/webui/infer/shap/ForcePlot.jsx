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
 * SHAP Force Plot
 *
 * Visualizes features as competing forces shifting the prediction
 * from baseline E[f(x)] to the final prediction f(x):
 * - Red bars push higher (to the right)
 * - Blue bars push lower (to the left)
 * - Arrows meet at the output equilibrium f(x).
 *
 * @param {object} props
 * @param {number} props.baseValue model expected value E[f(x)]
 * @param {number} props.predictionValue final prediction f(x)
 * @param {Array<{ name: string, value: *, attribution: number, absAttribution: number }>} props.features
 * @param {number} [props.topK=10]
 * @param {number} [props.width=680]
 */
export default function ForcePlot({
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

  // Separate positive and negative contributors
  const { posFeatures, negFeatures, totalPos, totalNeg } = useMemo(() => {
    const pos = [];
    const neg = [];
    let pSum = 0;
    let nSum = 0;
    for (const f of displayFeatures) {
      if (f.attribution >= 0) {
        pos.push(f);
        pSum += f.attribution;
      } else {
        neg.push(f);
        nSum += Math.abs(f.attribution);
      }
    }
    // Sort descending by magnitude
    pos.sort((a, b) => b.attribution - a.attribution);
    neg.sort((a, b) => Math.abs(b.attribution) - Math.abs(a.attribution));
    return { posFeatures: pos, negFeatures: neg, totalPos: pSum, totalNeg: nSum };
  }, [displayFeatures]);

  // Geometry and scale bounds
  // The forces meet at predictionValue.
  // Leftmost point: predictionValue - totalPos
  // Rightmost point: predictionValue + totalNeg
  const { minX, maxX } = useMemo(() => {
    const startPos = predictionValue - totalPos;
    const endNeg = predictionValue + totalNeg;
    let min = Math.min(baseValue, predictionValue, startPos);
    let max = Math.max(baseValue, predictionValue, endNeg);
    const span = Math.max(0.01, max - min);
    const pad = span * 0.12;
    return { minX: min - pad, maxX: max + pad };
  }, [baseValue, predictionValue, totalPos, totalNeg]);

  const marginTop = 55;
  const marginBottom = 65;
  const marginLeft = 40;
  const marginRight = 40;
  const plotWidth = width - marginLeft - marginRight;
  const height = 180;
  const barY = 65;
  const barHeight = 36;

  const scaleX = (val) => {
    if (maxX === minX) return marginLeft + plotWidth / 2;
    return marginLeft + ((val - minX) / (maxX - minX)) * plotWidth;
  };

  // Layout positive force segments (pushing right, from predictionValue - totalPos up to predictionValue)
  const posBlocks = useMemo(() => {
    let curr = predictionValue - totalPos;
    return posFeatures.map((f) => {
      const start = curr;
      const end = curr + f.attribution;
      curr = end;
      return { ...f, start, end };
    });
  }, [posFeatures, predictionValue, totalPos]);

  // Layout negative force segments (pushing left, meeting from predictionValue out to predictionValue + totalNeg)
  const negBlocks = useMemo(() => {
    let curr = predictionValue;
    return negFeatures.map((f) => {
      const start = curr;
      const end = curr + Math.abs(f.attribution);
      curr = end;
      return { ...f, start, end };
    });
  }, [negFeatures, predictionValue]);

  const ticks = useMemo(() => {
    const count = 5;
    const res = [];
    const step = (maxX - minX) / (count - 1);
    for (let i = 0; i < count; i++) {
      res.push(minX + i * step);
    }
    return res;
  }, [minX, maxX]);

  const predX = scaleX(predictionValue);
  const baseX = scaleX(baseValue);

  return (
    <div className="shap-plot-container">
      <div className="shap-plot-header">
        <div>
          <h4 className="shap-plot-title">Force Plot</h4>
          <p className="shap-plot-subtitle">
            <span style={{ color: "#ff0051", fontWeight: 600 }}>higher (red)</span> vs{" "}
            <span style={{ color: "#008bfb", fontWeight: 600 }}>lower (blue)</span> forces balancing at{" "}
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
          {/* Axis line and ticks */}
          <line
            x1={marginLeft}
            y1={barY + barHeight + 20}
            x2={width - marginRight}
            y2={barY + barHeight + 20}
            stroke="#cbd5e1"
            strokeWidth="1"
          />
          {ticks.map((tick, i) => {
            const x = scaleX(tick);
            return (
              <g key={i}>
                <line
                  x1={x}
                  y1={barY + barHeight + 20}
                  x2={x}
                  y2={barY + barHeight + 26}
                  stroke="#94a3b8"
                  strokeWidth="1"
                />
                <text
                  x={x}
                  y={barY + barHeight + 38}
                  textAnchor="middle"
                  className="shap-wf-text-val"
                >
                  {formatShapNumber(tick)}
                </text>
              </g>
            );
          })}

          {/* Base value marker */}
          <line
            x1={baseX}
            y1={barY - 15}
            x2={baseX}
            y2={barY + barHeight + 15}
            stroke="#64748b"
            strokeWidth="1.5"
            strokeDasharray="3, 3"
          />
          <text
            x={baseX}
            y={barY - 20}
            textAnchor="middle"
            className="shap-wf-text-val"
            fontWeight="600"
          >
            base = {formatShapNumber(baseValue)}
          </text>

          {/* Positive Force Blocks (Red, pushing right) */}
          {posBlocks.map((block, i) => {
            const x1 = scaleX(block.start);
            const x2 = scaleX(block.end);
            const w = Math.max(2, x2 - x1);
            return (
              <g key={`pos-${i}`}>
                <rect
                  x={x1}
                  y={barY}
                  width={w}
                  height={barHeight}
                  fill="#ff0051"
                  stroke="#ffffff"
                  strokeWidth="0.5"
                  onMouseEnter={(e) => {
                    setTooltip({
                      x: e.clientX,
                      y: e.clientY,
                      text: `${block.name}${block.value != null ? ` = ${block.value}` : ""}: ${formatSignedShap(block.attribution)}`,
                    });
                  }}
                  onMouseMove={(e) => {
                    setTooltip((prev) => (prev ? { ...prev, x: e.clientX, y: e.clientY } : null));
                  }}
                  style={{ cursor: "pointer" }}
                />
                {w > 40 && (
                  <text
                    x={x1 + w / 2}
                    y={barY + barHeight / 2 + 4}
                    textAnchor="middle"
                    fill="#ffffff"
                    fontSize="11"
                    fontWeight="500"
                    pointerEvents="none"
                  >
                    {block.name.length > 8 ? `${block.name.slice(0, 7)}…` : block.name}
                  </text>
                )}
              </g>
            );
          })}

          {/* Negative Force Blocks (Blue, pushing left) */}
          {negBlocks.map((block, i) => {
            const x1 = scaleX(block.start);
            const x2 = scaleX(block.end);
            const w = Math.max(2, x2 - x1);
            return (
              <g key={`neg-${i}`}>
                <rect
                  x={x1}
                  y={barY}
                  width={w}
                  height={barHeight}
                  fill="#008bfb"
                  stroke="#ffffff"
                  strokeWidth="0.5"
                  onMouseEnter={(e) => {
                    setTooltip({
                      x: e.clientX,
                      y: e.clientY,
                      text: `${block.name}${block.value != null ? ` = ${block.value}` : ""}: ${formatSignedShap(block.attribution)}`,
                    });
                  }}
                  onMouseMove={(e) => {
                    setTooltip((prev) => (prev ? { ...prev, x: e.clientX, y: e.clientY } : null));
                  }}
                  style={{ cursor: "pointer" }}
                />
                {w > 40 && (
                  <text
                    x={x1 + w / 2}
                    y={barY + barHeight / 2 + 4}
                    textAnchor="middle"
                    fill="#ffffff"
                    fontSize="11"
                    fontWeight="500"
                    pointerEvents="none"
                  >
                    {block.name.length > 8 ? `${block.name.slice(0, 7)}…` : block.name}
                  </text>
                )}
              </g>
            );
          })}

          {/* Equilibrium Output Value Marker (Center pointer triangle) */}
          <polygon
            points={`${predX - 6},${barY - 8} ${predX + 6},${barY - 8} ${predX},${barY}`}
            fill="#111827"
          />
          <line
            x1={predX}
            y1={barY}
            x2={predX}
            y2={barY + barHeight + 5}
            stroke="#111827"
            strokeWidth="2"
          />
          <text
            x={predX}
            y={barY - 14}
            textAnchor="middle"
            fill="#111827"
            fontWeight="700"
            fontSize="13"
          >
            f(x) = {formatShapNumber(predictionValue)}
          </text>
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
