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
import java.util.LinkedList;
import java.util.List;

/**
 * The entry of an universal hash table with collision solved by chaining.
 *
 * @author Haifeng Li
 */
public class HashEntry implements Serializable {
    private static final long serialVersionUID = 2L;

    /**
     * The chain of buckets in the slot.
     */
    private List<Bucket> buckets = new LinkedList<>();

    /**
     * Constructor.
     */
    public HashEntry() {

    }

    public List<Bucket> buckets() {
        return buckets;
    }

    /**
     * Adds a point to given bucket.
     * @param bucket the bucket number.
     * @param point the index of point.
     */
    public void add(int bucket, int point) {
        for (Bucket b : buckets) {
            if (b.bucket == bucket) {
                b.add(point);
                return;
            }
        }

        Bucket b = new Bucket(bucket);
        b.add(point);
        buckets.add(b);
    }

    /**
     * Removes a point from given bucket.
     * @param bucket the bucket number.
     * @param point the index of point.
     * @return true if the point was in the bucket.
     */
    public boolean remove(int bucket, int point) {
        for (Bucket b : buckets) {
            if (b.bucket == bucket) {
                return b.remove(point);
            }
        }

        return false;
    }
}
