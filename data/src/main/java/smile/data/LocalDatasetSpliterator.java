/*******************************************************************************
 * Copyright (c) 2010 Haifeng Li
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package smile.data;

import java.util.function.Consumer;
import java.util.Spliterator;

/**
 * A spliterator traverse and partition a local dataset.
 *
 * @author Haifeng Li
 */
public class LocalDatasetSpliterator<T> implements Spliterator<T> {
    /** The underlying Dataset. */
    private Dataset<T> data;
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
    public LocalDatasetSpliterator(Dataset<T> data, int additionalCharacteristics) {
        if (data.distributed()) {
            throw new UnsupportedOperationException("The LocalDatasetSpliterator is applied to a distributed Dataset.");
        }

        this.data = data;
        this.characteristics |= additionalCharacteristics;
        this.origin = 0;
        this.fence = data.size();
    }

    /**
     * Constructor.
     */
    public LocalDatasetSpliterator(LocalDatasetSpliterator<T> spliterator, int origin, int fence) {
        this.data = spliterator.data;
        this.characteristics = spliterator.characteristics;
        this.origin = origin;
        this.fence = fence;
    }

    @Override
    public boolean tryAdvance(Consumer<? super T> action) {
        if (origin < fence) {
            action.accept(data.get(origin));
            origin += 1;
            return true;
        }

        // cannot advance
        return false;
    }

    @Override
    public Spliterator<T> trySplit() {
        int lo = origin; // divide range in half
        int mid = ((lo + fence) >>> 1);
        if (lo < mid) {
            origin = mid; // reset this Spliterator's origin
            // split out left half
            return new LocalDatasetSpliterator<>(this, lo, mid);
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
