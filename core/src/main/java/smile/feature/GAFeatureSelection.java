/*******************************************************************************
 * Copyright (c) 2010 Haifeng Li
 *   
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *  
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/

package smile.feature;

import smile.classification.Classifier;
import smile.classification.ClassifierTrainer;
import smile.gap.BitString;
import smile.gap.FitnessMeasure;
import smile.gap.GeneticAlgorithm;
import smile.regression.Regression;
import smile.regression.RegressionTrainer;
import smile.validation.ClassificationMeasure;
import smile.validation.RegressionMeasure;
import smile.validation.Validation;

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
public class GAFeatureSelection {
    /**
     * Selection strategy.
     */
    private GeneticAlgorithm.Selection selection = GeneticAlgorithm.Selection.TOURNAMENT;
    /**
     * Mutation rate.
     * The mutation parameters are set higher than usual to prevent premature convergence. 
     */
    private double mutationRate = 0.01;
    /**
     * Crossover strategy.
     */
    private BitString.Crossover crossover = BitString.Crossover.UNIFORM;
    /**
     * Crossover rate.
     */
    private double crossoverRate = 1.0;

    /**
     * Constructor.
     */
    public GAFeatureSelection() {
    }

    /**
     * Constructor.
     * @param selection the selection strategy.
     * @param crossover the strategy of crossover operation.
     * @param crossoverRate the crossover rate.
     * @param mutationRate the mutation rate.
     */
    public GAFeatureSelection(GeneticAlgorithm.Selection selection, BitString.Crossover crossover, double crossoverRate, double mutationRate) {
        if (crossoverRate < 0.0 || crossoverRate > 1.0) {
            throw new IllegalArgumentException("Invalid crossover rate: " + crossoverRate);
        }
        
        if (mutationRate < 0.0 || mutationRate > 1.0) {
            throw new IllegalArgumentException("Invalid mutation rate: " + mutationRate);
        }
        
        this.selection = selection;
        this.crossover = crossover;
        this.crossoverRate = crossoverRate;
        this.mutationRate = mutationRate;
    }
    
    /**
     * Genetic algorithm based feature selection for classification.
     * @param size the population size of Genetic Algorithm.
     * @param generation the maximum number of iterations.
     * @param trainer classifier trainer.
     * @param measure classification measure as the chromosome fitness measure.
     * @param x training instances.
     * @param y training labels.
     * @param k k-fold cross validation for the evaluation.
     * @return bit strings of last generation.
     */
    public BitString[] learn(int size, int generation, ClassifierTrainer<double[]> trainer, ClassificationMeasure measure, double[][] x, int[] y, int k) {
        if (size <= 0) {
            throw new IllegalArgumentException("Invalid population size: " + size);
        }
        
        if (k < 2) {
            throw new IllegalArgumentException("Invalid k-fold cross validation: " + k);
        }
        
        if (x.length != y.length) {
            throw new IllegalArgumentException(String.format("The sizes of X and Y don't match: %d != %d", x.length, y.length));
        }

        int p = x[0].length;
        ClassificationFitness fitness = new ClassificationFitness(trainer, measure, x, y, k);
        
        BitString[] seeds = new BitString[size];
        for (int i = 0; i < size; i++) {
            seeds[i] = new BitString(p, fitness, crossover, crossoverRate, mutationRate);
        }

        GeneticAlgorithm<BitString> ga = new GeneticAlgorithm<>(seeds, selection);
        ga.evolve(generation);       
        
        return seeds;
    }
    
    /**
     * Genetic algorithm based feature selection for classification.
     * @param size the population size of Genetic Algorithm.
     * @param generation the maximum number of iterations.
     * @param trainer classifier trainer.
     * @param measure classification measure as the chromosome fitness measure.
     * @param x training instances.
     * @param y training instance labels.
     * @param testx testing instances.
     * @param testy testing instance labels.
     * @return bit strings of last generation.
     */
    public BitString[] learn(int size, int generation, ClassifierTrainer<double[]> trainer, ClassificationMeasure measure, double[][] x, int[] y, double[][] testx, int[] testy) {
        if (size <= 0) {
            throw new IllegalArgumentException("Invalid population size: " + size);
        }
        
        if (generation <= 0) {
            throw new IllegalArgumentException("Invalid number of generations to go: " + generation);
        }
        
        if (x.length != y.length) {
            throw new IllegalArgumentException(String.format("The sizes of X and Y don't match: %d != %d", x.length, y.length));
        }

        if (testx.length != testy.length) {
            throw new IllegalArgumentException(String.format("The sizes of test X and Y don't match: %d != %d", testx.length, testy.length));
        }

        int p = x[0].length;
        ClassificationFitness fitness = new ClassificationFitness(trainer, measure, x, y, testx, testy);
        
        BitString[] seeds = new BitString[size];
        for (int i = 0; i < size; i++) {
            seeds[i] = new BitString(p, fitness, crossover, crossoverRate, mutationRate);
        }

        GeneticAlgorithm<BitString> ga = new GeneticAlgorithm<>(seeds, selection);
        ga.evolve(generation);       
        
        return seeds;        
    }
    
    /**
     * The measure to evaluate the fitness of chromosome.
     */
    class ClassificationFitness implements FitnessMeasure<BitString> {

        /**
         * Classifier trainer
         */
        ClassifierTrainer<double[]> trainer;
        /**
         * Classification measure
         */
        ClassificationMeasure measure;
        /**
         * Training instances.
         */
        double[][] x;
        /**
         * Training labels.
         */
        int[] y;
        /**
         * Testing instances.
         */
        double[][] testx;
        /**
         * Testing instance labels.
         */
        int[] testy;
        /**
         * k-fold cross validation.
         */
        int k = -1;
        
        /**
         * Constructor.
         */
        ClassificationFitness(ClassifierTrainer<double[]> trainer, ClassificationMeasure measure, double[][] x, int[] y, int k) {
            this.trainer = trainer;
            this.measure = measure;
            this.x = x;
            this.y = y;
            this.k = k;
        }
        
        /**
         * Constructor.
         */
        ClassificationFitness(ClassifierTrainer<double[]> trainer, ClassificationMeasure measure, double[][] x, int[] y, double[][] testx, int[] testy) {
            this.trainer = trainer;
            this.measure = measure;
            this.x = x;
            this.y = y;
            this.testx = testx;
            this.testy = testy;
        }
        
        @Override
        public double fit(BitString chromosome) {
            int p = 0;
            int[] bits = chromosome.bits();
            for (int b : bits) {
                p += b;
            }
            
            if (p == 0) {
                return 0.0;
            }
            
            int m = x[0].length;
            int n = x.length;
            double[][] xx = new double[n][p];
            for (int j = 0, jj = 0; j < m; j++) {
                if (bits[j] == 1) {
                    for (int i = 0; i < n; i++) {
                        xx[i][jj] = x[i][j];
                    }
                    jj++;
                }
            }

            if (k != -1) {
                return Validation.cv(k, trainer, xx, y);
            } else {
                Classifier<double[]> classifier = trainer.train(xx, y);
                
                int testn = testx.length;
                double[][] testxx = new double[testn][p];
                for (int j = 0, jj = 0; j < m; j++) {
                    if (bits[j] == 1) {
                        for (int i = 0; i < testn; i++) {
                            testxx[i][jj] = testx[i][j];
                        }
                        jj++;
                    }
                }
                
                int[] prediction = new int[testn];
                for (int i = 0; i < testn; i++) {
                    prediction[i] = classifier.predict(testxx[i]);
                }
                
                return measure.measure(testy, prediction);                
            }
        }
    };
    /**
     * Genetic algorithm based feature selection for regression.
     * @param size the population size of Genetic Algorithm.
     * @param generation the maximum number of iterations.
     * @param trainer regression model trainer.
     * @param measure classification measure as the chromosome fitness measure.
     * @param x training instances.
     * @param y training instance response variable.
     * @param k k-fold cross validation for the evaluation.
     * @return bit strings of last generation.
     */
    public BitString[] learn(int size, int generation, RegressionTrainer<double[]> trainer, RegressionMeasure measure, double[][] x, double[] y, int k) {
        if (size <= 0) {
            throw new IllegalArgumentException("Invalid population size: " + size);
        }
        
        if (k < 2) {
            throw new IllegalArgumentException("Invalid k-fold cross validation: " + k);
        }
        
        if (x.length != y.length) {
            throw new IllegalArgumentException(String.format("The sizes of X and Y don't match: %d != %d", x.length, y.length));
        }

        int p = x[0].length;
        RegressionFitness fitness = new RegressionFitness(trainer, measure, x, y, k);
        
        BitString[] seeds = new BitString[size];
        for (int i = 0; i < size; i++) {
            seeds[i] = new BitString(p, fitness, crossover, crossoverRate, mutationRate);
        }

        GeneticAlgorithm<BitString> ga = new GeneticAlgorithm<>(seeds, selection);
        ga.evolve(generation);       
        
        return seeds;
    }
    
    /**
     * Genetic algorithm based feature selection for regression.
     * @param size the population size of Genetic Algorithm.
     * @param generation the maximum number of iterations.
     * @param trainer regression model trainer.
     * @param measure classification measure as the chromosome fitness measure.
     * @param x training instances.
     * @param y training instance response variable.
     * @param testx testing instances.
     * @param testy testing instance labels.
     * @return bit strings of last generation.
     */
    public BitString[] learn(int size, int generation, RegressionTrainer<double[]> trainer, RegressionMeasure measure, double[][] x, double[] y, double[][] testx, double[] testy) {
        if (size <= 0) {
            throw new IllegalArgumentException("Invalid population size: " + size);
        }
        
        if (generation <= 0) {
            throw new IllegalArgumentException("Invalid number of generations to go: " + generation);
        }
        
        if (x.length != y.length) {
            throw new IllegalArgumentException(String.format("The sizes of X and Y don't match: %d != %d", x.length, y.length));
        }

        if (testx.length != testy.length) {
            throw new IllegalArgumentException(String.format("The sizes of test X and Y don't match: %d != %d", testx.length, testy.length));
        }

        int p = x[0].length;
        RegressionFitness fitness = new RegressionFitness(trainer, measure, x, y, testx, testy);
        
        BitString[] seeds = new BitString[size];
        for (int i = 0; i < size; i++) {
            seeds[i] = new BitString(p, fitness, crossover, crossoverRate, mutationRate);
        }

        GeneticAlgorithm<BitString> ga = new GeneticAlgorithm<>(seeds, selection);
        ga.evolve(generation);       
        
        return seeds;        
    }
    
    /**
     * The measure to evaluate the fitness of chromosome.
     */
    class RegressionFitness implements FitnessMeasure<BitString> {

        /**
         * Classifier trainer
         */
        RegressionTrainer<double[]> trainer;
        /**
         * Classification measure
         */
        RegressionMeasure measure;
        /**
         * Training instances.
         */
        double[][] x;
        /**
         * Training instance response.
         */
        double[] y;
        /**
         * Testing instances.
         */
        double[][] testx;
        /**
         * Testing instance response.
         */
        double[] testy;
        /**
         * k-fold cross validation.
         */
        int k = -1;
        
        /**
         * Constructor.
         */
        RegressionFitness(RegressionTrainer<double[]> trainer, RegressionMeasure measure, double[][] x, double[] y, int k) {
            this.trainer = trainer;
            this.measure = measure;
            this.x = x;
            this.y = y;
            this.k = k;
        }
        
        /**
         * Constructor.
         */
        RegressionFitness(RegressionTrainer<double[]> trainer, RegressionMeasure measure, double[][] x, double[] y, double[][] testx, double[] testy) {
            this.trainer = trainer;
            this.measure = measure;
            this.x = x;
            this.y = y;
            this.testx = testx;
            this.testy = testy;
        }
        
        @Override
        public double fit(BitString chromosome) {
            int p = 0;
            int[] bits = chromosome.bits();
            for (int b : bits) {
                p += b;
            }
            
            if (p == 0) {
                return 0.0;
            }
            
            int m = x[0].length;
            int n = x.length;
            double[][] xx = new double[n][p];
            for (int j = 0, jj = 0; j < m; j++) {
                if (bits[j] == 1) {
                    for (int i = 0; i < n; i++) {
                        xx[i][jj] = x[i][j];
                    }
                    jj++;
                }
            }

            if (k != -1) {
                return -Validation.cv(k, trainer, xx, y);
            } else {
                Regression<double[]> regression = trainer.train(xx, y);
                
                int testn = testx.length;
                double[][] testxx = new double[testn][p];
                for (int j = 0, jj = 0; j < m; j++) {
                    if (bits[j] == 1) {
                        for (int i = 0; i < testn; i++) {
                            testxx[i][jj] = testx[i][j];
                        }
                        jj++;
                    }
                }
                
                double[] prediction = new double[testn];
                for (int i = 0; i < testn; i++) {
                    prediction[i] = regression.predict(testxx[i]);
                }
                
                return -measure.measure(testy, prediction);                
            }
        }
    };
}
