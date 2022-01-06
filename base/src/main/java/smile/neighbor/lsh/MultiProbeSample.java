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
     * The neighbors of query object in terms of kNN or range search.
     */
    public final List<double[]> neighbors;

    /**
     * Constructor.
     * @param query the query object.
     * @param neighbors the neighbors of query object.
     */
    public MultiProbeSample(double[] query, List<double[]> neighbors) {
        this.query = query;
        this.neighbors = neighbors;
    }
}
