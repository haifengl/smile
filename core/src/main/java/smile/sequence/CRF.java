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
package smile.sequence;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import smile.data.Attribute;
import smile.data.NominalAttribute;
import smile.data.NumericAttribute;
import smile.math.Math;
import smile.regression.RegressionTree;
import smile.sort.QuickSort;
import smile.util.MulticoreExecutor;

/**
 * First-order linear conditional random field. A conditional random field is a
 * type of discriminative undirected probabilistic graphical model. It is most
 * often used for labeling or parsing of sequential data. <p> A CRF is a Markov
 * random field that was trained discriminatively. Therefore it is not necessary
 * to model the distribution over always observed variables, which makes it
 * possible to include arbitrarily complicated features of the observed
 * variables into the model.
 *
 * This class implements an algorithm that trains CRFs via gradient
 * tree boosting. In tree boosting, the CRF potential functions
 * are represented as weighted sums of regression trees, which provide
 * compact representations of feature interactions. So the algorithm does
 * not explicitly consider the potentially large parameter space. As a result,
 * gradient tree boosting scales linearly in the order of the Markov model and in
 * the order of the feature interactions, rather than exponentially as
 * in previous algorithms based on iterative scaling and gradient descent.
 *
 * <h2>References</h2>
 * <ol>
 * <li> J. Lafferty, A. McCallum and F. Pereira.
 * Conditional random fields: Probabilistic models for segmenting and labeling
 * sequence data. ICML, 2001.</li>
 * <li> Thomas G. Dietterich, Guohua Hao, and
 * Adam Ashenfelter. Gradient Tree Boosting for Training Conditional Random
 * Fields. JMLR, 2008.</li>
 * </ol>
 *
 * @author Haifeng Li
 */
public class CRF implements SequenceLabeler<double[]> {
    private static final Logger logger = LoggerFactory.getLogger(CRF.class);

    /**
     * The number of classes.
     */
    private int numClasses;
    /**
     * The number of sparse binary features.
     */
    private int numFeatures = -1;
    /**
     * The potential functions for each class.
     */
    private TreePotentialFunction[] potentials;
    /**
     * True if using Viterbi algorithm for sequence labeling.
     */
    private boolean viterbi = false;

    /**
     * Returns a feature set with the class label of previous position.
     *
     * @param features the indices of the nonzero features.
     * @param label the class label of previous position as a feature.
     */
    public double[] featureset(double[] features, int label) {
        double[] fs = new double[features.length + 1];
        System.arraycopy(features, 0, fs, 0, features.length);
        fs[features.length] = label;
        return fs;
    }

    /**
     * Returns a feature set with the class label of previous position.
     *
     * @param features the indices of the nonzero features.
     * @param label the class label of previous position as a feature.
     */
    public int[] featureset(int[] features, int label) {
        int[] fs = new int[features.length + 1];
        System.arraycopy(features, 0, fs, 0, features.length);
        fs[features.length] = numFeatures + label;
        return fs;
    }

    /**
     * Regression tree based potential function.
     */
    class TreePotentialFunction {

        /**
         * Constructor.
         *
         * @param eta the learning rate for each tree when calculate potential
         * function.
         */
        public TreePotentialFunction(double eta) {
            this.eta = eta;
        }

        /**
         * Computes potential function score without exp.
         */
        public double f(double[] features) {
            double score = 0.0;
            for (RegressionTree tree : trees) {
                score += eta * tree.predict(features);
            }
            return score;
        }

        /**
         * Computes potential function score without exp.
         */
        public double f(int[] features) {
            double score = 0.0;
            for (RegressionTree tree : trees) {
                score += eta * tree.predict(features);
            }
            return score;
        }

        /*
         * Add another tree for the potential function.
         */
        public void add(RegressionTree tree) {
            trees.add(tree);
        }
        /**
         * Scale parameter in boosting.
         */
        private double eta;
        /**
         * Gradient regression tree boosting.
         */
        private List<RegressionTree> trees = new ArrayList<>();
    }

    /**
     * Dynamic programming table entry in forward-backward algorithm.
     */
    class TrellisNode {

