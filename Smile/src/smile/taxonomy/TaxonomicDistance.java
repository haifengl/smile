/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/

package smile.taxonomy;

import java.util.Iterator;
import java.util.List;
import smile.math.distance.Distance;

/**
 * The distance between concepts in a taxonomy.
 * The distance between two concepts a and b is defined by the length of the
 * path from a to their lowest common ancestor and then to b.
 *
 * @author Haifeng Li
 */
public class TaxonomicDistance implements Distance<Concept> {
    /**
     * The taxonomy basis to calculate the distance.
     */
    private Taxonomy taxonomy;

    /**
     * Constructor.
     * 
     * @param taxonomy the taxonomy that this distance is associated with.
     */
    public TaxonomicDistance(Taxonomy taxonomy) {
        this.taxonomy = taxonomy;
    }

    @Override
    public String toString() {
        return String.format("Taxonomic distance on %s", taxonomy);
    }

    /**
     * Compute the distance between two concepts in a taxonomy.
     */
    public double d(String x, String y) {
        return d(taxonomy.getConcept(x), taxonomy.getConcept(y));
    }

    /**
     * Compute the distance between two concepts in a taxonomy.
     */
    @Override
    public double d(Concept x, Concept y) {
        if (x.taxonomy != y.taxonomy) {
            throw new IllegalArgumentException("Concepts are not from the same taxonomy.");
        }

        List<Concept> xPath = x.getPathFromRoot();
        List<Concept> yPath = y.getPathFromRoot();

        Iterator<Concept> xIter = xPath.iterator();
        Iterator<Concept> yIter = yPath.iterator();

        int depth = 0;
        while (xIter.hasNext() && yIter.hasNext()) {
            if (xIter.next() != yIter.next()) {
                break;
            }

            depth++;
        }

        return xPath.size() - depth + yPath.size() - depth;
    }
}
