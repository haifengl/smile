/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/

package smile.projection;

/**
 * A projection is a kind of feature extraction technique that transforms data
 * from the input space to a feature space, linearly or nonlinearly. Often,
 * projections are used to reduce dimensionality, for example PCA and random
 * projection. However, kernel-based methods, e.g. Kernel PCA, can actually map
 * the data into a much higher dimensional space.
 *
 * @author Haifeng Li
 */
public interface Projection<T> {
    /**
     * Project a data point to the feature space.
     */
    public double[] project(T x);

    /**
     * Project a set of data toe the feature space.
     */
    public double[][] project(T[] x);
}
