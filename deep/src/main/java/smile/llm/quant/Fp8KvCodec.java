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

/**
 * Helpers for FP8 KV store/load with per-tensor scales.
 *
 * <p>Used by {@link smile.llm.cache.KvCachePool} when
 * {@code smile.chat.kv-cache.dtype} is FP8. Ampere stores FP8 and dequants to
 * BF16/FP16 for attention compute; Hopper may later use native FlashInfer FP8.
 *
 * @author Haifeng Li
 */
public final class Fp8KvCodec {
    /** Max finite magnitude for Float8 e4m3fn. */
    public static final float E4M3_MAX = 448.0f;

    private Fp8KvCodec() {}

    public static boolean isFp8(ScalarType dtype) {
        return dtype == ScalarType.Float8e4m3fn
                || dtype == ScalarType.Float8e5m2
                || dtype == ScalarType.Float8e4m3fnuz
                || dtype == ScalarType.Float8e5m2fnuz;
    }

    /**
     * Computes a positive float scale so {@code abs(x)/scale <= fp8Max}.
     */
    public static float computeScale(Tensor x, float fp8Max) {
        Tensor x32 = x.to(ScalarType.Float);
        Tensor abs = x32.abs();
        Tensor mx = abs.max();
        float amax = mx.to(Device.CPU()).floatArray()[0];
        mx.close();
        abs.close();
        x32.close();
        return Math.max(amax, 1e-12f) / fp8Max;
    }

    /**
     * Quantizes {@code x} to FP8 using {@code scale} (x_fp8 ≈ x / scale).
     */
    public static Tensor quantize(Tensor x, float scale, ScalarType fp8Dtype) {
        Tensor x32 = x.to(ScalarType.Float);
        Tensor scaled = x32.div(scale);
        x32.close();
        Tensor q = scaled.to(fp8Dtype);
        scaled.close();
        return q;
    }

    /**
     * Dequantizes FP8 {@code q} with {@code scale} to {@code outDtype}.
     */
    public static Tensor dequantize(Tensor q, float scale, ScalarType outDtype) {
        Tensor q32 = q.to(ScalarType.Float);
        Tensor scaled = q32.mul(scale);
        q32.close();
        Tensor out = scaled.to(outDtype);
        scaled.close();
        return out;
    }
}
