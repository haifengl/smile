package smile.neighbor.lsh;

import java.util.List;

/**
 * Training sample for MPLSH.
 *
 * @author Haifeng Li
 */
public class MultiProbeSample {
    /**
     * The query object.
     */
    public final double[] query;
    /**
     * Neighbors of query object in terms of kNN or range search.
     */
    public final List<double[]> neighbors;

    /** Constructor. */
    public MultiProbeSample(double[] query, List<double[]> neighbors) {
        this.query = query;
        this.neighbors = neighbors;
    }
}
