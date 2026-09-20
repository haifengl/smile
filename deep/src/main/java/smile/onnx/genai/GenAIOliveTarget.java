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
package smile.onnx.genai;

/**
 * Olive {@code auto-opt} target derived from the GenAI EP cascade.
 *
 * @param candidateId      GenAI cascade id ({@code cuda}, {@code dml}, {@code cpu}, …).
 * @param device           Olive {@code --device} ({@code gpu}, {@code npu}, {@code cpu}).
 * @param provider         Olive {@code --provider} EP name.
 * @param defaultPrecision preferred Olive {@code --precision} when unset ({@code fp8} or {@code int4}).
 * @author Haifeng Li
 */
public record GenAIOliveTarget(
        String candidateId,
        String device,
        String provider,
        String defaultPrecision) {

    /**
     * Returns whether this target prefers FP8 when auto-selecting precision.
     *
     * @return {@code true} for CUDA (and similar) FP8-capable targets.
     */
    public boolean prefersFp8() {
        return "fp8".equalsIgnoreCase(defaultPrecision);
    }
}
