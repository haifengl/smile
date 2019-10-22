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

package smile.classification;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import smile.sort.QuickSort;

/**
 * A method to calibrate decision function value to probability.
 * Compared to Platt's scaling, this approach fits a piecewise-constant
 * non-decreasing function instead of logistic regression.
 *
 * <h2>References</h2>
 * <ol>
 * <li>Alexandru Niculescu-Mizil and Rich Caruana. Predicting Good Probabilities With Supervised Learning. ICML, 2005.</li>
 * </ol>
 *
 * @author Haifeng Li
 */
public class IsotonicRegressionScaling implements Serializable {
    private static final long serialVersionUID = 2L;

    /**
     * Step-wise constant function.
     */
    private static class StepwiseConstant {
        double lo;
        double hi;
        double val;
        int weight;

        /** Constructor. */
        StepwiseConstant(double lo, double hi, double val, int weight) {
            this.lo = lo;
            this.hi = hi;
            this.val = val;
            this.weight = weight;
        }
    }

    /** The step-wise buckets of function values in ascending order. */
    private double[] buckets;
    /** The probability of instances falling into the corresponding buckets. */
    private double[] prob;

    /**
     * Constructor.
     * @param buckets the step-wise buckets of function values in ascending order.
     * @param prob the probability of instances falling into the corresponding buckets.
     */
    public IsotonicRegressionScaling(double[] buckets, double[] prob) {
        this.buckets = buckets.clone();
        this.prob = prob;

        // Set the last value to max value so that index is always in [0, n).
        int n = buckets.length;
        this.buckets[n-1] = Double.POSITIVE_INFINITY;
    }

    /**
     * Trains the Isotonic Regression scaling.
     *
     * @param scores The predicted scores.
     * @param y      The training labels.
     */
    public static IsotonicRegressionScaling fit(double[] scores, int[] y) {
        double[] sortedScores = Arrays.copyOf(scores, scores.length);
        int[] sortedY = Arrays.copyOf(y, y.length);

        QuickSort.sort(sortedScores, sortedY, sortedScores.length);

        LinkedList<StepwiseConstant> steps = new LinkedList<>();
        for (int i = 0; i < sortedScores.length; i++) {
            steps.add(new StepwiseConstant(sortedScores[i], sortedScores[i], sortedY[i] > 0 ? 1 : 0, 1));
        }

        boolean isotonic = false;
        while (!isotonic) {
            isotonic = true;
            Iterator<StepwiseConstant> iter = steps.iterator();
            StepwiseConstant prev = iter.next();
            while (iter.hasNext()) {
                StepwiseConstant g0 = prev;
                StepwiseConstant g1 = iter.next();
                if (g0.val >= g1.val) {
                    g0.hi = g1.hi;
                    int weight = g0.weight + g1.weight;
                    g0.val = (g0.weight * g0.val + g1.weight * g1.val) / weight;
                    g0.weight = weight;
                    iter.remove();
                    isotonic = false;
                } else {
                    prev = g1;
                }
            }
        }

        int n = steps.size();
        double[] buckets = new double[n];
        double[] prob = new double[n];

        Iterator<StepwiseConstant> iter = steps.iterator();
        for (int i = 0; iter.hasNext(); i++) {
            StepwiseConstant step = iter.next();
            buckets[i] = step.hi;
            prob[i] = step.val;
        }

        return new IsotonicRegressionScaling(buckets, prob);
    }

    /**
     * Returns the posterior probability estimate P(y = 1 | x).
     *
     * @param y the binary classifier output score.
     * @return the estimated probability.
     */
    public double predict(double y) {
        int index = Arrays.binarySearch(buckets, y);
        if (index < 0) index = -index - 1;
        return prob[index];
    }

    @Override
    public String toString() {
        return IntStream.range(0, buckets.length).mapToObj(i -> String.format("(%.2f, %.2f%%)", buckets[i], 100*prob[i])).collect(Collectors.joining(", ", "IsotonicRegressionScaling[", "]"));
    }
}
