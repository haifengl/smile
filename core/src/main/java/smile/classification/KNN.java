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

package smile.classification;

import java.io.Serializable;
import java.util.Arrays;
import smile.math.Math;
import smile.math.distance.Distance;
import smile.math.distance.EuclideanDistance;
import smile.math.distance.Metric;
import smile.neighbor.CoverTree;
import smile.neighbor.KDTree;
import smile.neighbor.KNNSearch;
import smile.neighbor.LinearSearch;
import smile.neighbor.Neighbor;

/**
 * K-nearest neighbor classifier. The k-nearest neighbor algorithm (k-NN) is
 * a method for classifying objects by a majority vote of its neighbors,
 * with the object being assigned to the class most common amongst its k
 * nearest neighbors (k is a positive integer, typically small).
 * k-NN is a type of instance-based learning, or lazy learning where the
 * function is only approximated locally and all computation
 * is deferred until classification.
 * <p>
 * The best choice of k depends upon the data; generally, larger values of
 * k reduce the effect of noise on the classification, but make boundaries
 * between classes less distinct. A good k can be selected by various
 * heuristic techniques, e.g. cross-validation. In binary problems, it is
 * helpful to choose k to be an odd number as this avoids tied votes.
 * <p>
 * A drawback to the basic majority voting classification is that the classes
 * with the more frequent instances tend to dominate the prediction of the
 * new object, as they tend to come up in the k nearest neighbors when
 * the neighbors are computed due to their large number. One way to overcome
 * this problem is to weight the classification taking into account the
 * distance from the test point to each of its k nearest neighbors.
 * <p>
 * Often, the classification accuracy of k-NN can be improved significantly
 * if the distance metric is learned with specialized algorithms such as
 * Large Margin Nearest Neighbor or Neighborhood Components Analysis.
 * <p>
 * Nearest neighbor rules in effect compute the decision boundary in an
 * implicit manner. It is also possible to compute the decision boundary
 * itself explicitly, and to do so in an efficient manner so that the
 * computational complexity is a function of the boundary complexity.
 * <p>
 * The nearest neighbor algorithm has some strong consistency results. As
 * the amount of data approaches infinity, the algorithm is guaranteed to
 * yield an error rate no worse than twice the Bayes error rate (the minimum 
 * achievable error rate given the distribution of the data). k-NN is
 * guaranteed to approach the Bayes error rate, for some value of k (where k
 * increases as a function of the number of data points).
 * 
 * @author Haifeng Li
 */
