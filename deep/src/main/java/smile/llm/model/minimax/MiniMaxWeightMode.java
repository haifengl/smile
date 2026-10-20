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
package smile.llm.model.minimax;

import smile.deep.layer.LinearLayer;
import smile.llm.quant.LinearOp;
import smile.llm.quant.MxFp8BlockLinear;
import smile.llm.quant.MxFp8MarlinLinear;

/**
 * How the model's linear weights are stored and multiplied.
 *
 * @author Haifeng Li
 */
public enum MiniMaxWeightMode {
    /** Dense weights in the compute dtype (BF16 checkpoints, or MXFP8 expanded while loading). */
    DENSE,
    /** MXFP8 stays FP8-resident; weight-only Marlin kernels with bfloat16 activations (Ampere and newer). */
    MARLIN,
    /** MXFP8 converted to 128x128 block FP8; W8A8 block-scaled tensor-core GEMM (Hopper and newer). */
    FP8,
    /** Same storage and arithmetic as {@link #FP8} but the GEMM is emulated, so it runs on any CUDA GPU (tests). */
    FP8_EMULATED;

    /**
     * Returns whether weights stay in MXFP8-derived form on the device.
     * @return {@code false} for {@link #DENSE}.
     */
    public boolean quantized() {
        return this != DENSE;
    }

    /**
     * Returns whether this mode uses the block-scaled FP8 GEMM (native or emulated).
     * @return {@code true} for {@link #FP8} and {@link #FP8_EMULATED}.
     */
    public boolean blockFp8() {
        return this == FP8 || this == FP8_EMULATED;
    }

    /**
     * Creates a bias-free linear layer in this mode. Quantized layers are empty until their weight is
     * installed from the checkpoint and own no module parameters.
     *
     * @param in  input features.
     * @param out output features.
     * @return the layer.
     */
    LinearOp newLinear(int in, int out) {
        return switch (this) {
            case DENSE -> new LinearLayer(in, out, false);
            case MARLIN -> new MxFp8MarlinLinear(in, out);
            case FP8 -> new MxFp8BlockLinear(in, out, false);
            case FP8_EMULATED -> new MxFp8BlockLinear(in, out, true);
        };
    }
}
