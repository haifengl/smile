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
package smile.llm.quant;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Detects HuggingFace quantization format from {@code config.json}.
 *
 * @author Haifeng Li
 */
public final class QuantFormatDetector {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private QuantFormatDetector() {}

    /**
     * Detects the quantization format for a checkpoint directory.
     *
     * @param checkpointDir model directory containing {@code config.json}.
     * @return detected format ({@link QuantFormat#DENSE} when absent).
     * @throws IOException if {@code config.json} exists but cannot be parsed.
     */
    public static QuantFormat detect(Path checkpointDir) throws IOException {
        if (checkpointDir == null) {
            return QuantFormat.DENSE;
        }
        Path config = checkpointDir.resolve("config.json");
        if (!Files.isRegularFile(config)) {
            return QuantFormat.DENSE;
        }
        JsonNode root = MAPPER.readTree(config.toFile());
        return fromConfig(root);
    }

    /**
     * Parses {@code quantization_config} (or related fields) from a config tree.
     *
     * @param root {@code config.json} root.
     * @return detected format.
     */
    public static QuantFormat fromConfig(JsonNode root) {
        if (root == null || root.isNull()) {
            return QuantFormat.DENSE;
        }
        JsonNode qc = root.get("quantization_config");
        if (qc != null && !qc.isNull()) {
            String method = text(qc, "quant_method");
            if (method == null) {
                method = text(qc, "quant_type");
            }
            if (method != null) {
                String m = method.trim().toLowerCase();
                return switch (m) {
                    case "gptq", "auto-gptq", "autogptq" -> QuantFormat.GPTQ;
                    case "awq", "autoawq", "llm-awq" -> QuantFormat.AWQ;
                    case "fp8", "float8", "fbgemm_fp8", "compressed-tensors-fp8" -> QuantFormat.FP8;
                    case "nvfp4", "fp4", "modelopt_fp4" -> QuantFormat.NVFP4;
                    default -> {
                        String format = text(qc, "format");
                        if (format != null) {
                            String f = format.toLowerCase();
                            if (f.contains("fp8") || f.contains("float8")) {
                                yield QuantFormat.FP8;
                            }
                            if (f.contains("nvfp4") || f.contains("fp4")) {
                                yield QuantFormat.NVFP4;
                            }
                            if (f.contains("gptq")) {
                                yield QuantFormat.GPTQ;
                            }
                            if (f.contains("awq")) {
                                yield QuantFormat.AWQ;
                            }
                        }
                        yield QuantFormat.DENSE;
                    }
                };
            }
        }
        JsonNode dtype = root.get("torch_dtype");
        if (dtype != null && dtype.isString()) {
            String d = dtype.asString().toLowerCase();
            if (d.contains("float8") || d.contains("fp8")) {
                return QuantFormat.FP8;
            }
        }
        return QuantFormat.DENSE;
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull() || !v.isString()) {
            return null;
        }
        String s = v.asString();
        return s == null || s.isBlank() ? null : s;
    }
}
