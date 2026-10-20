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

import smile.deep.tensor.Tensor;

/**
 * A linear layer that keeps its weight in MXFP8 form (in a backend-specific layout) and receives it
 * from the checkpoint after construction.
 *
 * @author Haifeng Li
 */
public interface MxFp8Layer extends LinearOp, AutoCloseable {
    /**
     * Installs the weight from checkpoint tensors.
     *
     * @param weight FP8 E4M3 {@code [N, K]} on the target CUDA device.
     * @param scale  E8M0 scales as uint8 {@code [N, K / 32]} on the same device.
     */
    void setWeight(Tensor weight, Tensor scale);

    /**
     * Returns whether the weight has been installed.
     * @return {@code true} once {@link #setWeight} has run.
     */
    boolean isLoaded();

    @Override
    void close();
}
