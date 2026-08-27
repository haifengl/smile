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
 * <p>Phase-1 packer: dequantizes GPTQ/AWQ to FP16, then re-quantizes and
 * packs with the upstream {@code marlin.Layer.pack} tile permutation
 * ({@code B} shape {@code [k/16, n*16/8]}).
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
        requireMarlinGroupSize(groupSize);
        // GPTQ qweight is typically [inFeatures/8 packed, outFeatures] — dequant to FP16 then repack.
        Tensor fp16 = dequantGptqToFp16(qweight, scales, qzeros, gIdx, groupSize);
        try {
            return packFromFp16(fp16, groupSize, device);
        } finally {
            fp16.close();
        }
    }

    /**
     * Packs AutoAWQ GEMM tensors ({@code qweight} {@code [in, out/8]}, scales,
     * qzeros) into Marlin layout. Does not use the GPTQ {@code [in/8, out]} unpack.
     */
    public static Packed packAwq(Tensor qweight, Tensor scales, Tensor qzeros,
                                 int groupSize, Device device) {
        requireMarlinGroupSize(groupSize);
        Tensor fp16 = dequantAwqToFp16(qweight, scales, qzeros, groupSize);
        try {
            return packFromFp16(fp16, groupSize, device);
        } finally {
            fp16.close();
        }
    }

    private static void requireMarlinGroupSize(int groupSize) {
        // Vendored kernel instantiates group_blocks -1 (column-wise) and 8 (groupSize 128) only.
        if (groupSize != 128) {
            throw new IllegalArgumentException(
                    "Marlin kernel supports groupSize 128 only; got " + groupSize);
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
        int n = (int) shape[0]; // outFeatures
        int k = (int) shape[1]; // inFeatures
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
        int numGroups = k / groupSize;

        Tensor wCpu = weightFp16.to(Device.CPU()).to(ScalarType.Float);
        float[] data = wCpu.floatArray(); // [n, k] row-major
        wCpu.close();

        // Per-group absmax scales [groups, n], then quantize to unsigned int4 in [k, n] layout.
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
        int[] qKn = new int[k * n]; // [k, n], values 0..15
        for (int kj = 0; kj < k; kj++) {
            int g = kj / groupSize;
            for (int nj = 0; nj < n; nj++) {
                float scale = scaleData[g * n + nj];
                int qi = Math.round(data[nj * k + kj] / scale);
                qi = Math.max(-8, Math.min(7, qi)) + 8;
                qKn[kj * n + nj] = qi;
            }
        }

        // Tile reshape: (k/16, 16, n/16, 16) → permute(0,2,1,3) → (k/16, n*16)
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

        // Apply Marlin 1024-element pack permutation.
        int[] perm = MarlinPerms.MARLIN_PERM;
        int[] permuted = new int[tiled.length];
        int permBlocks = tiled.length / perm.length;
        for (int b = 0; b < permBlocks; b++) {
            int off = b * perm.length;
            for (int i = 0; i < perm.length; i++) {
                permuted[off + i] = tiled[off + perm[i]];
            }
        }

        // Pack 8 int4 values into int32: packed[r,j] |= permuted[r, j*8+i] << (4*i)
        int packedCols = tiledCols / 8; // n * 16 / 8 = 2*n
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

        // Scale permutation (same as marlin.Layer.pack).
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
        // Logical shape remains [numGroups, n].
        if (tmp.length != numGroups * n) {
            throw new IllegalStateException("scale permute size mismatch");
        }
        return tmp;
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

    /**
     * Dequantizes AutoAWQ GEMM tensors to dense FP16 {@code [out, in]}.
     *
     * <p>AWQ packs along the <em>output</em> dim: {@code qweight} is
     * {@code [inFeatures, outFeatures/8]}, {@code scales} is
     * {@code [inFeatures/groupSize, outFeatures]}, {@code qzeros} is
     * {@code [inFeatures/groupSize, outFeatures/8]}. Formula:
     * {@code (q - zero) * scale} (no GPTQ-style +1 on zeros).
     */
    private static Tensor dequantAwqToFp16(Tensor qweight, Tensor scales, Tensor qzeros, int groupSize) {
        if (qweight.dtype() == ScalarType.Half || qweight.dtype() == ScalarType.BFloat16
                || qweight.dtype() == ScalarType.Float) {
            return qweight.to(ScalarType.Half);
        }
        Tensor qw = qweight.to(Device.CPU());
        Tensor sc = scales.to(Device.CPU()).to(ScalarType.Float);
        long[] qshape = qw.shape();
        if (qshape.length != 2) {
            throw new IllegalArgumentException("AWQ qweight must be 2D [in, out/8]");
        }
        int inFeatures = (int) qshape[0];
        int packedOut = (int) qshape[1];
        int outFeatures = packedOut * 8;
        if (inFeatures % groupSize != 0) {
            qw.close();
            sc.close();
            throw new IllegalArgumentException(
                    "AWQ inFeatures " + inFeatures + " not divisible by groupSize " + groupSize);
        }
        int numGroups = inFeatures / groupSize;

        long[] sshape = sc.shape();
        if (sshape.length != 2 || sshape[0] != numGroups || sshape[1] != outFeatures) {
            // Some exporters store [out, groups]; accept transpose.
            if (sshape.length == 2 && sshape[0] == outFeatures && sshape[1] == numGroups) {
                Tensor transposed = sc.transpose(0, 1).contiguous();
                sc.close();
                sc = transposed.to(ScalarType.Float);
            } else {
                qw.close();
                sc.close();
                throw new IllegalArgumentException(
                        "AWQ scales shape " + java.util.Arrays.toString(sshape)
                                + " incompatible with groups=" + numGroups
                                + " outFeatures=" + outFeatures);
            }
        }

        int[] packed = qw.intArray();
        float[] scaleArr = sc.floatArray();
        int[] zeroPacked = null;
        if (qzeros != null) {
            Tensor z = qzeros.to(Device.CPU());
            long[] zshape = z.shape();
            if (zshape.length != 2 || zshape[0] != numGroups || zshape[1] != packedOut) {
                z.close();
                qw.close();
                sc.close();
                throw new IllegalArgumentException(
                        "AWQ qzeros shape " + java.util.Arrays.toString(zshape)
                                + " expected [" + numGroups + ", " + packedOut + "]");
            }
            zeroPacked = z.intArray();
            z.close();
        }

        // Dense [out, in] for packFromFp16 / nn.Linear layout.
        float[] out = new float[outFeatures * inFeatures];
        for (int k = 0; k < inFeatures; k++) {
            int g = k / groupSize;
            for (int po = 0; po < packedOut; po++) {
                int word = packed[k * packedOut + po];
                int zword = zeroPacked != null ? zeroPacked[g * packedOut + po] : 0;
                for (int logical = 0; logical < 8; logical++) {
                    int bitSlot = AWQ_REVERSE_ORDER[logical];
                    int qi = (word >>> (4 * bitSlot)) & 0xF;
                    int zi = zeroPacked != null ? (zword >>> (4 * bitSlot)) & 0xF : 0;
                    int o = po * 8 + logical;
                    float scale = scaleArr[g * outFeatures + o];
                    out[o * inFeatures + k] = (qi - zi) * scale;
                }
            }
        }
        qw.close();
        sc.close();
        return Tensor.of(out).reshape(outFeatures, inFeatures).to(ScalarType.Half);
    }
}
