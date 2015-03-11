/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/

package smile.neighbor;

/**
 * Nearest neighbor search, also known as proximity search, similarity search
 * or closest point search, is an optimization problem for finding closest
 * points in metric spaces. The problem is: given a set S of points in a metric
 * space M and a query point q &isin; M, find the closest point in S to q.
 * 
 * @param <K> the type of keys.
 * @param <V> the type of associated objects.
 *
 * @author Haifeng Li
 */
public interface NearestNeighborSearch<K, V> {
    /**
     * Search the nearest neighbor to the given sample.
     *
     * @param q the query key.
     * @return the nearest neighbor
     */
    public Neighbor<K, V> nearest(K q);
}