        /**
         * Forward variable.
         */
        double alpha = 1.0;
        /**
         * Backward variable.
         */
        double beta = 1.0;
        /**
         * Conditional samples.
         */
        double[][] samples;
        /**
         * Conditional samples.
         */
        int[][] sparseSamples;
        /**
         * Residual of conditional samples as regression tree training target.
         */
        double[] target = new double[numClasses];
        /**
         * Potential function values scores[k] = F(k, X)
         */
        double[] scores = new double[numClasses];
        /**
         * Exp of potential function values
         */
        double[] expScores = new double[numClasses];
        /**
         * The generation of cached score.
         */
        int age = 0;
        
        TrellisNode(boolean sparse) {
            if (sparse) {
                sparseSamples = new int[numClasses][];                
            } else {
                samples = new double[numClasses][];
            }
        }
    }

    /**
     * Constructor.
     *
     * @param numClasses the number of classes.
     * @param eta the learning rate of potential function.
     */
    private CRF(int numClasses, double eta) {
        this.numClasses = numClasses;

        potentials = new TreePotentialFunction[numClasses];
        for (int i = 0; i < numClasses; i++) {
            potentials[i] = new TreePotentialFunction(eta);
        }
    }

    /**
     * Constructor.
     *
     * @param numFeatures the number of sparse binary features.
     * @param numClasses the number of classes.
     * @param eta the learning rate of potential function.
     */
    private CRF(int numFeatures, int numClasses, double eta) {
        this.numFeatures = numFeatures;
        this.numClasses = numClasses;

        potentials = new TreePotentialFunction[numClasses];
        for (int i = 0; i < numClasses; i++) {
            potentials[i] = new TreePotentialFunction(eta);
        }
    }

    /**
     * Returns true if using Viterbi algorithm for sequence labeling.
     */
    public boolean isViterbi() {
        return viterbi;
    }

    /**
     * Sets if using Viterbi algorithm for sequence labeling. Viterbi algorithm
     * returns the whole sequence label that has the maximum probability, which
     * makes sense in applications (e.g.part-of-speech tagging) that require
     * coherent sequential labeling. The forward-backward algorithm labels a
     * sequence by individual prediction on each position. This usually produces
     * better accuracy although the results may not be coherent.
     */
    public CRF setViterbi(boolean viterbi) {
        this.viterbi = viterbi;
        return this;
    }

    @Override
    public int[] predict(double[][] x) {
        if (viterbi) {
            return predictViterbi(x);
        } else {
            return predictForwardBackward(x);
        }
    }

    public int[] predict(int[][] x) {
        if (viterbi) {
            return predictViterbi(x);
        } else {
            return predictForwardBackward(x);
        }
    }

    /**
     * Returns the most likely label sequence given the feature sequence by the
     * forward-backward algorithm.
     *
     * @param x a sequence of sparse features taking values in [0, p) about each
     * position of original sequence, where p is the number of features.
     * @return the most likely label sequence.
     */
    private int[] predictForwardBackward(double[][] x) {
        int n = x.length; // length of sequence

        TrellisNode[][] trellis = getTrellis(x);
        double[] scaling = new double[n];
        forward(trellis, scaling);
        backward(trellis);

        int[] label = new int[n];
        double[] p = new double[numClasses];
        for (int i = 0; i < n; i++) {
            TrellisNode[] ti = trellis[i];
            for (int j = 0; j < numClasses; j++) {
                TrellisNode tij = ti[j];
                p[j] = tij.alpha * tij.beta;
            }

            double max = Double.NEGATIVE_INFINITY;
            for (int j = 0; j < numClasses; j++) {
                if (max < p[j]) {
                    max = p[j];
                    label[i] = j;
                }
            }
        }

        return label;
    }

