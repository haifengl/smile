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

package smile.sequence;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Properties;
import java.util.stream.IntStream;
import smile.base.cart.CART;
import smile.base.cart.Loss;
import smile.data.DataFrame;
import smile.data.Tuple;
import smile.data.measure.NominalScale;
import smile.data.type.DataTypes;
import smile.data.type.StructField;
import smile.data.type.StructType;
import smile.data.vector.IntVector;
import smile.math.MathEx;
import smile.regression.RegressionTree;
import smile.util.Strings;

/**
 * First-order linear conditional random field. A conditional random field is a
 * type of discriminative undirected probabilistic graphical model. It is most
 * often used for labeling or parsing of sequential data.
 *
 * A CRF is a Markov random field that was trained discriminatively.
 * Therefore it is not necessary to model the distribution over always
 * observed variables, which makes it possible to include arbitrarily
 * complicated features of the observed variables into the model.
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
 * <li> J. Lafferty, A. McCallum and F. Pereira. Conditional random fields: Probabilistic models for segmenting and labeling sequence data. ICML, 2001.</li>
 * <li> Thomas G. Dietterich, Guohua Hao, and Adam Ashenfelter. Gradient Tree Boosting for Training Conditional Random Fields. JMLR, 2008.</li>
 * </ol>
 *
 * @author Haifeng Li
 */
public class CRF implements Serializable {
    private static final long serialVersionUID = 2L;
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(CRF.class);

    /** The schema of (x, s_j). */
    private StructType schema;
    /**
     * The potential functions for each class.
     */
    private RegressionTree[][] potentials;
    /**
     * The learning rate.
     */
    private double shrinkage;

    /**
     * Constructor.
     *
     * @param schema the schema of features.
     * @param potentials the potential functions.
     * @param shrinkage the learning rate.
     */
    public CRF(StructType schema, RegressionTree[][] potentials, double shrinkage) {
        this.potentials = potentials;
        this.shrinkage = shrinkage;

        int k = potentials.length;
        NominalScale scale = new NominalScale(IntStream.range(0, k+1).mapToObj(String::valueOf).toArray(String[]::new));
        StructField field = new StructField("s(t-1)", DataTypes.IntegerType, scale);

        int length = schema.length();
        StructField[] fields = new StructField[length + 1];
        System.arraycopy(schema.fields(), 0, fields, 0, length);
        fields[length] = field;
        this.schema = new StructType(fields);
    }

    /**
     * Labels sequence with Viterbi algorithm. Viterbi algorithm
     * returns the whole sequence label that has the maximum probability,
     * which makes sense in applications (e.g.part-of-speech tagging) that
     * require coherent sequential labeling. The forward-backward algorithm
     * labels a sequence by individual prediction on each position.
     * This usually produces better accuracy although the results may not
     * be coherent.
     */
    public int[] viterbi(Tuple[] x) {
        int n = x.length;
        int k = potentials.length;

        double[][] trellis = new double[n][k];
        int[][] psy = new int[n][k];
        double[] delta = new double[k];

        // forward
        double[] t0 = trellis[0];
        int[] p0 = psy[0];

        Tuple x0 = extend(x[0], k);
        Tuple[] xt = new Tuple[k];

        for (int j = 0; j < k; j++) {
            t0[j] = f(potentials[j], x0);
            p0[j] = 0;
        }

        for (int t = 1; t < n; t++) {
            double[] tt = trellis[t];
            double[] tt1 = trellis[t - 1];
            int[] pt = psy[t];

            for (int j = 0; j < k; j++) {
                xt[j] = extend(x[t], j);
            }

            for (int i = 0; i < k; i++) {
                RegressionTree[] pi = potentials[i];
                for (int j = 0; j < k; j++) {
                    delta[j] = f(pi, xt[j]) + tt1[j];
                }
                pt[i] = MathEx.whichMax(delta);
                tt[i] = delta[pt[i]];
            }
        }

        // trace back
        int[] label = new int[n];
        label[n-1] = MathEx.whichMax(trellis[n - 1]);
        for (int t = n - 1; t-- > 0;) {
            label[t] = psy[t + 1][label[t + 1]];
        }
        return label;
    }

