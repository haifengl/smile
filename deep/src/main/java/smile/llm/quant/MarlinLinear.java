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

import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
import smile.torch.Native;

/**
 * Marlin FP16×INT4 linear layer (Ampere/Ada failover for GPTQ/AWQ).
 *
 * @author Haifeng Li
 */
public final class MarlinLinear implements LinearOp, AutoCloseable {
    private final Tensor qweight;  // Marlin-packed INT4
    private final Tensor scales;   // FP16 group scales
    private final Tensor bias;     // optional FP16
    private final Tensor workspace;
    private final int inFeatures;
    private final int outFeatures;
    private final int groupSize;

    /**
     * @param qweight     Marlin-packed weights.
     * @param scales      group scales.
     * @param bias        optional bias, or {@code null}.
     * @param inFeatures  input features (K).
     * @param outFeatures output features (N).
     * @param groupSize   quantization group size (typically 128).
     */
    public MarlinLinear(Tensor qweight, Tensor scales, Tensor bias,
                        int inFeatures, int outFeatures, int groupSize) {
        if (qweight == null || scales == null) {
            throw new IllegalArgumentException("qweight and scales required");
        }
        if (!Native.marlinAvailable()) {
            throw new IllegalStateException(
                    "Marlin is not compiled into libsmile_torch (USE_MARLIN). "
                            + "Rebuild the CUDA library for Ampere/Ada INT4 failover.");
        }
        this.qweight = qweight;
        this.scales = scales;
        this.bias = bias;
        this.inFeatures = inFeatures;
        this.outFeatures = outFeatures;
        this.groupSize = groupSize;
        // Small scratch; Marlin needs at least n/128 * max_par entries.
        this.workspace = Tensor.zeros(
                new Tensor.Options().dtype(ScalarType.Int32).device(qweight.device()),
                Math.max(outFeatures / 128L * 16L, 1L));
    }

    public int inFeatures() { return inFeatures; }
    public int outFeatures() { return outFeatures; }
    public int groupSize() { return groupSize; }

    @Override
    public Tensor forward(Tensor input) {
        long[] inShape = input.shape();
        long k = inShape[inShape.length - 1];
        if (k != inFeatures) {
            throw new IllegalArgumentException(
                    "input last dim " + k + " != inFeatures " + inFeatures);
        }
        long m = 1;
        for (int i = 0; i < inShape.length - 1; i++) {
            m *= inShape[i];
        }
        ScalarType inDtype = input.dtype();
        Tensor flat = input.reshape(m, k).contiguous();
        Tensor aFp16 = flat.dtype() == ScalarType.Half
                ? flat
                : flat.to(ScalarType.Half);
        // Marlin locks in workspace must be zero before each launch (C++ also zeros).
        workspace.fill_(0);
        Tensor outFlat;
        try {
            outFlat = Native.marlinMul(aFp16, qweight, scales, workspace, -1);
        } catch (RuntimeException e) {
            throw new IllegalStateException(
                    "smile_marlin_mul failed: " + e.getMessage()
                            + " (m=" + m + " k=" + k + " n=" + outFeatures
                            + " groupSize=" + groupSize
                            + " qweight=" + java.util.Arrays.toString(qweight.shape())
                            + " scales=" + java.util.Arrays.toString(scales.shape())
                            + " aDtype=" + aFp16.dtype()
                            + " qwDtype=" + qweight.dtype()
                            + " sDtype=" + scales.dtype() + ")",
                    e);
        } finally {
            if (aFp16 != flat) {
                aFp16.close();
            }
        }
        // Match the rest of the model (often BF16).
        if (inDtype != ScalarType.Half && outFlat.dtype() != inDtype) {
            Tensor cast = outFlat.to(inDtype);
            outFlat.close();
            outFlat = cast;
        }
        long[] outShape = new long[inShape.length];
        System.arraycopy(inShape, 0, outShape, 0, inShape.length - 1);
        outShape[outShape.length - 1] = outFeatures;
        Tensor out = outFlat.reshape(outShape);
        if (out != outFlat) {
            outFlat.close();
        }
        return out;
    }

    @Override
    public void close() {
        qweight.close();
        scales.close();
        workspace.close();
        if (bias != null) {
            bias.close();
        }
    }
}
