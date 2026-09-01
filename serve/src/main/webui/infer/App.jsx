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
import React, { useCallback, useEffect, useState } from "react";
import Sidebar from "./Sidebar";
import InferenceForm from "./InferenceForm";
import ChatShell from "../chat/ChatShell";
import CollapsiblePanel from "../shared/CollapsiblePanel";
import { INFER_RESUME_MODEL_KEY } from "../chat/api";
import "./App.css";

/** Maps API {@code kind} to UI panel type. */
function panelType(kind) {
  if (kind === "LLM") return "chat";
  if (kind === "ONNX") return "onnx";
  return "smile";
}

function App() {
  const [models, setModels] = useState([]);
  const [selectedModel, setSelectedModel] = useState(null);
  /** Panels kept mounted so switching models preserves chat / form state. */
  const [mountedModels, setMountedModels] = useState([]);

  useEffect(() => {
    fetch("/api/v1/models")
      .then((res) => (res.ok ? res.json() : { data: [] }))
      .catch(() => ({ data: [] }))
      .then((catalog) => {
        const data = Array.isArray(catalog?.data) ? catalog.data : [];
        setModels(
          data
            .filter((m) => m?.id)
            .map((m) => ({
              id: m.id,
              kind: m.kind || "Unknown",
              type: panelType(m.kind),
            }))
        );
      });
  }, []);

  const selectModel = useCallback((model) => {
    setSelectedModel(model);
    if (model?.id) {
      setMountedModels((prev) =>
        prev.some((m) => m.id === model.id) ? prev : [...prev, model]
      );
    }
  }, []);

  useEffect(() => {
    if (!models.length) return;
    const resumeId = sessionStorage.getItem(INFER_RESUME_MODEL_KEY);
    if (!resumeId) return;
    sessionStorage.removeItem(INFER_RESUME_MODEL_KEY);
    const model = models.find((m) => m.id === resumeId);
    if (model) {
      selectModel(model);
    }
  }, [models, selectModel]);

  const isChat = selectedModel?.type === "chat";

  return (
    <div className="app">
      <CollapsiblePanel
        side="left"
        storageKey="smile.infer.model-sidebar.expanded"
        defaultExpanded={true}
        width={280}
        collapsedWidth={44}
        className="infer-model-sidebar"
        ariaLabel="Models"
      >
        <Sidebar
          models={models}
          selectedModel={selectedModel}
          onSelect={selectModel}
        />
      </CollapsiblePanel>
      <div className={isChat ? "content content-chat" : "content"}>
        {!selectedModel && (
          <p className="toast">Select a model for inference...</p>
        )}
        {mountedModels.map((model) => {
          const active = selectedModel?.id === model.id;
          if (model.type === "chat") {
            return (
              <div key={model.id} className="panel-session" hidden={!active}>
                <ChatShell embedded model={model.id} title={model.id} />
              </div>
            );
          }
          return (
            <div key={model.id} className="panel-session" hidden={!active}>
              <InferenceForm model={model} />
            </div>
          );
        })}
      </div>
    </div>
  );
}

export default App;
