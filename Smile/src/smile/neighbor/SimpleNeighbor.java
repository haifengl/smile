/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/

package smile.neighbor;

/**
 * The simple neighbor object, in which key and object are the same.
 *
 * @param <T> the type of key/object.
 *
 * @author Haifeng Li
 */
public class SimpleNeighbor<T> extends Neighbor<T,T> {
    /**
     * Constructor.
     * @param key the neighbor object.
     * @param index the index of neighbor object in the dataset.
     * @param distance the distance between the query and the neighbor.
     */
    public SimpleNeighbor(T key, int index, double distance) {
        super(key, key, index, distance);
    }
}
