/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/

package smile.clustering.linkage;

/**
 * Single linkage. The distance between groups is defined as the distance
 * between the closest pair of objects, one from each group.
 * A drawback of this method is the so-called chaining phenomenon: clusters
 * may be forced together due to single elements being close to each other,
 * even though many of the elements in each cluster may be very distant to
 * each other.
 * <p>
 * Single linkage clustering is essentially the same as Kruskal's algorithm
 * for minimum spanning trees. However, in single linkage clustering, the
 * order in which clusters are formed is important, while for minimum spanning
 * trees what matters is the set of pairs of points that form distances chosen
 * by the algorithm.
 * 
 * @author Haifeng Li
 */
public class SingleLinkage extends Linkage {
    /**
     * Constructor.
     * @param proximity  The proximity matrix to store the distance measure of
     * dissimilarity. To save space, we only need the lower half of matrix.
     */
    public SingleLinkage(double[][] proximity) {
        this.proximity = proximity;
    }

    @Override
    public String toString() {
        return "single linkage";
    }

    @Override
    public void merge(int i, int j) {
        for (int k = 0; k < i; k++) {
            proximity[i][k] = Math.min(proximity[i][k], d(j, k));
        }

        for (int k = i+1; k < proximity.length; k++) {
            proximity[k][i] = Math.min(proximity[k][i], d(j, k));
        }
    }
}