    /**
     * Returns the most likely label sequence given the feature sequence by the
     * forward-backward algorithm.
     *
     * @param x a sequence.
     * @return the most likely label sequence.
     */
    public int[] predict(Tuple[] x) {
        int n = x.length;
        int k = potentials.length;

        Trellis trellis = new Trellis(n, k);
        f(x, trellis);

        double[] scaling = new double[n];
        trellis.forward(scaling);
        trellis.backward();

        int[] label = new int[n];
        double[] p = new double[k];
        for (int i = 0; i < n; i++) {
            Trellis.Cell[] ti = trellis.table[i];
            for (int j = 0; j < k; j++) {
                Trellis.Cell tij = ti[j];
                p[j] = tij.alpha * tij.beta;
            }

            label[i] = MathEx.whichMax(p);
        }

        return label;
    }

    /** Calculates the potential function values. */
    private void f(Tuple[] x, Trellis trellis) {
        int n = x.length;
        int k = potentials.length;

        Tuple x0 = extend(x[0], k);
        Tuple[] xt = new Tuple[k];

        for (int i = 0; i < k; i++) {
            trellis.table[0][i].expf[0] = f(potentials[i], x0);
        }

        for (int t = 1; t < n; t++) {
            for (int j = 0; j < k; j++) {
                xt[j] = extend(x[t], j);
            }

            for (int i = 0; i < k; i++) {
                for (int j = 0; j < k; j++) {
                    trellis.table[t][i].expf[j] = f(potentials[i], xt[j]);
                }
            }
        }
    }

    /** Calculates the potential function value. */
    private double f(RegressionTree[] potential, Tuple x) {
        double F = 0.0;
        for (RegressionTree tree : potential) {
            F += shrinkage * tree.predict(x);
        }
        return Math.exp(F);
    }

    /**
     * Fits a CRF model.
     * @param sequences the training data.
     * @param labels the training sequence labels.
     */
    public static CRF fit(Tuple[][] sequences, int[][] labels) {
        return fit(sequences, labels, new Properties());
    }

    /**
     * Fits a CRF model.
     * @param sequences the training data.
     * @param labels the training sequence labels.
     */
    public static CRF fit(Tuple[][] sequences, int[][] labels, Properties prop) {
        int ntrees = Integer.valueOf(prop.getProperty("smile.crf.trees", "100"));
        int maxDepth = Integer.valueOf(prop.getProperty("smile.crf.max.depth", "20"));
        int maxNodes = Integer.valueOf(prop.getProperty("smile.crf.max.nodes", "100"));
        int nodeSize = Integer.valueOf(prop.getProperty("smile.crf.node.size", "5"));
        double shrinkage = Double.valueOf(prop.getProperty("smile.crf.shrinkage", "1.0"));
        return fit(sequences, labels, ntrees, maxDepth, maxNodes, nodeSize, shrinkage);
    }

