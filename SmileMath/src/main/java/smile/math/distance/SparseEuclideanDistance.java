/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/

package smile.math.distance;

import java.util.Iterator;
import smile.math.SparseArray;

/**
 * Euclidean distance. Use getInstance() to get the standard unweighted
 * Euclidean distance. Or create an instance with a specified
 * weight vector. For float or double arrays, missing values (i.e. NaN)
 * are also handled. Also support sparse arrays of which zeros are excluded
 * to save space.
 *
 * @author Haifeng Li
 */
public class SparseEuclideanDistance implements Metric<SparseArray> {

    /**
     * The weights used in weighted distance.
     */
    private double[] weight = null;

    /**
     * Constructor. Standard (unweighted) Euclidean distance.
     */
    public SparseEuclideanDistance() {
    }

    /**
     * Constructor with a given weight vector.
     * 
     * @param weight the weight vector.
     */
    public SparseEuclideanDistance(double[] weight) {
        for (int i = 0; i < weight.length; i++) {
            if (weight[i] < 0)
                throw new IllegalArgumentException(String.format("Weight has to be nonnegative: %f", weight[i]));
        }

        this.weight = weight;
    }

    @Override
    public String toString() {
        if (weight != null)
            return "weighted Euclidean distance";
        else
            return "Euclidean distance";
    }

    @Override
    public double d(SparseArray x, SparseArray y) {
        if (x.isEmpty())
            throw new IllegalArgumentException("List x is empty.");

        if (y.isEmpty())
            throw new IllegalArgumentException("List y is empty.");

        Iterator<SparseArray.Entry> iterX = x.iterator();
        Iterator<SparseArray.Entry> iterY = y.iterator();

        SparseArray.Entry a = iterX.hasNext() ? iterX.next() : null;
        SparseArray.Entry b = iterY.hasNext() ? iterY.next() : null;

        double dist = 0.0;

        if (weight == null) {
            while (a != null && b != null) {
                if (a.i < b.i) {
                    double d = a.x;
                    dist += d * d;

                    a = iterX.hasNext() ? iterX.next() : null;
                } else if (a.i > b.i) {
                    double d = b.x;
                    dist += d * d;

                    b = iterY.hasNext() ? iterY.next() : null;
                } else {
                    double d = a.x - b.x;
                    dist += d * d;

                    a = iterX.hasNext() ? iterX.next() : null;
                    b = iterY.hasNext() ? iterY.next() : null;
                }
            }

            while (a != null) {
                double d = a.x;
                dist += d * d;

                a = iterX.hasNext() ? iterX.next() : null;
            }

            while (b != null) {
                double d = b.x;
                dist += d * d;

                b = iterY.hasNext() ? iterY.next() : null;
            }
        } else {
            while (a != null && b != null) {
                if (a.i < b.i) {
                    double d = a.x;
                    dist += weight[a.i] * d * d;

                    a = iterX.hasNext() ? iterX.next() : null;
                } else if (a.i > b.i) {
                    double d = b.x;
                    dist += weight[b.i] * d * d;

                    b = iterY.hasNext() ? iterY.next() : null;
                } else {
                    double d = a.x - b.x;
                    dist += weight[a.i] * d * d;

                    a = iterX.hasNext() ? iterX.next() : null;
                    b = iterY.hasNext() ? iterY.next() : null;
                }
            }

            while (a != null) {
                double d = a.x;
                dist += weight[a.i] * d * d;

                a = iterX.hasNext() ? iterX.next() : null;
            }

            while (b != null) {
                double d = b.x;
                dist += weight[b.i] * d * d;

                b = iterY.hasNext() ? iterY.next() : null;
            }
        }

        return Math.sqrt(dist);
    }
}
