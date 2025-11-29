/*
 * Copyright (c) 2010-2025 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.validation;

import java.io.Serial;
import java.io.Serializable;
import java.util.Arrays;
import smile.data.DataFrame;
import smile.math.MathEx;
import smile.stat.Sampling;
import smile.util.Index;
import smile.util.Tuple2;

/**
 * A bag of random selected samples.
 *
 * @param samples the random samples.
 * @param oob the out of bag samples.
 * @author Haifeng Li
 */
public record Bag(int[] samples, int[] oob) implements Serializable {
    @Serial
    private static final long serialVersionUID = 3L;

    /**
     * Splits samples into random train and test subsets.
     * @param n the number of samples.
     * @param holdout the proportion of samples in the test split.
     * @return the sample split.
     */
    public static Bag split(int n, double holdout) {
        if (n < 0) {
            throw new IllegalArgumentException("Invalid sample size: " + n);
        }

        if (holdout <= 0 || holdout >= 1) {
            throw new IllegalArgumentException("Invalid holdout proportion: " + holdout);
        }

        int[] index = MathEx.permutate(n);
        int trainSize = (int) Math.round(n * (1 - holdout));
        int[] train = Arrays.copyOf(index, trainSize);
        int[] test = Arrays.copyOfRange(index, trainSize, n);
        return new Bag(train, test);
    }

    /**
     * Splits a data frame into random train and test subsets.
     * @param data the data frame.
     * @param holdout the proportion of samples in the test split.
     * @return the data splits.
     */
    public static Tuple2<DataFrame, DataFrame> split(DataFrame data, double holdout) {
        Bag bag = split(data.nrow(), holdout);
        var train = data.get(Index.of(bag.samples()));
        var test = data.get(Index.of(bag.oob()));
        return new Tuple2<>(train, test);
    }

    /**
     * Stratified splitting samples into random train and test subsets.
     * The splits are made by preserving the percentage of samples for each group.
     *
     * @param category the strata label.
     * @param holdout the proportion of samples in the test split.
     * @return the stratified sample split.
     */
    static Bag stratify(int[] category, double holdout) {
        if (holdout <= 0 || holdout >= 1) {
            throw new IllegalArgumentException("Invalid holdout proportion: " + holdout);
        }

        int[][] strata = Sampling.strata(category);
        int n = category.length;
        int m = strata.length;

        int p = 0;
        int q = 0;
        int[] train = new int[n];
        int[] test = new int[n];

        for (int[] stratum : strata) {
            // Shuffle every stratum separately
            MathEx.permutate(stratum);
            int size = stratum.length;
            int trainSize = (int) Math.round(size * (1 - holdout));
            System.arraycopy(stratum, 0, train, p, trainSize);
            System.arraycopy(stratum, trainSize, test, q, size - trainSize);
            p += trainSize;
            q += size - trainSize;
        }

        train = Arrays.copyOf(train, p);
        test = Arrays.copyOf(test, q);

        // Samples are in order of strata. It is better to shuffle.
        MathEx.permutate(train);
        MathEx.permutate(test);
        return new Bag(train, test);
    }

    /**
     * Stratified splitting a data frame into random train and test subsets.
     * @param data the data frame.
     * @param category the column as the strata label.
     * @param holdout the proportion of samples in the test split.
     * @return the data splits.
     */
    public static Tuple2<DataFrame, DataFrame> stratify(DataFrame data, String category, double holdout) {
        int[] label = data.column(category).toIntArray();
        Bag bag = stratify(label, holdout);
        var train = data.get(Index.of(bag.samples()));
        var test = data.get(Index.of(bag.oob()));
        return new Tuple2<>(train, test);
    }
}
