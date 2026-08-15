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

/**
 * Heuristic: treat a 4-D tensor as an image when one axis is channels (1/3/4)
 * and two axes look like spatial sizes (roughly 16–2048).
 *
 * @param {number[]|null} shape
 * @returns {{ vision: boolean, layout: 'nchw'|'nhwc'|null, height: number, width: number, channels: number, batch: number }|null}
 */
export function analyzeImageShape(shape) {
  if (!shape || shape.length !== 4) {
    return null;
  }
  const dims = shape.map((d) => Number(d));
  const isSpatial = (d) => d < 0 || (d >= 16 && d <= 2048);
  const isChannel = (d) => d === 1 || d === 3 || d === 4;

  // NCHW: [N, C, H, W]
  if (isChannel(dims[1]) && isSpatial(dims[2]) && isSpatial(dims[3])) {
    return {
      vision: true,
      layout: "nchw",
      batch: dims[0] < 0 ? 1 : dims[0],
      channels: dims[1],
      height: dims[2] < 0 ? 224 : dims[2],
      width: dims[3] < 0 ? 224 : dims[3],
    };
  }
  // NHWC: [N, H, W, C]
  if (isSpatial(dims[1]) && isSpatial(dims[2]) && isChannel(dims[3])) {
    return {
      vision: true,
      layout: "nhwc",
      batch: dims[0] < 0 ? 1 : dims[0],
      height: dims[1] < 0 ? 224 : dims[1],
      width: dims[2] < 0 ? 224 : dims[2],
      channels: dims[3],
    };
  }
  return null;
}

/**
 * Picks the best image-like input from an ONNX model info payload.
 *
 * @param {{ inputs?: Array<{ name: string, shape?: number[] }> }} info
 * @returns {{ name: string, analysis: NonNullable<ReturnType<typeof analyzeImageShape>> }|null}
 */
export function findVisionInput(info) {
  if (!info?.inputs?.length) {
    return null;
  }
  for (const input of info.inputs) {
    const analysis = analyzeImageShape(input.shape);
    if (analysis?.vision) {
      return { name: input.name, analysis };
    }
  }
  return null;
}

/**
 * Flattens a tensor shape to an element count when all dims are static.
 *
 * @param {number[]|null} shape
 * @returns {number|null}
 */
export function staticElementCount(shape) {
  if (!shape || shape.length === 0) {
    return null;
  }
  let n = 1;
  for (const d of shape) {
    if (d == null || d < 0) {
      return null;
    }
    n *= d;
  }
  return n;
}

/**
 * Builds an RJSF schema for ONNX numeric inputs.
 * Small static tensors become number arrays; larger/dynamic ones use a CSV string.
 *
 * @param {{ id: string, inputs?: Array<{ name: string, shape?: number[], elementType?: string }> }} info
 */
export function onnxToJsonSchema(info) {
  const schema = {
    title: info.id,
    type: "object",
    required: [],
    properties: {},
  };
  const uiSchema = {};

  for (const input of info.inputs || []) {
    const count = staticElementCount(input.shape);
    schema.required.push(input.name);
    if (count != null && count > 0 && count <= 64) {
      schema.properties[input.name] = {
        type: "array",
        title: `${input.name} ${formatShape(input.shape)}`,
        minItems: count,
        maxItems: count,
        items: { type: "number" },
        default: Array(count).fill(0),
      };
    } else {
      schema.properties[input.name] = {
        type: "string",
        title: `${input.name} ${formatShape(input.shape)} (CSV or JSON array)`,
        default: "",
      };
      uiSchema[input.name] = { "ui:widget": "textarea", "ui:options": { rows: 4 } };
    }
  }
  return { schema, uiSchema };
}

/**
 * Converts RJSF form data into the ONNX predict JSON body.
 *
 * @param {Record<string, unknown>} formData
 * @param {{ inputs?: Array<{ name: string }> }} info
 */
export function formDataToOnnxBody(formData, info) {
  const body = {};
  for (const input of info.inputs || []) {
    const value = formData[input.name];
    if (Array.isArray(value)) {
      body[input.name] = value.map(Number);
    } else if (typeof value === "string") {
      body[input.name] = parseNumericList(value);
    } else {
      throw new Error(`Missing input: ${input.name}`);
    }
  }
  return body;
}

/**
 * @param {string} text
 * @returns {number[]}
 */
export function parseNumericList(text) {
  const trimmed = text.trim();
  if (!trimmed) {
    throw new Error("Empty numeric input");
  }
  if (trimmed.startsWith("[")) {
    const parsed = JSON.parse(trimmed);
    if (!Array.isArray(parsed)) {
      throw new Error("JSON input must be an array");
    }
    return parsed.map(Number);
  }
  return trimmed
    .split(/[\s,]+/)
    .filter(Boolean)
    .map((token) => {
      const n = Number(token);
      if (Number.isNaN(n)) {
        throw new Error(`Invalid number: ${token}`);
      }
      return n;
    });
}

/**
 * Loads an image file, resizes to the model input size, and returns a flat
 * tensor array in NCHW or NHWC layout (values in [0, 1] for FLOAT models).
 *
 * @param {File} file
 * @param {{ layout: 'nchw'|'nhwc', height: number, width: number, channels: number }} analysis
 * @param {string} [elementType]
 * @returns {Promise<number[]>}
 */
export function imageFileToTensor(file, analysis, elementType = "FLOAT") {
  return new Promise((resolve, reject) => {
    const url = URL.createObjectURL(file);
    const img = new Image();
    img.onload = () => {
      try {
        const { height, width, channels, layout } = analysis;
        const canvas = document.createElement("canvas");
        canvas.width = width;
        canvas.height = height;
        const ctx = canvas.getContext("2d");
        ctx.drawImage(img, 0, 0, width, height);
        const { data } = ctx.getImageData(0, 0, width, height);
        const asFloat = !elementType || elementType === "FLOAT" || elementType === "DOUBLE";
        const scale = asFloat ? 1 / 255 : 1;
        const out = new Array(channels * height * width);
        let o = 0;
        if (layout === "nchw") {
          for (let c = 0; c < channels; c++) {
            for (let y = 0; y < height; y++) {
              for (let x = 0; x < width; x++) {
                const i = (y * width + x) * 4;
                const src = c < 3 ? data[i + c] : data[i + 3];
                out[o++] = src * scale;
              }
            }
          }
        } else {
          for (let y = 0; y < height; y++) {
            for (let x = 0; x < width; x++) {
              const i = (y * width + x) * 4;
              for (let c = 0; c < channels; c++) {
                const src = c < 3 ? data[i + c] : data[i + 3];
                out[o++] = src * scale;
              }
            }
          }
        }
        resolve(out);
      } catch (err) {
        reject(err);
      } finally {
        URL.revokeObjectURL(url);
      }
    };
    img.onerror = () => {
      URL.revokeObjectURL(url);
      reject(new Error("Failed to load image"));
    };
    img.src = url;
  });
}

function formatShape(shape) {
  if (!shape) {
    return "";
  }
  return `[${shape.join(", ")}]`;
}