    /**
     * Returns the most likely label sequence given the feature sequence by the
     * Viterbi algorithm.
     *
     * @param x a sequence of sparse features taking values in [0, p) about each
     * position of original sequence, where p is the number of features.
     * @return the most likely label sequence.
     */
    private int[] predictViterbi(double[][] x) {
        int n = x.length;

        double[][] trellis = new double[n][numClasses];
        int[][] psy = new int[n][numClasses];

        int p = x[0].length; // dimension

        // forward
        double[] features = featureset(x[0], numClasses);
        double[] t0 = trellis[0];
        int[] p0 = psy[0];
        for (int j = 0; j < numClasses; j++) {
            t0[j] = potentials[j].f(features);
            p0[j] = 0;
        }

        for (int t = 1; t < n; t++) {
            System.arraycopy(x[t], 0, features, 0, p);
            double[] tt = trellis[t];
            double[] tt1 = trellis[t - 1];
            int[] pt = psy[t];
            for (int i = 0; i < numClasses; i++) {
                double max = Double.NEGATIVE_INFINITY;
                int maxPsy = 0;
                TreePotentialFunction pi = potentials[i];
                for (int j = 0; j < numClasses; j++) {
                    features[p] = j;
                    double delta = pi.f(features) + tt1[j];
                    if (max < delta) {
                        max = delta;
                        maxPsy = j;
                    }
                }
                tt[i] = max;
                pt[i] = maxPsy;
            }
        }

        // trace back
        int[] label = new int[n];
        double[] tn1 = trellis[n - 1];
        double max = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < numClasses; i++) {
            if (max < tn1[i]) {
                max = tn1[i];
                label[n - 1] = i;
            }
        }