public class KNN<T> implements SoftClassifier<T>, Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * The data structure for nearest neighbor search.
     */
    private KNNSearch<T, T> knn;
    /**
     * The labels of training samples.
     */
    private int[] y;
    /**
     * The number of neighbors for decision.
     */
    private int k;
    /**
     * The number of classes.
     */
    private int c;

    /**
     * Trainer for KNN classifier.
     */
    public static class Trainer<T> extends ClassifierTrainer<T> {
        /**
         * The number of neighbors.
         */
        private int k;
        /**
         * The distance functor.
         */
        private Distance<T> distance;

        /**
         * Constructor.
         * 
         * @param distance the distance metric functor.
         * @param k the number of neighbors.
         */
        public Trainer(Distance<T> distance, int k) {
            if (k < 1) {
                throw new IllegalArgumentException("Invalid k of k-NN: " + k);
            }
            
            this.distance = distance;
            this.k = k;
        }
        
        @Override
        public KNN<T> train(T[] x, int[] y) {
            return new KNN<>(x, y, distance, k);
        }
    }
    
    /**
     * Constructor.
     * @param knn k-nearest neighbor search data structure of training instances.
     * @param y training labels in [0, c), where c is the number of classes.
     * @param k the number of neighbors for classification.
     */
    public KNN(KNNSearch<T, T> knn, int[] y, int k) {
        this.knn = knn;
        this.k = k;
        this.y = y;
        
        // class label set.
        int[] labels = Math.unique(y);
        Arrays.sort(labels);
        
        for (int i = 0; i < labels.length; i++) {
            if (labels[i] < 0) {
                throw new IllegalArgumentException("Negative class label: " + labels[i]); 
            }
            
            if (i > 0 && labels[i] - labels[i-1] > 1) {
                throw new IllegalArgumentException("Missing class: " + labels[i]+1);                 
            }
        }

        c = labels.length;
        if (c < 2) {
            throw new IllegalArgumentException("Only one class.");            
        }
    }

    /**
     * Constructor. By default, this is a 1-NN classifier.
     * @param x training samples.
     * @param y training labels in [0, c), where c is the number of classes.
     * @param distance the distance measure for finding nearest neighbors.
     */
    public KNN(T[] x, int[] y, Distance<T> distance) {
        this(x, y, distance, 1);
    }

    /**
     * Learn the K-NN classifier from data of any generalized type with a given
     * distance definition.
     * @param k the number of neighbors for classification.
     * @param x training samples.
     * @param y training labels in [0, c), where c is the number of classes.
     * @param distance the distance measure for finding nearest neighbors.
     */
    public KNN(T[] x, int[] y, Distance<T> distance, int k) {
        if (x.length != y.length) {
            throw new IllegalArgumentException(String.format("The sizes of X and Y don't match: %d != %d", x.length, y.length));
        }

        if (k < 1) {
            throw new IllegalArgumentException("Illegal k = " + k);
        }
        
        // class label set.
        int[] labels = Math.unique(y);
        Arrays.sort(labels);
        
        for (int i = 0; i < labels.length; i++) {
            if (labels[i] < 0) {
                throw new IllegalArgumentException("Negative class label: " + labels[i]); 
            }
            
            if (i > 0 && labels[i] - labels[i-1] > 1) {
                throw new IllegalArgumentException("Missing class: " + labels[i]+1);                 
            }
        }

        c = labels.length;
        if (c < 2) {
            throw new IllegalArgumentException("Only one class.");            
        }
        
        this.y = y;
        this.k = k;
        if (distance instanceof Metric) {
            knn = new CoverTree<>(x, (Metric<T>) distance);
        } else {
            knn = new LinearSearch<>(x, distance);
        }
    }

    /**
     * Learn the 1-NN classifier from data of type double[].
     * @param x the training samples.
     * @param y training labels in [0, c), where c is the number of classes.
     */
    public static KNN<double[]> learn(double[][] x, int[] y) {
        return learn(x, y, 1);
    }

    /**
     * Learn the K-NN classifier from data of type double[].
     * @param k the number of neighbors for classification.
     * @param x training samples.
     * @param y training labels in [0, c), where c is the number of classes.
     */
    public static KNN<double[]> learn(double[][] x, int[] y, int k) {
        if (x.length != y.length) {
            throw new IllegalArgumentException(String.format("The sizes of X and Y don't match: %d != %d", x.length, y.length));
        }

        if (k < 1) {
            throw new IllegalArgumentException("Illegal k = " + k);
        }

        KNNSearch<double[], double[]> knn = null;
        if (x[0].length < 10) {
            knn = new KDTree<>(x, x);
        } else {
            knn = new CoverTree<>(x, new EuclideanDistance());
        }
        
        return new KNN<>(knn, y, k);
    }

    @Override
    public int predict(T x) {
        return predict(x, null);
    }

    @Override
    public int predict(T x, double[] posteriori) {
        if (posteriori != null && posteriori.length != c) {
            throw new IllegalArgumentException(String.format("Invalid posteriori vector size: %d, expected: %d", posteriori.length, c));
        }

        Neighbor<T,T>[] neighbors = knn.knn(x, k);
        if (k == 1) {
            return y[neighbors[0].index];
        }

        int[] count = new int[c];
        for (int i = 0; i < k; i++) {
            count[y[neighbors[i].index]]++;
        }

        if (posteriori != null) {
            for (int i = 0; i < c; i++) {
                posteriori[i] = (double) count[i] / k;
            }
        }
        
        int max = 0;
        int idx = 0;
        for (int i = 0; i < c; i++) {
            if (count[i] > max) {
                max = count[i];
                idx = i;
            }
        }

        return idx;
    }
}
