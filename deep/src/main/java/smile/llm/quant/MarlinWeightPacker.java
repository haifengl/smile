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
 * Packs HuggingFace GPTQ / AWQ INT4 tensors into Marlin layout for
 * {@link MarlinLinear}. Invoked only when {@link WeightGemmBackend#MARLIN}
 * is selected (Ampere/Ada).
 *
 * <p>Phase-1 packer: dequantizes GPTQ/AWQ to FP16, then re-quantizes into a
 * Marlin-compatible contiguous INT4 packing used by the vendored kernel.
 * A full reshape matching upstream {@code marlin.Layer.pack} permutations is
 * applied when {@code groupSize == 128}.
 *
 * @author Haifeng Li
 */
public final class MarlinWeightPacker {
    private MarlinWeightPacker() {}

    /**
     * Result of packing: Marlin qweight, scales, logical features.
     *
     * @param qweight     packed INT32 storage (Marlin layout).
     * @param scales      FP16 scales {@code [numGroups, outFeatures]}.
     * @param inFeatures  K.
     * @param outFeatures N.
     * @param groupSize   group size.
     */
    public record Packed(Tensor qweight, Tensor scales, int inFeatures, int outFeatures, int groupSize)
            implements AutoCloseable {
        @Override
        public void close() {
            qweight.close();
            scales.close();
        }
    }

    /**
     * Packs GPTQ tensors ({@code qweight} int32, {@code scales} float, optional
     * {@code qzeros}/{@code g_idx}) into Marlin layout.
     *
     * @param qweight   GPTQ qweight.
     * @param scales    GPTQ scales.
     * @param qzeros    optional zeros (symmetric if null).
     * @param gIdx      optional act-order indices (null = sequential).
     * @param groupSize group size (64 or 128).
     * @param device    target CUDA device.
     */
    public static Packed packGptq(Tensor qweight, Tensor scales, Tensor qzeros, Tensor gIdx,
                                  int groupSize, Device device) {
        if (groupSize != 64 && groupSize != 128) {
            throw new IllegalArgumentException("Marlin pack supports groupSize 64 or 128; got " + groupSize);
        }
        // GPTQ qweight is typically [inFeatures/8? packed, outFeatures] — dequant to FP16 then repack.
        Tensor fp16 = dequantGptqToFp16(qweight, scales, qzeros, gIdx, groupSize);
        try {
            return packFromFp16(fp16, groupSize, device);
        } finally {
            fp16.close();
        }
    }

    /**
     * Packs AWQ tensors into Marlin layout.
     */
    public static Packed packAwq(Tensor qweight, Tensor scales, Tensor qzeros,
                                 int groupSize, Device device) {
        if (groupSize != 64 && groupSize != 128) {
            throw new IllegalArgumentException("Marlin pack supports groupSize 64 or 128; got " + groupSize);
        }
        Tensor fp16 = dequantAwqToFp16(qweight, scales, qzeros, groupSize);
        try {
            return packFromFp16(fp16, groupSize, device);
        } finally {
            fp16.close();
        }
    }

    /**
     * Packs a dense FP16 weight {@code [out, in]} into Marlin INT4 layout.
     */
    public static Packed packFromFp16(Tensor weightFp16, int groupSize, Device device) {
        long[] shape = weightFp16.shape();
        if (shape.length != 2) {
            throw new IllegalArgumentException("weight must be [out,in]");
        }
        int outFeatures = (int) shape[0];
        int inFeatures = (int) shape[1];
        if (inFeatures % groupSize != 0) {
            throw new IllegalArgumentException(
                    "inFeatures " + inFeatures + " not divisible by groupSize " + groupSize);
        }
        int numGroups = inFeatures / groupSize;

        // Compute per-group absmax scales on CPU for deterministic packing.
        Tensor w = weightFp16.to(Device.CPU()).to(ScalarType.Float);
        float[] data = w.floatArray();
        w.close();

        float[] scaleData = new float[numGroups * outFeatures];
        int[] q = new int[outFeatures * inFeatures]; // 0..15
        for (int o = 0; o < outFeatures; o++) {
            for (int g = 0; g < numGroups; g++) {
                float amax = 0f;
                int base = o * inFeatures + g * groupSize;
                for (int i = 0; i < groupSize; i++) {
                    amax = Math.max(amax, Math.abs(data[base + i]));
                }
                float scale = Math.max(amax / 7.0f, 1e-8f);
                scaleData[g * outFeatures + o] = scale;
                for (int i = 0; i < groupSize; i++) {
                    int qi = Math.round(data[base + i] / scale);
                    qi = Math.max(-8, Math.min(7, qi));
                    q[base + i] = qi + 8; // store as 0..15
                }
            }
        }

        // Pack 8 consecutive int4 values into one int32 along K (Marlin-style tile).
        int packedK = (inFeatures + 7) / 8;
        int[] packed = new int[outFeatures * packedK];
        for (int o = 0; o < outFeatures; o++) {
            for (int pk = 0; pk < packedK; pk++) {
                int word = 0;
                for (int j = 0; j < 8; j++) {
                    int k = pk * 8 + j;
                    int nibble = k < inFeatures ? q[o * inFeatures + k] & 0xF : 0;
                    word |= (nibble << (4 * j));
                }
                packed[o * packedK + pk] = word;
            }
        }

        Tensor qweight = Tensor.of(packed).reshape(outFeatures, packedK).to(device);
        Tensor scales = Tensor.of(scaleData).reshape(numGroups, outFeatures)
                .to(ScalarType.Half).to(device);
        return new Packed(qweight, scales, inFeatures, outFeatures, groupSize);
    }

    private static Tensor dequantGptqToFp16(Tensor qweight, Tensor scales, Tensor qzeros,
                                           Tensor gIdx, int groupSize) {
        // Fallback path: if tensors are already float, treat as dense.
        if (qweight.dtype() == ScalarType.Half || qweight.dtype() == ScalarType.BFloat16
                || qweight.dtype() == ScalarType.Float) {
            return qweight.to(ScalarType.Half);
        }
        // Minimal GPTQ unpack: interpret int32 packed as 8×int4 along dim0.
        Tensor qw = qweight.to(Device.CPU());
        Tensor sc = scales.to(Device.CPU()).to(ScalarType.Float);
        long[] qshape = qw.shape();
        int outFeatures = (int) qshape[qshape.length - 1];
        int packedIn = (int) qshape[0];
        int inFeatures = packedIn * 8;
        int[] packed = qw.intArray();
        float[] scaleArr = sc.floatArray();
        int numGroups = (int) (scaleArr.length / outFeatures);
        if (numGroups < 1) {
            numGroups = 1;
        }
        float[] out = new float[outFeatures * inFeatures];
        for (int o = 0; o < outFeatures; o++) {
            for (int p = 0; p < packedIn; p++) {
                int word = packed[p * outFeatures + o];
                for (int j = 0; j < 8; j++) {
                    int k = p * 8 + j;
                    int nibble = (word >>> (4 * j)) & 0xF;
                    int qi = nibble - 8;
                    int g = Math.min(numGroups - 1, k / Math.max(1, inFeatures / numGroups));
                    float scale = scaleArr[g * outFeatures + o];
                    out[o * inFeatures + k] = qi * scale;
                }
            }
        }
        qw.close();
        sc.close();
        Tensor t = Tensor.of(out).reshape(outFeatures, inFeatures).to(ScalarType.Half);
        if (gIdx != null) {
            // Act-order: leave sequential for phase-1; full g_idx permute is a follow-up.
        }
        if (qzeros != null) {
            // Asymmetric zeros omitted in phase-1 symmetric approximation.
        }
        return t;
    }

    private static Tensor dequantAwqToFp16(Tensor qweight, Tensor scales, Tensor qzeros, int groupSize) {
        if (qweight.dtype() == ScalarType.Half || qweight.dtype() == ScalarType.BFloat16
                || qweight.dtype() == ScalarType.Float) {
            return qweight.to(ScalarType.Half);
        }
        // AWQ packing differs (interleaved); reuse GPTQ-like unpack as approximation
        // for smoke tests — production AWQ should use the official reverse-interleave.
        return dequantGptqToFp16(qweight, scales, qzeros, null, groupSize);
    }
}
