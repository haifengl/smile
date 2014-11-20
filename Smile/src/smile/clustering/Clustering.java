/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/
package smile.clustering;

/**
 * Clustering interface.
 * 
 * @param <T> the type of input object.
 * 
 * @author Haifeng Li
 */
public interface Clustering <T> {
    /**
     * Cluster label for outliers or noises.
     */
    public static final int OUTLIER = Integer.MAX_VALUE;
    
    /**
     * Cluster a new instance.
     * @param x a new instance.
     * @return the cluster label.
     */
    public int predict(T x);
}
