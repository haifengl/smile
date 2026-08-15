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
import React, { useEffect, useState } from "react";
import Sidebar from "./Sidebar";
import InferenceForm from "./InferenceForm";
import ChatApp from "../chat/ChatApp";
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

  const isChat = selectedModel?.type === "chat";

  return (
    <div className="app">
      <Sidebar
        models={models}
        selectedModel={selectedModel}
        onSelect={setSelectedModel}
      />
      <div className={isChat ? "content content-chat" : "content"}>
        {isChat ? (
          <ChatApp
            key={selectedModel.id}
            model={selectedModel.id}
            title={selectedModel.id}
            embedded
          />
        ) : (
          <InferenceForm model={selectedModel} />
        )}
      </div>
    </div>
  );
}

export default App;
