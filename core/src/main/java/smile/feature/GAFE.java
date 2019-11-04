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

package smile.feature;

import java.util.function.BiFunction;
import smile.classification.Classifier;
import smile.classification.DataFrameClassifier;
import smile.data.DataFrame;
import smile.data.formula.Formula;
import smile.gap.*;
import smile.math.MathEx;
import smile.regression.DataFrameRegression;
import smile.regression.Regression;
import smile.validation.*;

/**
 * Genetic algorithm based feature selection. This method finds many (random)
 * subsets of variables of expected classification power using a Genetic
 * Algorithm. The "fitness" of each subset of variables is determined by its
 * ability to classify the samples according to a given classification
 * method. When many such subsets of variables are obtained, the one with best
 * performance may be used as selected features. Alternatively, the frequencies
 * with which variables are selected may be analyzed further. The most
 * frequently selected variables may be presumed to be the most relevant to
 * sample distinction and are finally used for prediction. Although GA avoids
 * brute-force search, it is still much slower than univariate feature selection.
 * 
 * <h2>References</h2>
 * <ol>
 * <li> Leping Li and Clarice R. Weinberg. Gene Selection and Sample Classification Using a Genetic Algorithm/k-Nearest Neighbor Method.</li>
 * </ol>
 * 
 * @author Haifeng Li
 */
public class GAFE {
    /**
     * Selection strategy.
     */
    private Selection selection;
    /**
     * The number of best chromosomes to copy to new population. When creating
     * new population by crossover and mutation, we have a big chance, that we
     * will loose the best chromosome. Elitism first copies the best chromosome
     * (or a few best chromosomes) to new population. The rest is done in
     * classical way. Elitism can very rapidly increase performance of GA,
     * because it prevents losing the best found solution.
     */
    private int elitism;
    /**
     * Crossover strategy.
     */
    private Crossover crossover;
    /**
     * Crossover rate.
     */
    private double crossoverRate;
    /**
     * Mutation rate.
     * The mutation parameters are set higher than usual to prevent premature convergence.
     */
    private double mutationRate;

    /**
     * Constructor.
     */
    public GAFE() {
        this(Selection.Tournament(3, 0.95), 1, Crossover.TWO_POINT, 1.0, 0.01);
    }

    /**
     * Constructor.
     * @param selection the selection strategy.
     * @param crossover the strategy of crossover operation.
     * @param crossoverRate the crossover rate.
     * @param mutationRate the mutation rate.
     */
    public GAFE(Selection selection, int elitism, Crossover crossover, double crossoverRate, double mutationRate) {
        this.selection = selection;
        this.elitism = elitism;
        this.crossover = crossover;
        this.crossoverRate = crossoverRate;
        this.mutationRate = mutationRate;
    }
    
    /**
     * Genetic algorithm based feature selection for classification.
     * @param size the population size of Genetic Algorithm.
     * @param generation the maximum number of iterations.
     * @param length the length of bit string, i.e. the number of features.
     * @return bit strings of last generation.
     */
    public BitString[] apply(int size, int generation, int length, FitnessMeasure<BitString> fitness) {
        if (size <= 0) {
            throw new IllegalArgumentException("Invalid population size: " + size);
        }
        
        BitString[] seeds = new BitString[size];
        for (int i = 0; i < size; i++) {
            seeds[i] = new BitString(length, fitness, crossover, crossoverRate, mutationRate);
        }

        GeneticAlgorithm<BitString> ga = new GeneticAlgorithm<>(seeds, selection, elitism);
        ga.evolve(generation);       
        
        return seeds;
    }

    /** Returns the index of 1's. */
    private static int[] indexOfOnes(byte[] bits) {
        int p = MathEx.sum(bits);
        if (p == 0) return null;

        int[] index = new int[p];
        for (int i = 0, ii = 0; i < bits.length; i++) {
            if (bits[i] == 1) index[ii++] = i;
        }
        return index;
    }