    /**
     * Fits a CRF model.
     * @param sequences the training data.
     * @param labels the training sequence labels.
     * @param ntrees the number of trees/iterations.
     * @param maxDepth the maximum depth of the tree.
     * @param maxNodes the maximum number of leaf nodes in the tree.
     * @param nodeSize  the number of instances in a node below which the tree will
     *                  not split, setting nodeSize = 5 generally gives good results.
     * @param shrinkage the shrinkage parameter in (0, 1] controls the learning rate of procedure.
     */
    public static CRF fit(Tuple[][] sequences, int[][] labels, int ntrees, int maxDepth, int maxNodes, int nodeSize, double shrinkage) {
        int k = MathEx.max(labels) + 1;
        double[][] scaling = new double[sequences.length][];
        Trellis[] trellis = new Trellis[sequences.length];
        for (int i = 0; i < sequences.length; i++) {
            scaling[i] = new double[sequences[i].length];
            trellis[i] = new Trellis(sequences[i].length, k);
        }

        // training data
        int n = Arrays.stream(sequences).mapToInt(s -> s.length).map(ni -> 1 + (ni - 1) * k).sum();
        ArrayList<Tuple> x = new ArrayList<>(n);
        int[] state = new int[n];

        for (int s = 0, l = 0; s < sequences.length; s++) {
            Tuple[] sequence = sequences[s];
            x.add(sequence[0]);
            state[l++] = k;
            for (int i = 1; i < sequence.length; i++) {
                for (int j = 0; j < k; j++) {
                    x.add(sequence[i]);
                    state[l++] = j;
                }
            }
        }

        NominalScale scale = new NominalScale(IntStream.range(0, k+1).mapToObj(String::valueOf).toArray(String[]::new));
        DataFrame data = DataFrame.of(x)
                .merge(IntVector.of(new StructField("s(t-1)", DataTypes.IntegerType, scale), state));

        StructField field = new StructField("residual", DataTypes.DoubleType);
        RegressionTree[][] potentials = new RegressionTree[k][ntrees];

        double[][] h = new double[k][n]; // boost tree output.
        double[][] response = new double[k][n]; // response for boosting tree.
        Loss[] loss = new Loss[k];
        for (int i = 0; i < k; i++) {
            loss[i] = new PotentialLoss(response[i]);
        }

        int[] samples = new int[n];
        Arrays.fill(samples, 1);
        int[][] order = CART.order(data);

        for (int iter = 0; iter < ntrees; iter++) {
            logger.info("Training {} tree", Strings.ordinal(iter+1));

            // set exp(F) in the trellis
            IntStream.range(0, k).parallel().forEach(j -> {
                double[] f = h[j];
                for (int s = 0, l = 0; s < sequences.length; s++) {
                    Trellis grid = trellis[s];
                    grid.table[0][j].expf[0] = Math.exp(f[l++]);
                    for (int t = 1; t < grid.table.length; t++) {
                        for (int i = 0; i < k; i++) {
                            grid.table[t][j].expf[i] = Math.exp(f[l++]);
                        }
                    }
                }
            });

            // gradient
            IntStream.range(0, sequences.length).parallel().forEach(s -> {
                trellis[s].forward(scaling[s]);
                trellis[s].backward();
                trellis[s].gradient(scaling[s], labels[s]);
            });

            // copy gradient back to response
            IntStream.range(0, k).parallel().forEach(j -> {
                double[] r = response[j];
                for (int s = 0, l = 0; s < sequences.length; s++) {
                    Trellis grid = trellis[s];
                    r[l++] = grid.table[0][j].residual[0];
                    for (int t = 1; t < grid.table.length; t++) {
                        for (int i = 0; i < k; i++) {
                            r[l++] = grid.table[t][j].residual[i];
                        }
                    }
                }
            });

            for (int j = 0; j < k; j++) {
                RegressionTree tree = new RegressionTree(data, loss[j], field, maxDepth, maxNodes, nodeSize, data.ncols(), samples, order);
                potentials[j][iter] = tree;

                double[] hj = h[j];
                for (int i = 0; i < n; i++) {
                    hj[i] += shrinkage * tree.predict(data.get(i));
                }
            }
        }

        return new CRF(sequences[0][0].schema(), potentials, shrinkage);
    }

    static class PotentialLoss implements Loss {
        /** The response variable. */
        double[] response;

        PotentialLoss(double[] response) {
            this.response = response;
        }

        @Override
        public double output(int[] nodeSamples, int[] sampleCount) {
            int n = 0;
            double output = 0.0;
            for (int i : nodeSamples) {
                n += sampleCount[i];
                output += response[i] * sampleCount[i];
            }

            return output / n;
        }

        @Override
        public double intercept(double[] $y) {
            return 0;
        }

        @Override
        public double[] response() {
            return response;
        }

        @Override
        public double[] residual() {
            throw new IllegalStateException();
        }
    }

    /** Extends a feature vector with previous element's state. */
    Tuple extend(Tuple x, int state) {
        return new Tuple() {
            @Override
            public StructType schema() {
                return schema;
            }

            @Override
            public Object get(int j) {
                return j == x.length() ? state : x.get(j);
            }

            @Override
            public int getInt(int j) {
                return j == x.length() ? state : x.getInt(j);
            }
        };
    }
}
