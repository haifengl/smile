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
package smile.llm.model.qwen;

import java.util.Arrays;
import smile.deep.tensor.Index;
import smile.deep.tensor.Tensor;

/**
 * Interleaved multimodal RoPE for Qwen3.8 / Qwen3.5 VL text towers.
 *
 * <p>When T/H/W position planes are equal, the result matches classic 1D
 * partial RoPE ({@link PartialRotaryEncoding#computeCosSin}).
 *
 * @author Haifeng Li
 */
public final class InterleavedMRope {
    private InterleavedMRope() {}

    /**
     * Builds cos/sin for a sequence from three position planes.
     *
     * @param rotaryDim   even rotary feature count (e.g. 64).
     * @param theta       RoPE base.
     * @param mropeSection half-freq section widths summing to {@code rotaryDim/2}.
     * @param posT        temporal positions length {@code S}.
     * @param posH        height positions length {@code S}.
     * @param posW        width positions length {@code S}.
     * @return cos/sin of shape {@code [S, rotaryDim]} float32 (caller owns).
     */
    public static PartialRotaryEncoding.CosSin computeCosSin(
            int rotaryDim, double theta, int[] mropeSection,
            int[] posT, int[] posH, int[] posW) {
        if (rotaryDim <= 0 || (rotaryDim & 1) != 0) {
            throw new IllegalArgumentException("rotaryDim must be positive and even");
        }
        if (posT == null || posH == null || posW == null) {
            throw new IllegalArgumentException("position planes required");
        }
        int seq = posT.length;
        if (posH.length != seq || posW.length != seq) {
            throw new IllegalArgumentException("position plane lengths must match");
        }
        int half = rotaryDim / 2;
        int[] section = normalizeSection(mropeSection, half);

        // Equal planes → classic 1D table gather (bit-identical shortcut).
        if (Arrays.equals(posT, posH) && Arrays.equals(posT, posW)) {
            int maxPos = 0;
            for (int p : posT) {
                maxPos = Math.max(maxPos, p);
            }
            try (PartialRotaryEncoding.CosSin table =
                         PartialRotaryEncoding.computeCosSin(rotaryDim, maxPos + 1, theta);
                 var idx = Index.of(posT);
                 Tensor cosRows = table.cos().get(idx);
                 Tensor sinRows = table.sin().get(idx)) {
                Tensor cos = cosRows.copy();
                Tensor sin = sinRows.copy();
                cos.detachFromScopes();
                sin.detachFromScopes();
                return new PartialRotaryEncoding.CosSin(cos, sin);
            }
        }

        float[] invFreq = new float[half];
        for (int i = 0; i < half; i++) {
            invFreq[i] = (float) Math.exp(-Math.log(theta) * (2.0 * i) / rotaryDim);
        }

        float[] freqsT = new float[seq * half];
        float[] freqsH = new float[seq * half];
        float[] freqsW = new float[seq * half];
        for (int s = 0; s < seq; s++) {
            for (int i = 0; i < half; i++) {
                freqsT[s * half + i] = posT[s] * invFreq[i];
                freqsH[s * half + i] = posH[s] * invFreq[i];
                freqsW[s * half + i] = posW[s] * invFreq[i];
            }
        }

        // Interleave: start from T, overwrite H/W slots per section pattern.
        float[] mixed = Arrays.copyOf(freqsT, freqsT.length);
        int sH = section[1];
        int sW = section[2];
        for (int s = 0; s < seq; s++) {
            int base = s * half;
            for (int i = 1; i < sH * 3 && i < half; i += 3) {
                mixed[base + i] = freqsH[base + i];
            }
            for (int i = 2; i < sW * 3 && i < half; i += 3) {
                mixed[base + i] = freqsW[base + i];
            }
        }

        float[] cosData = new float[seq * rotaryDim];
        float[] sinData = new float[seq * rotaryDim];
        for (int s = 0; s < seq; s++) {
            for (int i = 0; i < half; i++) {
                float f = mixed[s * half + i];
                float c = (float) Math.cos(f);
                float sn = (float) Math.sin(f);
                // emb = cat(freqs, freqs) then cos/sin
                cosData[s * rotaryDim + i] = c;
                cosData[s * rotaryDim + half + i] = c;
                sinData[s * rotaryDim + i] = sn;
                sinData[s * rotaryDim + half + i] = sn;
            }
        }

        Tensor cos = Tensor.of(cosData, seq, rotaryDim);
        Tensor sin = Tensor.of(sinData, seq, rotaryDim);
        cos.detachFromScopes();
        sin.detachFromScopes();
        return new PartialRotaryEncoding.CosSin(cos, sin);
    }

