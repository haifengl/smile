/*
 * Copyright (c) 2010-2025 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
import React, { useEffect, useState } from "react";
import Sidebar from "./Sidebar";
import InferenceForm from "./InferenceForm";
import "./App.css";

function App() {
  const [models, setModels] = useState([]);
  const [selectedModel, setSelectedModel] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    fetch("/api/v1/models")
      .then((res) => {
        if (!res.ok) {
          throw new Error("Failed to fetch models");
        }
        return res.json();
      })
      .then((data) => {
        setModels(data);
        setLoading(false);
      })
      .catch((err) => {
        setError(err.message);
        setLoading(false);
      });
  }, []);

  if (loading) return <p className="toast">Loading models…</p>;
  if (error) return <p className="toast">Error: {error}</p>;

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
