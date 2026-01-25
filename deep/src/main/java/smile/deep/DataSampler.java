/*
 * Copyright (c) 2010-2024 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.deep;

import java.util.Iterator;
import org.bytedeco.pytorch.*;

/**
 * Data sampler is an iterator that shuffles the data and
 * returns samples in mini-batches.
 *
 * @author Haifeng Li
 */
class DataSampler implements Iterator<SampleBatch> {
    final ExampleIterator begin;
    final ExampleIterator end;
    ExampleIterator it;

    /**
     * Constructor.
     * @param begin the beginning iterator.
     * @param end the end iterator.
     */
    DataSampler(ExampleIterator begin, ExampleIterator end) {
        this.begin = begin;
        this.end = end;
    }

    @Override
    public boolean hasNext() {
        it = it == null ? begin : it.increment();
        return !it.equals(end);
    }

    @Override
    public SampleBatch next() {
        Example example = it.access();
        return new SampleBatch(example.data(), example.target());
    }
}