    /** Returns the data with selected features. */
    private static double[][] select(double[][] x, int[] features) {
        int p = features.length;
        int n = x.length;

        double[][] xx = new double[n][p];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < p; j++) {
                xx[i][j] = x[i][features[j]];
            }
        }

        return xx;
    }

    /**
     * Returns a classification fitness measure.
     *
     * @param x training samples.
     * @param y training labels.
     * @param testx testing samples.
     * @param testy testing labels.
     * @param measure classification measure.
     * @param trainer the lambda to train a model.
     */
    public static FitnessMeasure<BitString> fitness(double[][] x, int[] y, double[][] testx, int[] testy, ClassificationMeasure measure, BiFunction<double[][], int[], Classifier<double[]>> trainer) {
        return chromosome -> {
            byte[] bits = chromosome.bits();
            int[] features = indexOfOnes(bits);
            if (features == null) return 0.0;

            double[][] xx = select(x, features);
            double[][] testxx = select(testx, features);

            Classifier<double[]> model = trainer.apply(xx, y);
            return measure.measure(testy, model.predict(testxx));
        };
    }

    /**
     * Returns a regression fitness measure.
     *
     * @param x training samples.
     * @param y training response.
     * @param testx testing samples.
     * @param testy testing response.
     * @param measure classification measure.
     * @param trainer the lambda to train a model.
     */
    public static FitnessMeasure<BitString> fitness(double[][] x, double[] y, double[][] testx, double[] testy, RegressionMeasure measure, BiFunction<double[][], double[], Regression<double[]>> trainer) {
        return chromosome -> {
            byte[] bits = chromosome.bits();
            int[] features = indexOfOnes(bits);
            if (features == null) return Double.NEGATIVE_INFINITY;

            double[][] xx = select(x, features);
            double[][] testxx = select(testx, features);

            Regression<double[]> model = trainer.apply(xx, y);
            return -measure.measure(testy, model.predict(testxx));
        };
    }

    /** Returns the selected features. */
    private static String[] selectedFeatures(byte[] bits, String[] names, String y) {
        int p = MathEx.sum(bits);
        if (p == 0) return null;

        int offset = 0;
        String[] features = new String[p];
        for (int i = 0, ii = 0; i < bits.length; i++) {
            if (names[i].equals(y)) {
                offset++;
            }

            if (bits[i] == 1) {
                features[ii++] = names[i+offset];
            }
        }
        return features;
    }

    /**
     * Returns a classification fitness measure.
     *
     * @param y the column name of class labels.
     * @param train training data.
     * @param test testing data.
     * @param measure classification measure.
     * @param trainer the lambda to train a model.
     */
    public static FitnessMeasure<BitString> fitness(String y, DataFrame train, DataFrame test, ClassificationMeasure measure, BiFunction<Formula, DataFrame, DataFrameClassifier> trainer) {
        String[] names = train.names();
        int[] testy = test.column(y).toIntArray();

        return chromosome -> {
            byte[] bits = chromosome.bits();
            String[] features = selectedFeatures(bits, names, y);
            if (features == null) return 0.0;

            Formula formula = Formula.of(y, features);
            DataFrameClassifier model = trainer.apply(formula, train);
            return measure.measure(testy, model.predict(test));
        };
    }

    /**
     * Returns a regression fitness measure.
     *
     * @param y the column name of response variable.
     * @param train training data.
     * @param test testing data.
     * @param measure classification measure.
     * @param trainer the lambda to train a model.
     */
    public static FitnessMeasure<BitString> fitness(String y, DataFrame train, DataFrame test, RegressionMeasure measure, BiFunction<Formula, DataFrame, DataFrameRegression> trainer) {
        String[] names = train.names();
        double[] testy = test.column(y).toDoubleArray();

        return chromosome -> {
            byte[] bits = chromosome.bits();
            String[] features = selectedFeatures(bits, names, y);
            if (features == null) return Double.NEGATIVE_INFINITY;

            Formula formula = Formula.of(y, features);
            DataFrameRegression model = trainer.apply(formula, train);
            return -measure.measure(testy, model.predict(test));
        };
    }
}
