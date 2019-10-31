/*******************************************************************************
 * Copyright (c) 2010-2019 Haifeng Li
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 *******************************************************************************/

package smile.neighbor.lsh;

import java.io.Serializable;
import smile.util.IntArrayList;

/**
 * A bucket is a container for points that all have the same value for hash
 * function g (function g is a vector of k LSH functions). A bucket is specified
 * by a vector in integers of length k.
 *
 * @author Haifeng Li
 */
public class Bucket implements Serializable {
    private static final long serialVersionUID = 2L;

    /**
     * The bucket id is given by the universal bucket hashing.
     * The id is used instead of the full k-vector (value of the hash
     * function g) describing the bucket. With a high probability all
     * buckets will have different pairs of id's.
     */
    public final int bucket;

    /**
     * The indices of points that all have the same value for hash function g.
     */
    public final IntArrayList entry = new IntArrayList();

    /**
     * Constructor.
     * @param bucket the bucket number given by universal hashing.
     */
    public Bucket(int bucket) {
        this.bucket = bucket;
    }

    /** Returns the points in the bucket. */
    public IntArrayList points() {
        return entry;
    }

    /**
     * Adds a point to bucket.
     * @param point the index of point.
     */
    public void add(int point) {
        entry.add(point);
    }

    /**
     * Removes a point from bucket.
     * @param point the index of point.
     * @return true if the point was in the bucket.
     */
    public boolean remove(int point) {
        int n = entry.size();
        for (int i = 0; i < n; i++) {
            if (entry.get(i) == point) {
                entry.remove(i);
                return true;
            }
        }

        return false;
    }
}
