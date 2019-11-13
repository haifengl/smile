/*******************************************************************************
 * Copyright (c) 2010-2019 Haifeng Li
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
 *******************************************************************************/

package smile.sampling;

import java.util.Arrays;
import java.util.stream.Collectors;
import smile.classification.ClassLabels;
import smile.math.MathEx;

/**
 * Bagging (Bootstrap aggregating) is a way to improve the classification by
 * combining classifications of randomly generated training sets.
 *
 * @author Haifeng Li
 */
public class Bagging {

    /**
     * The samples.
     */
    public final int[] samples;

    /**
     * Constructor.
     * @param samples the samples.
     */
    public Bagging(int[] samples) {
        this.samples = samples;
    }

    @Override
    public String toString() {
        return Arrays.stream(samples).mapToObj(String::valueOf).collect(Collectors.joining(", ", "Bagging(", ")"));
    }

    /**
     * Random sampling.
     *
     * @param n the size of samples.
     * @param subsample sampling rate. Draw samples with replacement if it is 1.0.
     */
    public static Bagging random(int n, double subsample) {
        if (subsample == 1.0) {
            // draw with replacement.
            int[] samples = new int[n];
            for (int i = 0; i < n; i++) {
                samples[i] = MathEx.randomInt(n);
            }
            return new Bagging(samples);
        } else {
            // draw without replacement.
            int size = (int) Math.round(subsample * n);
            int[] samples = MathEx.permutate(n);
            return new Bagging(Arrays.copyOf(samples, size));
        }
    }

    /**
     * Stratified sampling.
     *
     * @param strata strata labels.
     * @param subsample sampling rate. Draw samples with replacement if it is 1.0.
     */
    public static Bagging strateify(int[] strata, double subsample) {
        ClassLabels codec = ClassLabels.fit(strata);
        int k = codec.k;
        int[] y = codec.y;

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
            return new Bagging(samples);
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
            return new Bagging(samples);
        }
    }
}
