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

import smile.math.MathEx;
import smile.math.distance.Distance;
import smile.math.distance.EuclideanDistance;
import smile.math.distance.Metric;
import smile.neighbor.CoverTree;
import smile.neighbor.KDTree;
import smile.neighbor.KNNSearch;
import smile.neighbor.LinearSearch;
import smile.neighbor.Neighbor;
import smile.util.IntSet;

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
public class KNN<T> implements SoftClassifier<T> {
    private static final long serialVersionUID = 2L;

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
     * The class labels.
     */
    private IntSet labels;
    /**
     * Constructor.
     * @param knn k-nearest neighbor search data structure of training instances.
     * @param y training labels.
     * @param k the number of neighbors for classification.
     */
    public KNN(KNNSearch<T, T> knn, int[] y, int k) {
        this.knn = knn;
        this.k = k;
        this.y = y;
        labels = ClassLabels.fit(y).labels;
    }

    /**
     * Learn the 1-NN classifier.
     * @param x training samples.
     * @param y training labels.
     * @param distance the distance measure for finding nearest neighbors.
     */
    public static <T> KNN<T> fit(T[] x, int[] y, Distance<T> distance) {
        return fit(x, y, distance, 1);
    }

    /**
     * Learn the K-NN classifier.
     * @param k the number of neighbors.
     * @param x training samples.
     * @param y training labels.
     * @param distance the distance measure for finding nearest neighbors.
     */
    public static <T> KNN<T> fit(T[] x, int[] y, Distance<T> distance, int k) {
        if (x.length != y.length) {
            throw new IllegalArgumentException(String.format("The sizes of X and Y don't match: %d != %d", x.length, y.length));
        }

        if (k < 1) {
            throw new IllegalArgumentException("Illegal k = " + k);
        }

        KNNSearch<T, T> knn;
        if (distance instanceof Metric) {
            knn = new CoverTree<>(x, (Metric<T>) distance);
        } else {
            knn = new LinearSearch<>(x, distance);
        }

        return new KNN<>(knn, y, k);
    }

    /**
     * Learn the 1-NN classifier.
     * @param x training samples.
     * @param y training labels.
     */
    public static KNN<double[]> fit(double[][] x, int[] y) {
        return fit(x, y, 1);
    }

    /**
     * Learn the K-NN classifier.
     * @param k the number of neighbors for classification.
     * @param x training samples.
     * @param y training labels.
     */
    public static KNN<double[]> fit(double[][] x, int[] y, int k) {
        if (x.length != y.length) {
            throw new IllegalArgumentException(String.format("The sizes of X and Y don't match: %d != %d", x.length, y.length));
        }

        if (k < 1) {
            throw new IllegalArgumentException("Illegal k = " + k);
        }

        KNNSearch<double[], double[]> knn;
        if (x[0].length < 10) {
            knn = new KDTree<>(x, x);
        } else {
            knn = new CoverTree<>(x, new EuclideanDistance());
        }
        
        return new KNN<>(knn, y, k);
    }

    @Override
    public int predict(T x) {
        Neighbor<T,T>[] neighbors = knn.knn(x, k);
        if (k == 1) {
            return y[neighbors[0].index];
        }

        int[] count = new int[labels.size()];
        for (int i = 0; i < k; i++) {
            count[labels.indexOf(y[neighbors[i].index])]++;
        }

        return labels.valueOf(MathEx.whichMax(count));
    }

    @Override
    public int predict(T x, double[] posteriori) {
        Neighbor<T,T>[] neighbors = knn.knn(x, k);
        if (k == 1) {
            return y[neighbors[0].index];
        }

        int[] count = new int[labels.size()];
        for (int i = 0; i < k; i++) {
            count[labels.indexOf(y[neighbors[i].index])]++;
        }

        for (int i = 0; i < count.length; i++) {
            posteriori[i] = (double) count[i] / k;
        }

        return labels.valueOf(MathEx.whichMax(count));
    }
}
