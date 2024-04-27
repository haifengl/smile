/*
 * Copyright (c) 2010-2021 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */

package smile.data;

import java.util.function.Consumer;
import java.util.Spliterator;

/**
 * A spliterator traverse and partition a local dataset.
 *
 * @author Haifeng Li
 */
class DataFrameSpliterator implements Spliterator<Tuple> {
    /** The underlying Dataset. */
    private final DataFrame data;
    /** These may be employed by Spliterator clients to control, specialize or simplify computation. */
    private int characteristics = IMMUTABLE | SIZED | SUBSIZED;
    /** Current index, advanced on split or traversal */
    private int origin;
    /** One past the greatest index. */
    private final int fence;

    /**
     * Constructor.
     * @param data the underlying Dataset.
     * @param additionalCharacteristics properties of this spliterator's source.
     */
    public DataFrameSpliterator(DataFrame data, int additionalCharacteristics) {
        this.data = data;
        this.characteristics |= additionalCharacteristics;
        this.origin = 0;
        this.fence = data.size();
    }

    /**
     * Constructor.
     */
    public DataFrameSpliterator(DataFrameSpliterator spliterator, int origin, int fence) {
        this.data = spliterator.data;
        this.characteristics = spliterator.characteristics;
        this.origin = origin;
        this.fence = fence;
    }

    @Override
    public boolean tryAdvance(Consumer<? super Tuple> action) {
        if (origin < fence) {
            action.accept(data.get(origin));
            origin += 1;
            return true;
        }

        // cannot advance
        return false;
    }

    @Override
    public Spliterator<Tuple> trySplit() {
        int lo = origin; // divide range in half
        int mid = ((lo + fence) >>> 1);
        if (lo < mid) {
            origin = mid; // reset this Spliterator's origin
            // split out left half
            return new DataFrameSpliterator(this, lo, mid);
        }

        // too small to split
        return null;
    }

    @Override
    public long estimateSize() {
        return fence - origin;
    }

    @Override
    public int characteristics() {
        return characteristics;
    }
}
