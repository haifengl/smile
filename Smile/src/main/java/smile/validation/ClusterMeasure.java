/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/

package smile.validation;

/**
 * An abstract interface to measure the clustering performance.
 *
 * @author Haifeng Li
 */
public interface ClusterMeasure {

    /**
     * Returns an index to measure the quality of clustering.
     * @param y1 the cluster labels.
     * @param y2 the alternative cluster labels.
     */
    public double measure(int[] y1, int[] y2);

}