    /**
     * Builds per-token mRoPE position planes for a multimodal prompt.
     *
     * @param mmTokenTypeIds 0=text, 1=image, 2=video; length {@code S}.
     * @param imageGridThw   {@code [nImg][3]} patch grids (T,H,W), or empty.
     * @param videoGridThw   {@code [nVid][3]} patch grids, or empty.
     * @param spatialMerge   spatial merge size.
     * @return planes and {@code rope_delta = max(pos)+1 - S}.
     */
    public static MropePositions getRopeIndex(
            int[] mmTokenTypeIds,
            int[][] imageGridThw,
            int[][] videoGridThw,
            int spatialMerge) {
        if (mmTokenTypeIds == null) {
            throw new IllegalArgumentException("mmTokenTypeIds required");
        }
        int seq = mmTokenTypeIds.length;
        int[] posT = new int[seq];
        int[] posH = new int[seq];
        int[] posW = new int[seq];
        int imgPtr = 0;
        int vidPtr = 0;
        int current = 0;
        int i = 0;
        while (i < seq) {
            int type = mmTokenTypeIds[i];
            int j = i + 1;
            while (j < seq && mmTokenTypeIds[j] == type) {
                j++;
            }
            int run = j - i;
            if (type == 0) {
                for (int k = 0; k < run; k++) {
                    int p = current + k;
                    posT[i + k] = p;
                    posH[i + k] = p;
                    posW[i + k] = p;
                }
                current += run;
            } else {
                int[] grid;
                if (type == 1) {
                    if (imageGridThw == null || imgPtr >= imageGridThw.length) {
                        throw new IllegalArgumentException("missing image_grid_thw");
                    }
                    grid = imageGridThw[imgPtr++];
                } else {
                    if (videoGridThw == null || vidPtr >= videoGridThw.length) {
                        throw new IllegalArgumentException("missing video_grid_thw");
                    }
                    grid = videoGridThw[vidPtr++];
                }
                int t = grid[0];
                int h = grid[1];
                int w = grid[2];
                int m = spatialMerge;
                int tPrime = t;
                int hPrime = h / m;
                int wPrime = w / m;
                int expected = tPrime * hPrime * wPrime;
                if (expected != run) {
                    throw new IllegalArgumentException(
                            "vision run length " + run + " != merged tokens " + expected);
                }
                int idx = 0;
                for (int tt = 0; tt < tPrime; tt++) {
                    for (int hh = 0; hh < hPrime; hh++) {
                        for (int ww = 0; ww < wPrime; ww++) {
                            posT[i + idx] = current + tt;
                            posH[i + idx] = current + hh;
                            posW[i + idx] = current + ww;
                            idx++;
                        }
                    }
                }
                current += Math.max(h, w) / m;
            }
            i = j;
        }
        int maxPos = 0;
        for (int p : posT) {
            maxPos = Math.max(maxPos, p);
        }
        for (int p : posH) {
            maxPos = Math.max(maxPos, p);
        }
        for (int p : posW) {
            maxPos = Math.max(maxPos, p);
        }
        int ropeDelta = maxPos + 1 - seq;
        return new MropePositions(posT, posH, posW, ropeDelta);
    }

    /**
     * mRoPE position planes for one sequence.
     *
     * @param t          temporal plane.
     * @param h          height plane.
     * @param w          width plane.
     * @param ropeDelta  {@code max(pos)+1 - seqLen}.
     */
    public record MropePositions(int[] t, int[] h, int[] w, int ropeDelta) {}

    static int[] normalizeSection(int[] section, int half) {
        if (section == null || section.length < 3) {
            int a = half / 3;
            int b = half / 3;
            return new int[]{a, b, half - a - b};
        }
        int sum = section[0] + section[1] + section[2];
        if (sum != half) {
            throw new IllegalArgumentException(
                    "mrope_section sum " + sum + " != rotaryDim/2 " + half);
        }
        return section;
    }
}
