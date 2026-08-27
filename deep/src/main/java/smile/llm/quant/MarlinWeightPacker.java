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
 * <p>Default HF load path uses {@link #packAwqDirect} / {@link #packGptqDirect}
 * (dequant → optional RoPE permute → Marlin requant). {@link #packFromFp16}
 * remains for tests and dense requant.
 *
 * @author Haifeng Li
 */
public final class MarlinWeightPacker {
    /**
     * AutoAWQ GEMM nibble interleave: logical column {@code j % 8} is stored at
     * bit position {@code 4 * AWQ_REVERSE_ORDER[j % 8]} within each packed int32.
     * Inverse of pack order {@code [0, 2, 4, 6, 1, 3, 5, 7]}.
     */
    private static final int[] AWQ_REVERSE_ORDER = {0, 4, 1, 5, 2, 6, 3, 7};

    private static final int TILE = 16;

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
     * Packs GPTQ tensors into Marlin layout (FP16 dequant→requant; act-order rejected).
     */
    public static Packed packGptq(Tensor qweight, Tensor scales, Tensor qzeros, Tensor gIdx,
                                  int groupSize, Device device) {
        return packGptqDirect(qweight, scales, qzeros, gIdx, groupSize, device, null);
    }

    /**
     * Packs AutoAWQ GEMM tensors into Marlin layout.
     *
     * <p>Dequantizes with AWQ zeros (exact), then requants into Marlin's
     * symmetric int4 (zp=8). Folding zeros into int4 with clamp is unsafe when
     * {@code |q - z| > 7}.
     */
    public static Packed packAwq(Tensor qweight, Tensor scales, Tensor qzeros,
                                 int groupSize, Device device) {
        return packAwqDirect(qweight, scales, qzeros, groupSize, device, null);
    }

    /**
     * AWQ→Marlin pack with optional HF→Meta RoPE rearrange on out-features.
     *
     * @param ropeHeads if non-null, permute {@code [out,in]} for Meta-style RoPE
     *                  ({@code num_attention_heads} or {@code num_kv_heads}).
     */
    public static Packed packAwqDirect(Tensor qweight, Tensor scales, Tensor qzeros,
                                       int groupSize, Device device, Integer ropeHeads) {
        requireMarlinGroupSize(groupSize);
        Tensor fp16;
        if (qweight.dtype() == ScalarType.Half || qweight.dtype() == ScalarType.BFloat16
                || qweight.dtype() == ScalarType.Float) {
            fp16 = qweight.to(ScalarType.Half);
        } else {
            fp16 = dequantAwqToFp16(qweight, scales, qzeros, groupSize);
        }
        try {
            if (ropeHeads != null) {
                Tensor perm = reversePermuteHfToMeta(fp16, ropeHeads);
                fp16.close();
                fp16 = perm;
            }
            return packFromFp16(fp16, groupSize, device);
        } finally {
            fp16.close();
        }
    }

    /** @see #packAwqDirect(Tensor, Tensor, Tensor, int, Device, Integer) */
    public static Packed packAwqDirect(Tensor qweight, Tensor scales, Tensor qzeros,
                                       int groupSize, Device device) {
        return packAwqDirect(qweight, scales, qzeros, groupSize, device, null);
    }

    /**
     * HuggingFace interleaved Q/K layout → Meta / GPT-NeoX layout used by Smile RoPE.
     * Same transform as dense {@code Llama.reversePermute} on {@code [out, in]} weights.
     */
    public static Tensor reversePermuteHfToMeta(Tensor w, int nHeads) {
        if (nHeads < 1) {
            throw new IllegalArgumentException("nHeads must be >= 1");
        }
        long[] shape = w.shape();
        if (shape.length != 2) {
            throw new IllegalArgumentException("weight must be 2D [out, in]");
        }
        long out = shape[0];
        long in = shape[1];
        if (out % nHeads != 0) {
            throw new IllegalArgumentException(
                    "outFeatures " + out + " not divisible by nHeads " + nHeads);
        }
        long headDim = out / nHeads;
        if (headDim % 2 != 0) {
            throw new IllegalArgumentException(
                    "headDim must be even for RoPE permute; got " + headDim);
        }
        try (Tensor viewed = w.view(nHeads, 2, headDim / 2, in);
             Tensor transposed = viewed.transpose(1, 2);
             Tensor cont = transposed.contiguous();
             Tensor reshaped = cont.reshape(out, in)) {
            return reshaped.copy();
        }
    }

    /**
     * Direct GPTQ→Marlin pack. Act-order ({@code g_idx} not sequential) fails fast.
     *
     * <p>Dequantizes (with zeros when present), optionally applies HF→Meta RoPE
     * rearrange, then requants into Marlin symmetric int4.
     *
     * @param ropeHeads if non-null, permute out-features for Meta-style RoPE.
     */
    public static Packed packGptqDirect(Tensor qweight, Tensor scales, Tensor qzeros, Tensor gIdx,
                                        int groupSize, Device device, Integer ropeHeads) {
        requireMarlinGroupSize(groupSize);
        Tensor fp16;
        if (qweight.dtype() == ScalarType.Half || qweight.dtype() == ScalarType.BFloat16
                || qweight.dtype() == ScalarType.Float) {
            fp16 = qweight.to(ScalarType.Half);
        } else {
            long[] qshape = qweight.shape();
            int k = (int) qshape[0] * 8;
            requireNoActOrder(gIdx, k, groupSize);
            fp16 = dequantGptqToFp16(qweight, scales, qzeros, gIdx, groupSize);
        }
        try {
            if (ropeHeads != null) {
                Tensor perm = reversePermuteHfToMeta(fp16, ropeHeads);
                fp16.close();
                fp16 = perm;
            }
            return packFromFp16(fp16, groupSize, device);
        } finally {
            fp16.close();
        }
    }

    /** @see #packGptqDirect(Tensor, Tensor, Tensor, Tensor, int, Device, Integer) */
    public static Packed packGptqDirect(Tensor qweight, Tensor scales, Tensor qzeros, Tensor gIdx,
                                        int groupSize, Device device) {
        return packGptqDirect(qweight, scales, qzeros, gIdx, groupSize, device, null);
    }

    private static void requireMarlinGroupSize(int groupSize) {
        if (groupSize != 128) {
            throw new IllegalArgumentException(
                    "Marlin kernel supports groupSize 128 only; got " + groupSize);
        }
    }

    private static void requireMarlinDims(int k, int n, int groupSize) {
        if (k % 128 != 0) {
            throw new IllegalArgumentException("Marlin requires inFeatures divisible by 128; got " + k);
        }
        if (n % 256 != 0) {
            throw new IllegalArgumentException("Marlin requires outFeatures divisible by 256; got " + n);
        }
        if (groupSize != 128 && groupSize != k) {
            throw new IllegalArgumentException(
                    "Marlin pack supports groupSize 128 or inFeatures (column-wise); got " + groupSize);
        }
        if (k % groupSize != 0) {
            throw new IllegalArgumentException(
                    "inFeatures " + k + " not divisible by groupSize " + groupSize);
        }
    }

    /**
     * Rejects GPTQ act-order checkpoints ({@code g_idx} not {@code i / groupSize}).
     */
    static void requireNoActOrder(Tensor gIdx, int k, int groupSize) {
        if (gIdx == null) {
            return;
        }
        Tensor idx = gIdx.to(Device.CPU());
        try {
            long[] shape = idx.shape();
            long numel = 1;
            for (long d : shape) {
                numel *= d;
            }
            if (numel != k) {
                throw new IllegalArgumentException(
                        "GPTQ g_idx length " + numel + " != inFeatures " + k);
            }
            int[] data = idx.intArray();
            for (int i = 0; i < data.length; i++) {
                if (data[i] != i / groupSize) {
                    throw new IllegalArgumentException(
                            "GPTQ act-order (non-sequential g_idx) is not supported for Marlin pack; "
                                    + "use a non-act-order GPTQ checkpoint or dense weights.");
                }
            }
        } finally {
            idx.close();
        }
    }

    private static float[] normalizeScales(Tensor sc, int numGroups, int n) {
        long[] sshape = sc.shape();
        float[] scaleArr = sc.floatArray();
        if (sshape.length == 2 && sshape[0] == numGroups && sshape[1] == n) {
            return scaleArr;
        }
        if (sshape.length == 2 && sshape[0] == n && sshape[1] == numGroups) {
            float[] transposed = new float[numGroups * n];
            for (int g = 0; g < numGroups; g++) {
                for (int nj = 0; nj < n; nj++) {
                    transposed[g * n + nj] = scaleArr[nj * numGroups + g];
                }
            }
            return transposed;
        }
        if (scaleArr.length == numGroups * n) {
            return scaleArr;
        }
        throw new IllegalArgumentException(
                "scales shape " + java.util.Arrays.toString(sshape)
                        + " incompatible with groups=" + numGroups + " outFeatures=" + n);
    }

    private static int[] readAwqZeros(Tensor qzeros, int numGroups, int packedOut) {
        if (qzeros == null) {
            return null;
        }
        Tensor z = qzeros.to(Device.CPU());
        try {
            long[] zshape = z.shape();
            if (zshape.length != 2 || zshape[0] != numGroups || zshape[1] != packedOut) {
                throw new IllegalArgumentException(
                        "AWQ qzeros shape " + java.util.Arrays.toString(zshape)
                                + " expected [" + numGroups + ", " + packedOut + "]");
            }
            return z.intArray();
        } finally {
            z.close();
        }
    }

    private static int[] readGptqZeros(Tensor qzeros, int numGroups, int n) {
        if (qzeros == null) {
            return null;
        }
        Tensor z = qzeros.to(Device.CPU());
        try {
            long[] zshape = z.shape();
            int packedOut = n / 8;
            if (zshape.length == 2 && zshape[0] == numGroups && zshape[1] == packedOut) {
                return z.intArray();
            }
            if (zshape.length == 2 && zshape[0] == numGroups && zshape[1] == n) {
                // Unpacked zeros — pack along out for a uniform path.
                float[] asFloat = z.dtype() == ScalarType.Float || z.dtype() == ScalarType.Half
                        || z.dtype() == ScalarType.BFloat16
                        ? z.to(ScalarType.Float).floatArray()
                        : null;
                int[] unpacked = asFloat == null ? z.intArray() : null;
                int[] packed = new int[numGroups * packedOut];
                for (int g = 0; g < numGroups; g++) {
                    for (int po = 0; po < packedOut; po++) {
                        int word = 0;
                        for (int j = 0; j < 8; j++) {
                            int zi;
                            if (asFloat != null) {
                                zi = Math.round(asFloat[g * n + po * 8 + j]);
                            } else {
                                zi = unpacked[g * n + po * 8 + j];
                            }
                            word |= (zi & 0xF) << (4 * j);
                        }
                        packed[g * packedOut + po] = word;
                    }
                }
                return packed;
            }
            throw new IllegalArgumentException(
                    "GPTQ qzeros shape " + java.util.Arrays.toString(zshape)
                            + " expected [" + numGroups + ", " + packedOut + "] or ["
                            + numGroups + ", " + n + "]");
        } finally {
            z.close();
        }
    }

    /**
     * Packs a dense FP16 weight {@code [out, in]} into Marlin INT4 layout
     * matching upstream {@code marlin.Layer.pack} ({@code B} is {@code [k/16, n*16/8]}).
     */
    public static Packed packFromFp16(Tensor weightFp16, int groupSize, Device device) {
        long[] shape = weightFp16.shape();
        if (shape.length != 2) {
            throw new IllegalArgumentException("weight must be [out,in]");
        }
        int n = (int) shape[0];
        int k = (int) shape[1];
        requireMarlinDims(k, n, groupSize);
        int numGroups = k / groupSize;

        Tensor wCpu = weightFp16.to(Device.CPU()).to(ScalarType.Float);
        float[] data = wCpu.floatArray();
        wCpu.close();

        float[] scaleData = new float[numGroups * n];
        for (int g = 0; g < numGroups; g++) {
            for (int nj = 0; nj < n; nj++) {
                float amax = 0f;
                int rowBase = nj * k + g * groupSize;
                for (int i = 0; i < groupSize; i++) {
                    amax = Math.max(amax, Math.abs(data[rowBase + i]));
                }
                scaleData[g * n + nj] = Math.max(amax / 7.0f, 1e-8f);
            }
        }
        int[] qKn = new int[k * n];
        for (int kj = 0; kj < k; kj++) {
            int g = kj / groupSize;
            for (int nj = 0; nj < n; nj++) {
                float scale = scaleData[g * n + nj];
                int qi = Math.round(data[nj * k + kj] / scale);
                qi = Math.max(-8, Math.min(7, qi)) + 8;
                qKn[kj * n + nj] = qi;
            }
        }
        return packInt4Marlin(qKn, scaleData, k, n, groupSize, device);
    }

    /**
     * Marlin tile permute + int32 pack from unsigned int4 {@code qKn[k*n]} and
     * scales {@code [groups, n]}.
     */
    static Packed packInt4Marlin(int[] qKn, float[] scaleData, int k, int n, int groupSize,
                                 Device device) {
        int numGroups = k / groupSize;
        if (qKn.length != k * n) {
            throw new IllegalArgumentException("qKn length mismatch");
        }
        if (scaleData.length != numGroups * n) {
            throw new IllegalArgumentException("scaleData length mismatch");
        }

        int rows = k / TILE;
        int nTiles = n / TILE;
        int tiledCols = n * TILE;
        int[] tiled = new int[rows * tiledCols];
        for (int k0 = 0; k0 < rows; k0++) {
            for (int n0 = 0; n0 < nTiles; n0++) {
                for (int kt = 0; kt < TILE; kt++) {
                    for (int nt = 0; nt < TILE; nt++) {
                        int src = (k0 * TILE + kt) * n + (n0 * TILE + nt);
                        int dst = k0 * tiledCols + n0 * (TILE * TILE) + kt * TILE + nt;
                        tiled[dst] = qKn[src];
                    }
                }
            }
        }

        int[] perm = MarlinPerms.MARLIN_PERM;
        int[] permuted = new int[tiled.length];
        int permBlocks = tiled.length / perm.length;
        for (int b = 0; b < permBlocks; b++) {
            int off = b * perm.length;
            for (int i = 0; i < perm.length; i++) {
                permuted[off + i] = tiled[off + perm[i]];
            }
        }

        int packedCols = tiledCols / 8;
        int[] packed = new int[rows * packedCols];
        for (int r = 0; r < rows; r++) {
            int rowOff = r * tiledCols;
            int packOff = r * packedCols;
            for (int c = 0; c < packedCols; c++) {
                int word = 0;
                int srcBase = rowOff + c * 8;
                for (int i = 0; i < 8; i++) {
                    word |= (permuted[srcBase + i] & 0xF) << (4 * i);
                }
                packed[packOff + c] = word;
            }
        }

        float[] scaleOut = permuteScales(scaleData, numGroups, n, groupSize == k);
        Tensor qweight = Tensor.of(packed).reshape(rows, packedCols).to(device);
        Tensor scalesOut = Tensor.of(scaleOut).reshape(numGroups, n)
                .to(ScalarType.Half).to(device);
        return new Packed(qweight, scalesOut, k, n, groupSize);
    }

    private static float[] permuteScales(float[] scaleData, int numGroups, int n, boolean columnWise) {
        int[] scalePerm = columnWise ? MarlinPerms.MARLIN_SCALE_PERM_SINGLE : MarlinPerms.MARLIN_SCALE_PERM;
        if (scaleData.length % scalePerm.length != 0) {
            throw new IllegalStateException(
                    "scale length " + scaleData.length + " not divisible by " + scalePerm.length);
        }
        float[] tmp = new float[scaleData.length];
        int nrows = scaleData.length / scalePerm.length;
        for (int r = 0; r < nrows; r++) {
            int off = r * scalePerm.length;
            for (int i = 0; i < scalePerm.length; i++) {
                tmp[off + i] = scaleData[off + scalePerm[i]];
            }
        }
        if (tmp.length != numGroups * n) {
            throw new IllegalStateException("scale permute size mismatch");
        }
        return tmp;
    }

    private static Tensor dequantGptqToFp16(Tensor qweight, Tensor scales, Tensor qzeros,
                                           Tensor gIdx, int groupSize) {
        if (qweight.dtype() == ScalarType.Half || qweight.dtype() == ScalarType.BFloat16
                || qweight.dtype() == ScalarType.Float) {
            return qweight.to(ScalarType.Half);
        }
        Tensor qw = qweight.to(Device.CPU());
        Tensor sc = scales.to(Device.CPU()).to(ScalarType.Float);
        try {
            long[] qshape = qw.shape();
            int packedIn = (int) qshape[0];
            int outFeatures = (int) qshape[qshape.length - 1];
            int inFeatures = packedIn * 8;
            requireNoActOrder(gIdx, inFeatures, groupSize);
            int numGroups = inFeatures / groupSize;
            float[] scaleArr = normalizeScales(sc, numGroups, outFeatures);
            int[] packed = qw.intArray();
            int[] zeroPacked = readGptqZeros(qzeros, numGroups, outFeatures);
            float[] out = new float[outFeatures * inFeatures];
            int packedOut = outFeatures / 8;
            for (int o = 0; o < outFeatures; o++) {
                for (int p = 0; p < packedIn; p++) {
                    int word = packed[p * outFeatures + o];
                    for (int j = 0; j < 8; j++) {
                        int kk = p * 8 + j;
                        int qi = (word >>> (4 * j)) & 0xF;
                        int zi = 8;
                        if (zeroPacked != null) {
                            int zword = zeroPacked[(kk / groupSize) * packedOut + o / 8];
                            zi = (zword >>> (4 * (o % 8))) & 0xF;
                        }
                        float scale = scaleArr[(kk / groupSize) * outFeatures + o];
                        out[o * inFeatures + kk] = (qi - zi) * scale;
                    }
                }
            }
            return Tensor.of(out).reshape(outFeatures, inFeatures).to(ScalarType.Half);
        } finally {
            qw.close();
            sc.close();
        }
    }

    private static Tensor dequantAwqToFp16(Tensor qweight, Tensor scales, Tensor qzeros, int groupSize) {
        if (qweight.dtype() == ScalarType.Half || qweight.dtype() == ScalarType.BFloat16
                || qweight.dtype() == ScalarType.Float) {
            return qweight.to(ScalarType.Half);
        }
        Tensor qw = qweight.to(Device.CPU());
        Tensor sc = scales.to(Device.CPU()).to(ScalarType.Float);
        try {
            long[] qshape = qw.shape();
            int inFeatures = (int) qshape[0];
            int packedOut = (int) qshape[1];
            int outFeatures = packedOut * 8;
            int numGroups = inFeatures / groupSize;
            float[] scaleArr = normalizeScales(sc, numGroups, outFeatures);
            int[] packed = qw.intArray();
            int[] zeroPacked = readAwqZeros(qzeros, numGroups, packedOut);
            float[] out = new float[outFeatures * inFeatures];
            for (int kk = 0; kk < inFeatures; kk++) {
                int g = kk / groupSize;
                for (int po = 0; po < packedOut; po++) {
                    int word = packed[kk * packedOut + po];
                    int zword = zeroPacked != null ? zeroPacked[g * packedOut + po] : 0;
                    for (int logical = 0; logical < 8; logical++) {
                        int bitSlot = AWQ_REVERSE_ORDER[logical];
                        int qi = (word >>> (4 * bitSlot)) & 0xF;
                        int zi = zeroPacked != null ? (zword >>> (4 * bitSlot)) & 0xF : 0;
                        int o = po * 8 + logical;
                        out[o * inFeatures + kk] = (qi - zi) * scaleArr[g * outFeatures + o];
                    }
                }
            }
            return Tensor.of(out).reshape(outFeatures, inFeatures).to(ScalarType.Half);
        } finally {
            qw.close();
            sc.close();
        }
    }
}
