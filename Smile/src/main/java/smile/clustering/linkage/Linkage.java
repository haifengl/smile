/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/

package smile.clustering.linkage;

/**
 * A measure of dissimilarity between clusters (i.e. sets of observations).
 * 
 * <h2>References</h2>
 * <ol>
 * <li> Anil K. Jain, Richard C. Dubes. Algorithms for clustering data. 1988.</li> 
 * </ol>
 * 
 * @see smile.clustering.HierarchicalClustering
 *
 * @author Haifeng Li
 */
public abstract class Linkage {
    /**
     * The proximity matrix to store the pair-wise distance measure as
     * dissimilarity between clusters. To save space, we only need the
     * lower half of matrix. During the clustering, this matrix will be
     * updated to reflect the dissimilarity of merged clusters.
     */
    double[][] proximity;

    /**
     * Returns the proximity matrix.
     */
    public double[][] getProximity() {
        return proximity;
    }

    /**
     * Returns the distance/dissimilarity between two clusters/objects, which
     * are indexed by integers.
     */
    double d(int i, int j) {
        if (i > j)
            return proximity[i][j];
        else
            return proximity[j][i];
    }

    /**
     * Merge two clusters into one and update the proximity matrix.
     * @param i cluster id.
     * @param j cluster id.
     */
    public abstract void merge(int i, int j);
}
