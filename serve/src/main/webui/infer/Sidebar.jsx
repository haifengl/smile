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
import React from "react";

function Sidebar({ models, onSelect, selectedModel }) {
  const chat = models.filter((m) => m.type === "chat");
  const smile = models.filter((m) => m.type === "smile");
  const onnx = models.filter((m) => m.type === "onnx");

  const renderGroup = (title, items) => {
    if (items.length === 0) {
      return null;
    }
    return (
      <>
        <h3 className="sidebar-group">{title}</h3>
        <ul>
          {items.map((model) => {
            const key = `${model.type}:${model.id}`;
            const active =
              selectedModel &&
              selectedModel.type === model.type &&
              selectedModel.id === model.id;
            const badgeClass =
              model.type === "chat"
                ? "badge-chat"
                : model.type === "onnx"
                  ? "badge-onnx"
                  : "badge-smile";
            return (
              <li
                key={key}
                className={active ? "active" : ""}
                onClick={() => onSelect(model)}
              >
                <span className="model-name" title={model.id}>
                  {model.id}
                </span>
                <span className={`badge ${badgeClass}`} title={model.kind}>
                  {model.kind}
                </span>
              </li>
            );
          })}
        </ul>
      </>
    );
  };

  return (
    <div className="sidebar">
      <h2>Models</h2>
      {models.length === 0 && <p className="sidebar-empty">No models loaded</p>}
      {renderGroup("Chat", chat)}
      {renderGroup("SMILE", smile)}
      {renderGroup("ONNX", onnx)}
    </div>
  );
}

export default Sidebar;
