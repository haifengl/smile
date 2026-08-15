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
import "./App.css";

function App() {
  const [models, setModels] = useState([]);
  const [selectedModel, setSelectedModel] = useState(null);

  useEffect(() => {
    Promise.all([
      fetch("/api/v1/ml/models")
        .then((res) => (res.ok ? res.json() : []))
        .catch(() => []),
      fetch("/api/v1/onnx")
        .then((res) => (res.ok ? res.json() : []))
        .catch(() => []),
    ]).then(([smileIds, onnxIds]) => {
      const smile = (Array.isArray(smileIds) ? smileIds : []).map((id) => ({
        id,
        type: "smile",
      }));
      const onnx = (Array.isArray(onnxIds) ? onnxIds : []).map((id) => ({
        id,
        type: "onnx",
      }));
      setModels([...smile, ...onnx]);
    });
  }, []);

  return (
    <div className="app">
      <Sidebar
        models={models}
        selectedModel={selectedModel}
        onSelect={setSelectedModel}
      />
      <div className="content">
        <InferenceForm model={selectedModel} />
      </div>
    </div>
  );
}

export default App;
