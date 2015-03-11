/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/

package smile.clustering.linkage;

/**
 * Weighted Pair Group Method using Centroids (also known as median linkage).
 * The distance between two clusters is the Euclidean distance between their
 * weighted centroids. Only valid for Euclidean distance based proximity matrix.
 * 
 * @author Haifeng Li
 */
public class WPGMCLinkage extends Linkage {
    /**
     * Constructor.
     * @param proximity  the proximity matrix to store the distance measure of
     * dissimilarity. To save space, we only need the lower half of matrix.
     */
    public WPGMCLinkage(double[][] proximity) {
        this.proximity = proximity;
        for (int i = 0; i < proximity.length; i++) {
            for (int j = 0; j < i; j++)
                proximity[i][j] *= proximity[i][j];
        }
    }

    @Override
    public String toString() {
        return "WPGMC linkage";
    }

    @Override
    public void merge(int i, int j) {
        for (int k = 0; k < i; k++) {
            proximity[i][k] = (proximity[i][k] + proximity[j][k]) / 2 - proximity[j][i] / 4;
        }

        for (int k = i+1; k < j; k++) {
            proximity[k][i] = (proximity[k][i] + proximity[j][k]) / 2 - proximity[j][i] / 4;
        }

        for (int k = j+1; k < proximity.length; k++) {
            proximity[k][i] = (proximity[k][i] + proximity[k][j]) / 2 - proximity[j][i] / 4;
        }
    }
}
