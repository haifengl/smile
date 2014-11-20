/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/

package smile.clustering.linkage;

/**
 * Weighted Pair Group Method with Arithmetic mean. WPGMA down-weights the
 * largest group by giving equal weights to the two branches of the dendrogram
 * that are about to fuse.
 * <p>
 * Note that the terms weighted and unweighted refer to the final result,
 * not the math by which it is achieved. Thus the simple averaging in WPGMA
 * produces a weighted result, and the proportional averaging in UPGMA produces
 * an unweighted result.
 * 
 * @author Haifeng Li
 */
public class WPGMALinkage extends Linkage {
    /**
     * Constructor.
     * @param proximity  the proximity matrix to store the distance measure of
     * dissimilarity. To save space, we only need the lower half of matrix.
     */
    public WPGMALinkage(double[][] proximity) {
        this.proximity = proximity;
    }

    @Override
    public String toString() {
        return "WPGMA linkage";
    }

    @Override
    public void merge(int i, int j) {
        for (int k = 0; k < i; k++) {
            proximity[i][k] = (proximity[i][k] + d(j, k)) / 2;
        }

        for (int k = i+1; k < proximity.length; k++) {
            proximity[k][i] = (proximity[k][i] + d(j, k)) / 2;
        }
    }
}
