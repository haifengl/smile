/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/

package smile.clustering.linkage;

/**
 * Complete linkage. This is the opposite of single linkage. Distance between
 * groups is now defined as the distance between the most distant pair of
 * objects, one from each group.
 * 
 * @author Haifeng Li
 */
public class CompleteLinkage extends Linkage {
    /**
     * Constructor.
     * @param proximity  the proximity matrix to store the distance measure of
     * dissimilarity. To save space, we only need the lower half of matrix.
     */
    public CompleteLinkage(double[][] proximity) {
        this.proximity = proximity;
    }

    @Override
    public String toString() {
        return "complete linkage";
    }

    @Override
    public void merge(int i, int j) {
        for (int k = 0; k < i; k++) {
            proximity[i][k] = Math.max(proximity[i][k], d(j, k));
        }

        for (int k = i+1; k < proximity.length; k++) {
            proximity[k][i] = Math.max(proximity[k][i], d(j, k));
        }
    }
}
