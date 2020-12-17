/*
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
 */

package smile.stat;

import java.util.Arrays;
import smile.math.MathEx;
import smile.util.IntSet;

/**
 * Random sampling Sampling is the selection of a subset of individuals
 * from within a statistical population to estimate characteristics of
 * the whole population.
 *
 * @author Haifeng Li
 */
public interface Sampling {
    /**
     * Random sampling. All samples have an equal probability of being selected.
     *
     * @param n the size of samples.
     * @param subsample sampling rate. Draw samples with replacement if it is 1.0.
     * @return the indices of selected samples.
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
     * @param category the strata labels.
     * @param subsample sampling rate. Draw samples with replacement if it is 1.0.
     * @return the indices of selected samples.
     */
    static int[] strateified(int[] category, double subsample) {
        int[] unique = MathEx.unique(category);
        int m = unique.length;

        Arrays.sort(unique);
        IntSet encoder = new IntSet(unique);

        int n = category.length;
        int[] y = category;
        if (unique[0] != 0 || unique[m-1] != m-1) {
            y = new int[n];
            for (int i = 0; i < n; i++) {
                y[i] = encoder.indexOf(category[i]);
            }
        }

        // # of samples in each strata
        int[] ni = new int[m];
        for (int i : y) ni[i]++;

        int[][] strata = new int[m][];
        for (int i = 0; i < m; i++) {
            strata[i] = new int[ni[i]];
        }

        int[] pos = new int[m];
        for (int i = 0; i < n; i++) {
            int j =  y[i];
            strata[j][pos[j]++] = i;
        }

        if (subsample == 1.0) {
            // draw with replacement.
            int[] samples = new int[n];
            int l = 0;
            for (int i = 0; i < m; i++) {
                int[] stratum = strata[i];
                int size = ni[i];
                for (int j = 0; j < size; j++) {
                    samples[l++] = stratum[MathEx.randomInt(size)];
                }
            }
            return samples;
        } else {
            // draw without replacement.
            int size = 0;
            for (int i = 0; i < m; i++) {
                size += (int) Math.round(subsample * ni[i]);
            }

            int[] samples = new int[size];
            int l = 0;
            for (int i = 0; i < m; i++) {
                int sub = (int) Math.round(subsample * ni[i]);
                int[] stratum = strata[i];
                int[] permutation = MathEx.permutate(ni[i]);
                for (int j = 0; j < sub; j++) {
                    samples[l++] = stratum[permutation[j]];
                }
            }
            return samples;
        }
    }
}
