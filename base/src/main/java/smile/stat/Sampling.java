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
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.stat;

import java.util.Arrays;
import smile.math.MathEx;
import smile.util.IntSet;

/**
 * Random sampling is the selection of a subset of individuals
 * from within a statistical population to estimate characteristics of
 * the whole population.
 *
 * @author Haifeng Li
 */
public interface Sampling {
    /**
     * Simple random sampling. All samples have an equal probability
     * of being selected.
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
     * Returns the strata of samples as a two-dimensional
     * array. Each row is the sample indices of stratum.
     *
     * @param category the strata labels.
     * @return the strata of samples as a two-dimensional array.
     *         Each row is the sample indices of stratum.
     */
    static int[][] strata(int[] category) {
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

        // # of samples in each stratum
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

        return strata;
    }

    /**
     * Stratified sampling from a population which can be partitioned
     * into subpopulations. In statistical surveys, when subpopulations
     * within an overall population vary, it could be advantageous to
     * sample each subpopulation (stratum) independently.
     * <p>
     * Stratification is the process of dividing members of the population
     * into homogeneous subgroups before sampling. The strata should define
     * a partition of the population. That is, it should be collectively
     * exhaustive and mutually exclusive: every element in the population
     * must be assigned to one and only one stratum. Then simple random
     * sampling is applied within each stratum.
     *
     * @param category the strata labels.
     * @param subsample sampling rate. Draw samples with replacement if it is 1.0.
     * @return the indices of selected samples.
     */
    static int[] stratify(int[] category, double subsample) {
        int[][] strata = strata(category);

        int n = category.length;
        int m = strata.length;

        if (subsample == 1.0) {
            // draw with replacement.
            int[] samples = new int[n];
            int l = 0;
            for (var stratum : strata) {
                int size = stratum.length;
                for (int j = 0; j < size; j++) {
                    samples[l++] = stratum[MathEx.randomInt(size)];
                }
            }
            return samples;
        } else {
            // draw without replacement.
            int size = 0;
            for (var stratum : strata) {
                size += (int) Math.round(subsample * stratum.length);
            }

            int[] samples = new int[size];
            int l = 0;
            for (var stratum : strata) {
                int sub = (int) Math.round(subsample * stratum.length);
                int[] permutation = MathEx.permutate(stratum.length);
                for (int j = 0; j < sub; j++) {
                    samples[l++] = stratum[permutation[j]];
                }
            }
            return samples;
        }
    }

    /**
     * Latin hypercube sampling. LHS generates a near-random sample of
     * parameter values from a multidimensional distribution. The sampling
     * method is often used to construct Monte Carlo simulation.
     * <p>
     * A Latin hypercube is an n-by-d matrix, each column of which is a
     * permutation of 1, 2, ..., n. When sampling a function of d variables,
     * the range of each variable is divided into n equally probable intervals.
     * n sample points are then placed to satisfy the Latin hypercube
     * requirements; this forces the number of divisions, n, to be equal for
     * each variable.
     * <p>
     * This sampling scheme does not require more samples for more dimensions
     * (variables); this independence is one of the main advantages of this
     * sampling scheme. Another advantage is that random samples can be taken
     * one at a time, remembering which samples were taken so far.
     * <p>
     * Because the component samples are randomly paired, an LHS is not unique;
     * there are (d!)<sup>n-1</sup> possible combinations. With this in mind,
     * improved LHS algorithms iterate to determine optimal pairings according
     * to some specified criteria - such as reduced correlation among the terms
     * or enhanced space-filling properties.
     * <p>
     * A randomly generated Latin Hypercube may be quite structured: the design
     * may not have good univariate projection uniformity or the different
     * columns might be highly correlated. Several criteria such as maximin
     * distance and minimum correlation have been proposed to address these
     * issues.
     *
     * @param n the number of divisions, also the number of samples.
     * @param d the dimensionality, i.e. the number of variables.
     * @return Latin hypercube of n-by-d matrix.
     */
    static int[][] latin(int n, int d) {
        int[][] hypercube = new int[n][d];
        int[] intervals = MathEx.permutate(n);
        for (int j = 0; j < d; j++) {
            for (int i = 0; i < n; i++) {
                hypercube[i][j] = intervals[i];
            }

            MathEx.permutate(intervals);
        }

        return hypercube;
    }
}