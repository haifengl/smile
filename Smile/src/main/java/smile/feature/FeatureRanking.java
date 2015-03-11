/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/

package smile.feature;

/**
 * Univariate feature ranking metric.
 * 
 * @author Haifeng Li
 */
public interface FeatureRanking {
    
    /**
     * Univariate feature ranking. Note that this method actually does NOT rank
     * the features. It just returns the metric values of each feature. The
     * caller can then rank and select features.
     * 
     * @param x a n-by-p matrix of n instances with p features.
     * @param y class labels in [0, k), where k is the number of classes.
     * @return the metric values of each feature.
     */
    public double[] rank(double[][] x, int[] y);
    
}
