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
import Form from "@rjsf/core";
import validator from "@rjsf/validator-ajv8";

function InferenceForm({ model }) {
  const [schema, setSchema] = useState(null);
  const [prediction, setPrediction] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    if (!model) return;

    const typeOf = (type) => {
      switch (type) {
        case "float":
        case "double":
           return "number";
        case "byte":
        case "short":
        case "char":
        case "integer":
        case "long":
           return "integer";
        case "bool":
           return "boolean";
        default:
           return type;
      }
    };

    const toJsonSchema = (model) => {
      const jsonSchema = {
        "title": model.id,
        "type": "object",
        "required": [],
        "properties": {}
      };
      for (const key in model.schema) {
        const field = model.schema[key];
        jsonSchema.properties[key] = {
          "type": typeOf(field.type)
        }
        if (!field.nullable) {
          jsonSchema.required.push(key);
        }
      }
      return jsonSchema;
    };

    fetch(`/api/v1/models/${model}`)
      .then((res) => {
        if (!res.ok) {
          throw new Error("Failed to fetch model schema");
        }
        return res.json();
      })
      .then((data) => {
        setSchema(toJsonSchema(data));
        setLoading(false);
      })
      .catch((err) => {
        setError(err.message);
        setLoading(false);
      });
  }, [model]);

  const handleSubmit = ({ formData }) => {
    console.log("Submitted data:", formData);
    fetch(`/api/v1/models/${model}`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(formData),
    })
      .then((res) => {
        if (!res.ok) {
          throw new Error("Failed to make an inference");
        }
        return res.json();
      })
     .then((data) => {
       setPrediction(data);
       const dialog = document.getElementById('output');
       dialog.showModal();
       setTimeout(function() {
         dialog.close();
       }, 10000);
     })
     .catch((err) => {
       setError(err.message);
     });
  };

  const handleClose = () => {
    const dialog = document.getElementById('output');
    dialog.close();
  };

  if (!model) return <p className="toast">Select a model for inference...</p>;
  if (loading) return <p className="toast">Loading form…</p>;
  if (error) return <p className="toast">Error: {error}</p>;

  return (
    <div>
      <Form
        schema={schema}
        validator={validator}
        onSubmit={handleSubmit}
      />
      <dialog id="output">
        <h3 style={{ marginTop: "0px" }}>Output</h3>
        <div className="json-container">
          <pre>{JSON.stringify(prediction, null, 2) }</pre>
        </div>
        <button onClick={handleClose}>Close</button>
      </dialog>
    </div>
  );
}

export default InferenceForm;
