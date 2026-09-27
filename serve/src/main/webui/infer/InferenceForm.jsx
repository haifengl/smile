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
import React from "react";
import SmileForm from "./SmileForm";
import OnnxForm from "./OnnxForm";

function InferenceForm({ model }) {
  if (!model) {
    return <p className="toast">Select a model for inference...</p>;
  }
  if (model.type === "onnx") {
    return <OnnxForm modelId={model.id} />;
  }
  return <SmileForm modelId={model.id} />;
}

export default InferenceForm;
