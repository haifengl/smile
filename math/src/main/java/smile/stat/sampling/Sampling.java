/*******************************************************************************
 * Copyright (c) 2010-2020 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 ******************************************************************************/

package smile.stat.sampling;

import java.util.Arrays;
import java.util.stream.Collectors;
import smile.math.MathEx;

/**
 * Random sampling.
 *
 * @author Haifeng Li
 */
public interface Sampling {
    /**
     * Random sampling. All samples have an equal probability of being selected.
     *
     * @param n the size of samples.
     * @param subsample sampling rate. Draw samples with replacement if it is 1.0.
     */
    static int[] random(int n, double subsample) {
        if (subsample == 1.0) {
            // draw with replacement.
            int[] samples = new int[n];
            for (int i = 0; i < n; i++) {
                samples[i] = MathEx.randomInt(n);
            }
            return samples;
        } else {
            // draw without replacement.
            int size = (int) Math.round(subsample * n);
            int[] samples = MathEx.permutate(n);
            return Arrays.copyOf(samples, size);
        }
    }

    /**
     * Stratified sampling. When the population embraces a number of
     * distinct categories, the frame can be organized by these categories
     * into separate strata. Each stratum is then sampled as an independent
     * sub-population, out of which individual elements can be randomly selected.
     *
     * @param y strata labels in [0, k).
     * @param subsample sampling rate. Draw samples with replacement if it is 1.0.
     */
    static int[] strateified(int[] y, double subsample) {
        int k = MathEx.max(y);
        int n = y.length;

        // # of samples in each strata
        int[] count = new int[k];
        for (int i = 0; i < n; i++) {
            count[y[i]]++;
        }
        // samples in each strata
        int[][] yi = new int[k][];
        for (int i = 0; i < k; i++) {
            yi[i] = new int[count[i]];
        }
        int[] idx = new int[k];
        for (int i = 0; i < n; i++) {
            int j = y[i];
            yi[j][idx[j]++] = i;
        }

        if (subsample == 1.0) {
            // draw with replacement.
            int[] samples = new int[n];
            int l = 0;
            for (int i = 0; i < k; i++) {
                int ni = count[i];
                int[] yj = yi[i];
                for (int j = 0; j < ni; j++) {
                    samples[l++] = yj[MathEx.randomInt(ni)];
                }
            }
            return samples;
        } else {
            // draw without replacement.
            int size = 0;
            for (int i = 0; i < k; i++) {
                size += (int) Math.round(subsample * count[i]);
            }

            int[] samples = new int[size];
            int l = 0;
            for (int i = 0; i < k; i++) {
                int ni = (int) Math.round(subsample * count[i]);
                int[] yj = yi[i];
                int[] permutation = MathEx.permutate(count[i]);
                for (int j = 0; j < ni; j++) {
                    samples[l++] = yj[permutation[j]];
                }
            }
            return samples;
        }
    }
}