        for (int t = n - 1; t-- > 0;) {
            label[t] = psy[t + 1][label[t + 1]];
        }
        return label;
    }

    /**
     * Returns the most likely label sequence given the feature sequence by the
     * forward-backward algorithm.
     *
     * @param x a sequence of sparse features taking values in [0, p) about each
     * position of original sequence, where p is the number of features.
     * @return the most likely label sequence.
     */
    private int[] predictForwardBackward(int[][] x) {
        int n = x.length; // length of sequence

        TrellisNode[][] trellis = getTrellis(x);
        double[] scaling = new double[n];
        forward(trellis, scaling);
        backward(trellis);

        int[] label = new int[n];
        double[] p = new double[numClasses];
        for (int i = 0; i < n; i++) {
            TrellisNode[] ti = trellis[i];
            for (int j = 0; j < numClasses; j++) {
                TrellisNode tij = ti[j];
                p[j] = tij.alpha * tij.beta;
            }

            double max = Double.NEGATIVE_INFINITY;
            for (int j = 0; j < numClasses; j++) {
                if (max < p[j]) {
                    max = p[j];
                    label[i] = j;
                }
            }
        }

        return label;
    }

    /**
     * Returns the most likely label sequence given the feature sequence by the
     * Viterbi algorithm.
     *
     * @param x a sequence of sparse features taking values in [0, p) about each
     * position of original sequence, where p is the number of features.
     * @return the most likely label sequence.
     */
    private int[] predictViterbi(int[][] x) {
        int n = x.length;

        double[][] trellis = new double[n][numClasses];
        int[][] psy = new int[n][numClasses];

        int p = x[0].length; // dimension

        // forward
        double[] t0 = trellis[0];
        int[] p0 = psy[0];
        int[] features = featureset(x[0], numClasses);
        for (int j = 0; j < numClasses; j++) {
            t0[j] = potentials[j].f(features);
            p0[j] = 0;
        }

        for (int t = 1; t < n; t++) {
            System.arraycopy(x[t], 0, features, 0, p);
            double[] tt = trellis[t];
            double[] tt1 = trellis[t - 1];
            int[] pt = psy[t];
            for (int i = 0; i < numClasses; i++) {
                double max = Double.NEGATIVE_INFINITY;
                int maxPsy = 0;
                for (int j = 0; j < numClasses; j++) {
                    features[p] = numFeatures + j;
                    double delta = potentials[i].f(features) + tt1[j];
                    if (max < delta) {
                        max = delta;
                        maxPsy = j;
                    }
                }
                tt[i] = max;
                pt[i] = maxPsy;
            }
        }

        // trace back
        int[] label = new int[n];
        double[] tn1 = trellis[n - 1];
        double max = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < numClasses; i++) {
            if (max < tn1[i]) {
                max = tn1[i];
                label[n - 1] = i;
            }
        }

        for (int t = n - 1; t-- > 0;) {
            label[t] = psy[t + 1][label[t + 1]];
        }
        return label;
    }

    /**
     * Trainer for CRF.
     */
    public static class Trainer {

        /**
         * The number of classes.
         */
        private int numClasses;
        /**
         * The number of sparse binary features.
         */
        private int numFeatures = -1;
        /**
         * The feature attributes.
         */
        private Attribute[] attributes;
        /**
         * The maximum number of leaf nodes in the tree.
         */
        private int maxLeaves = 100;
        /**
         * The learning rate of potential function.
         */
        private double eta = 1.0;
        /**
         * The number of iterations.
         */
        private int iters = 100;

        /**
         * Constructor.
         *
         * @param numClasses the maximum number of classes.
         */
        public Trainer(Attribute[] attributes, int numClasses) {
            if (numClasses < 2) {
                throw new IllegalArgumentException("Invalid number of classes: " + numClasses);
            }

            this.numClasses = numClasses;
            this.attributes = new Attribute[attributes.length + 1];
            System.arraycopy(attributes, 0, this.attributes, 0, attributes.length);
            
            String[] values = new String[numClasses + 1];
            for (int i = 0; i <= numClasses; i++) {
                values[i] = Integer.toString(i);
            }
            this.attributes[attributes.length] = new NominalAttribute("Previous Position Label", values);
        }

        /**
         * Constructor.
         *
         * @param numFeatures the number of sparse binary features.
         * @param numClasses the maximum number of classes.
         */
        public Trainer(int numFeatures, int numClasses) {
            if (numFeatures < 2) {
                throw new IllegalArgumentException("Invalid number of features: " + numClasses);
            }

            if (numClasses < 2) {
                throw new IllegalArgumentException("Invalid number of classes: " + numClasses);
            }

            this.numFeatures = numFeatures;
            this.numClasses = numClasses;
        }

        /**
         * Sets the maximum number of leaf nodes in the tree.
         *
         * @param maxLeaves the maximum number of leaf nodes in the tree.
         */
        public Trainer setMaxNodes(int maxLeaves) {
            if (maxLeaves < 2) {
                throw new IllegalArgumentException("Invalid number of leaf nodes: " + maxLeaves);
            }

            this.maxLeaves = maxLeaves;
            return this;
        }

        public Trainer setLearningRate(double eta) {
            if (eta <= 0.0) {
                throw new IllegalArgumentException("Invalid learning rate: " + eta);
            }

            this.eta = eta;
            return this;
        }

        public Trainer setNumTrees(int iters) {
            if (iters < 1) {
                throw new IllegalArgumentException("Invalid number of iterations: " + iters);
            }

            this.iters = iters;
            return this;
        }

        public CRF train(double[][][] sequences, int[][] labels) {
            CRF crf = new CRF(numClasses, eta);

            double[][] scaling = new double[sequences.length][];
            TrellisNode[][][] trellis = new TrellisNode[sequences.length][][];
            for (int i = 0; i < sequences.length; i++) {
                scaling[i] = new double[sequences[i].length];
                trellis[i] = crf.getTrellis(sequences[i]);
            }

            List<GradientTask> gradientTasks = new ArrayList<>();
            for (int i = 0; i < sequences.length; i++) {
                gradientTasks.add(new GradientTask(crf, trellis[i], scaling[i], labels[i]));
            }

            List<BoostingTask> boostingTasks = new ArrayList<>();
            for (int i = 0; i < numClasses; i++) {
                boostingTasks.add(new BoostingTask(crf.potentials[i], trellis, i));
            }

            for (int iter = 0; iter < iters; iter++) {
                try {
                    MulticoreExecutor.run(gradientTasks);
                    MulticoreExecutor.run(boostingTasks);
                } catch (Exception e) {
                    logger.error("Failed to train CRF on multi-core", e);
                }
            }

            return crf;
        }

        public CRF train(int[][][] sequences, int[][] labels) {
            CRF crf = new CRF(numFeatures, numClasses, eta);

            double[][] scaling = new double[sequences.length][];
            TrellisNode[][][] trellis = new TrellisNode[sequences.length][][];
            for (int i = 0; i < sequences.length; i++) {
                scaling[i] = new double[sequences[i].length];
                trellis[i] = crf.getTrellis(sequences[i]);
            }

            List<GradientTask> gradientTasks = new ArrayList<>();
            for (int i = 0; i < sequences.length; i++) {
                gradientTasks.add(new GradientTask(crf, trellis[i], scaling[i], labels[i]));
            }

            List<BoostingTask> boostingTasks = new ArrayList<>();
            for (int i = 0; i < numClasses; i++) {
                boostingTasks.add(new BoostingTask(crf.potentials[i], trellis, i));
            }

            for (int iter = 0; iter < iters; iter++) {
                try {
                    MulticoreExecutor.run(gradientTasks);
                    MulticoreExecutor.run(boostingTasks);
                } catch (Exception e) {
                    logger.error("Failed to train CRF on multi-core", e);
                }
            }

            return crf;
        }

        /**
         * Calculate gradients with forward-backward algorithm.
         */
        class GradientTask implements Callable<Object> {

            CRF crf;
            TrellisNode[][] trellis;
            double[] scaling;
            int[] label;

            GradientTask(CRF crf, TrellisNode[][] trellis, double[] scaling, int[] label) {
                this.crf = crf;
                this.trellis = trellis;
                this.scaling = scaling;
                this.label = label;
            }

            @Override
            public Object call() {
                crf.forward(trellis, scaling);
                crf.backward(trellis);
                crf.setTargets(trellis, scaling, label);
                return null;
            }
        }

        /**
         * Boosting potential function.
         */
        class BoostingTask implements Callable<Object> {

            int i; // training task for class i
            TreePotentialFunction potential;
            TrellisNode[][][] trellis;
            RegressionTree.Trainer trainer;
            int[][] sparseX;
            double[][] x;
            double[] y;
            int[][] order;
            int[] samples;

            BoostingTask(TreePotentialFunction potential, TrellisNode[][][] trellis, int i) {
                this.potential = potential;
                this.trellis = trellis;
                this.i = i;
                if (numFeatures <= 0) {
                    trainer = new RegressionTree.Trainer(attributes, maxLeaves);
                } else {
                    trainer = new RegressionTree.Trainer(numFeatures + numClasses + 1, maxLeaves);
                }
            
                // Create training datasets for each potential function.
                int n = 0;
                for (int l = 0; l < trellis.length; l++) {
                    n += 1 + (trellis[l].length - 1) * numClasses;
                }

                //samples = new int[n];
                y = new double[n];
                if (numFeatures <= 0) {
                    x = new double[n][];

                    for (int l = 0, m = 0; l < trellis.length; l++) {
                        TrellisNode[][] tl = trellis[l];
                        x[m++] = tl[0][i].samples[0];

                        for (int t = 1; t < trellis[l].length; t++) {
                            TrellisNode tlti = tl[t][i];
                            for (int j = 0; j < numClasses; j++) {
                                x[m++] = tlti.samples[j];
                            }
                        }
                    }
                    
                    int p = x[0].length;

                    double[] a = new double[n];
                    order = new int[p][];

                    for (int j = 0; j < p; j++) {
                        if (attributes[j] instanceof NumericAttribute) {
                            for (int l = 0; l < n; l++) {
                                a[l] = x[l][j];
                            }
                            order[j] = QuickSort.sort(a);
                        }
                    }
                } else {
                    sparseX = new int[n][];

                    for (int l = 0, m = 0; l < trellis.length; l++) {
                        TrellisNode[][] tl = trellis[l];
                        sparseX[m++] = tl[0][i].sparseSamples[0];

                        for (int t = 1; t < trellis[l].length; t++) {
                            TrellisNode tlti = tl[t][i];
                            for (int j = 0; j < numClasses; j++) {
                                sparseX[m++] = tlti.sparseSamples[j];
                            }
                        }
                    }
                }
            }

            @Override
            public Object call() {
                for (int l = 0, m = 0; l < trellis.length; l++) {
                    TrellisNode[][] tl = trellis[l];
                    y[m++] = tl[0][i].target[0];

                    for (int t = 1; t < trellis[l].length; t++) {
                        TrellisNode tlti = tl[t][i];
                        for (int j = 0; j < numClasses; j++) {
                            y[m++] = tlti.target[j];
                        }
                    }
                }
                
                /*
                int n = y.length;
                for (int l = 0; l < n; l++) {
                    if (Math.random() < 0.75) {
                        samples[l] = 1;
                    } else {
                        samples[l] = 0;
                    }
                }
                * 
                */
                
                // Perform training.
                if (x != null) {
                    RegressionTree tree = new RegressionTree(attributes, x, y, maxLeaves, 5, attributes.length, order, samples, null);
                    potential.add(tree);
                } else {
                    RegressionTree tree = new RegressionTree(numFeatures + numClasses + 1, sparseX, y, maxLeaves, 5, samples, null);
                    potential.add(tree);
                }
                
                return null;
            }
        }
    }

    /**
     * Performs forward procedure on the trellis.
     */
    private void forward(TrellisNode[][] trellis, double[] scaling) {
        int n = trellis.length; // length of sequence

        TrellisNode[] t0 = trellis[0];
        for (int i = 0; i < numClasses; i++) {
            TrellisNode t0i = t0[i];
            TreePotentialFunction pi = potentials[i];
            if (numFeatures <= 0) {
                for (int k = t0i.age; k < pi.trees.size(); k++) {
                    t0i.scores[0] += pi.eta * pi.trees.get(k).predict(t0i.samples[0]);
                }
            } else {
                for (int k = t0i.age; k < pi.trees.size(); k++) {
                    t0i.scores[0] += pi.eta * pi.trees.get(k).predict(t0i.sparseSamples[0]);
                }                
            }

            t0i.expScores[0] = Math.exp(t0i.scores[0]);
            t0i.alpha = t0i.expScores[0];
            t0i.age = pi.trees.size();
        }

        // Normalize alpha values since they increase quickly
        scaling[0] = 0.0;
        for (int i = 0; i < numClasses; i++) {
            scaling[0] += t0[i].alpha;
        }
        for (int i = 0; i < numClasses; i++) {
            t0[i].alpha /= scaling[0];
        }

        for (int t = 1; t < n; t++) {
            TrellisNode[] tt = trellis[t];
            TrellisNode[] tt1 = trellis[t-1];
            for (int i = 0; i < numClasses; i++) {
                TrellisNode tti = tt[i];
                TreePotentialFunction pi = potentials[i];
                tti.alpha = 0.0;
                for (int j = 0; j < numClasses; j++) {
                    if (numFeatures <= 0) {
                        for (int k = tti.age; k < pi.trees.size(); k++) {
                            tti.scores[j] += pi.eta * pi.trees.get(k).predict(tti.samples[j]);
                        }
                    } else {
                        for (int k = tti.age; k < pi.trees.size(); k++) {
                            tti.scores[j] += pi.eta * pi.trees.get(k).predict(tti.sparseSamples[j]);
                        }
                    }

                    tti.expScores[j] = Math.exp(tti.scores[j]);
                    tti.alpha += tti.expScores[j] * tt1[j].alpha;
                }
                tti.age = pi.trees.size();
            }

            // Normalize alpha values since they increase quickly
            scaling[t] = 0.0;
            for (int i = 0; i < numClasses; i++) {
                scaling[t] += tt[i].alpha;
            }
            for (int i = 0; i < numClasses; i++) {
                tt[i].alpha /= scaling[t];
            }
        }
    }

    /**
     * Performs backward procedure on the trellis.
     */
    private void backward(TrellisNode[][] trellis) {
        int n = trellis.length - 1;
        TrellisNode[] tn = trellis[n];
        for (int i = 0; i < numClasses; i++) {
            tn[i].beta = 1.0;
        }

        for (int t = n; t-- > 0;) {
            TrellisNode[] tt = trellis[t];
            TrellisNode[] tt1 = trellis[t+1];
            for (int i = 0; i < numClasses; i++) {
                TrellisNode tti = tt[i];
                tti.beta = 0.0;
                for (int j = 0; j < numClasses; j++) {
                    tti.beta += tt1[j].expScores[i] * tt1[j].beta;
                }
            }

            // Normalize beta values since they increase quickly
            double sum = 0.0;
            for (int i = 0; i < numClasses; i++) {
                sum += tt[i].beta;
            }
            for (int i = 0; i < numClasses; i++) {
                tt[i].beta /= sum;
            }
        }
    }

    /**
     * Create a trellis of a given sequence for forward-backward algorithm.
     *
     * @param sequence the feature sequence.
     */
    private TrellisNode[][] getTrellis(double[][] sequence) {
        TrellisNode[][] trellis = new TrellisNode[sequence.length][numClasses];

        TrellisNode[] t0 = trellis[0];
        for (int i = 0; i < numClasses; i++) {
            t0[i] = new TrellisNode(false);
            t0[i].samples[0] = featureset(sequence[0], numClasses);
        }

        for (int t = 1; t < sequence.length; t++) {
            trellis[t][0] = new TrellisNode(false);
            TrellisNode tt0 = trellis[t][0];
            for (int j = 0; j < numClasses; j++) {
                tt0.samples[j] = featureset(sequence[t], j);
            }

            for (int i = 1; i < numClasses; i++) {
                trellis[t][i] = new TrellisNode(false);
                TrellisNode tti = trellis[t][i];
                System.arraycopy(tt0.samples, 0, tti.samples, 0, numClasses);
            }
        }

        return trellis;
    }

    /**
     * Create a trellis of a given sequence for forward-backward algorithm.
     *
     * @param sequence the feature sequence.
     */
    private TrellisNode[][] getTrellis(int[][] sequence) {
        TrellisNode[][] trellis = new TrellisNode[sequence.length][numClasses];

        TrellisNode[] t0 = trellis[0];
        for (int i = 0; i < numClasses; i++) {
            t0[i] = new TrellisNode(true);
            t0[i].sparseSamples[0] = featureset(sequence[0], numClasses);
        }

        for (int t = 1; t < sequence.length; t++) {
            trellis[t][0] = new TrellisNode(true);
            TrellisNode tt0 = trellis[t][0];
            for (int j = 0; j < numClasses; j++) {
                tt0.sparseSamples[j] = featureset(sequence[t], j);
            }

            for (int i = 1; i < numClasses; i++) {
                trellis[t][i] = new TrellisNode(true);
                TrellisNode tti = trellis[t][i];
                System.arraycopy(tt0.sparseSamples, 0, tti.sparseSamples, 0, numClasses);
            }
        }

        return trellis;
    }

    /**
     * Set training targets based on results of forward-backward
     */
    private void setTargets(TrellisNode[][] trellis, double[] scaling, int[] label) {
        TrellisNode[] t0 = trellis[0];

        // Finding the normalizer for our first 'column' in the matrix
        double normalizer = 0.0;
        for (int i = 0; i < numClasses; i++) {
            normalizer += t0[i].expScores[0] * t0[i].beta;
        }

        for (int i = 0; i < numClasses; i++) {
            if (label[0] == i) {
                t0[i].target[0] = 1 - t0[i].expScores[0] * t0[i].beta / normalizer;
            } else {
                t0[i].target[0] = -t0[i].expScores[0] * t0[i].beta / normalizer;
            }
        }

        for (int t = 1; t < label.length; t++) {
            normalizer = 0.0;
            TrellisNode[] tt = trellis[t];
            TrellisNode[] tt1 = trellis[t-1];
            for (int i = 0; i < numClasses; i++) {
                normalizer += tt[i].alpha * tt[i].beta;
            }
            normalizer *= scaling[t];

            for (int i = 0; i < numClasses; i++) {
                TrellisNode tti = tt[i];
                for (int j = 0; j < numClasses; j++) {
                    if (label[t] == i && label[t - 1] == j) {
                        tti.target[j] = 1 - tti.expScores[j] * tt1[j].alpha * tti.beta / normalizer;
                    } else {
                        tti.target[j] = -tti.expScores[j] * tt1[j].alpha * tti.beta / normalizer;
                    }
                }
            }
        }
    }
}
