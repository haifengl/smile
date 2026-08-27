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

import smile.deep.tensor.Device;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
import smile.torch.Native;


/**
 * Weight-only FP8 linear layer using cuBLASLt via LibTorch {@code _scaled_mm}.
 *
 * <p>Holds Float8_e4m3fn weights in {@code [N,K]} layout expected by
 * {@code at::_scaled_mm}, plus a float weight scale. Activations are cast to
 * FP8 with a dynamic (or fixed) scale; output is BF16 or FP16.
 *
 * @author Haifeng Li
 */
public final class Fp8Linear implements LinearOp, AutoCloseable {
    private final Tensor weight;       // FP8 [out, in]
    private final Tensor weightScale;  // float scalar or [1]
    private final Tensor bias;         // optional BF16/FP16
    private final ScalarType outDtype;
    private final int inFeatures;
    private final int outFeatures;

    /**
     * @param weight      FP8 weight {@code [outFeatures, inFeatures]}.
     * @param weightScale float scale for {@code weight}.
     * @param bias        optional bias (same compute dtype), or {@code null}.
     * @param outDtype    output dtype ({@link ScalarType#BFloat16} or {@link ScalarType#Half}).
     */
    public Fp8Linear(Tensor weight, Tensor weightScale, Tensor bias, ScalarType outDtype) {
        if (weight == null || weightScale == null) {
            throw new IllegalArgumentException("weight and weightScale required");
        }
        long[] shape = weight.shape();
        if (shape.length != 2) {
            throw new IllegalArgumentException("weight must be 2D [out,in]");
        }
        this.outFeatures = (int) shape[0];
        this.inFeatures = (int) shape[1];
        this.weight = weight;
        this.weightScale = weightScale;
        this.bias = bias;
        this.outDtype = outDtype == null ? ScalarType.BFloat16 : outDtype;
    }

    public int inFeatures() { return inFeatures; }
    public int outFeatures() { return outFeatures; }
    public Tensor weight() { return weight; }
    public Tensor weightScale() { return weightScale; }

    @Override
    public Tensor forward(Tensor input) {
        // Flatten leading dims to [M,K]
        long[] inShape = input.shape();
        if (inShape.length < 1) {
            throw new IllegalArgumentException("input must be at least 1D");
        }
        long k = inShape[inShape.length - 1];
        if (k != inFeatures) {
            throw new IllegalArgumentException(
                    "input last dim " + k + " != inFeatures " + inFeatures);
        }
        long m = 1;
        for (int i = 0; i < inShape.length - 1; i++) {
            m *= inShape[i];
        }

        Tensor flat = input.reshape(m, k);
        // Dynamic activation scale: amax / fp8 max (~448 for e4m3)
        Tensor aFp32 = flat.to(ScalarType.Float);
        Tensor aAbs = aFp32.abs();
        Tensor aMax = aAbs.max();
        float amax = aMax.to(Device.CPU()).floatArray()[0];
        aAbs.close();
        aMax.close();
        float scale = Math.max(amax, 1e-12f) / 448.0f;
        Tensor scaleA = Tensor.of(new float[]{scale}).to(flat.device());
        Tensor aScaled = aFp32.div(scale);
        aFp32.close();
        Tensor aFp8 = aScaled.to(ScalarType.Float8e4m3fn);
        aScaled.close();

        int outCode = outDtype.code();
        Tensor outFlat = Native.scaledMm(aFp8, weight, scaleA, weightScale, outCode);
        aFp8.close();
        scaleA.close();
        if (outFlat == null) {
            throw new IllegalStateException(
                    "smile_scaled_mm unavailable; rebuild libsmile_torch with CUDA / LibTorch FP8 support");
        }
        if (bias != null) {
            Tensor withBias = outFlat.add(bias);
            outFlat.close();
            outFlat = withBias;
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
        weight.close();
        weightScale.close();
        if (bias != null) {
            bias.close();
        }
    }
}
