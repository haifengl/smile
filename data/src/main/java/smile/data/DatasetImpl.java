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

package smile.data;

import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Stream;

/**
 * A simple implementation of Dataset that store data in single machine's memory.
 *
 * @param <T> the type of data objects.
 *
 * @author Haifeng Li
 */
class DatasetImpl<T> implements Dataset<T> {
    /**
     * The data objects.
     */
    private T[] data;

    /**
     * Constructor
     * @param data The underlying data collection.
     */
    @SuppressWarnings("unchecked")
    public DatasetImpl(Collection<T> data) {
        this.data = (T[]) new Object[data.size()];
        this.data = data.toArray(this.data);
    }

    @Override
    public int size() {
        return data.length;
    }

    @Override
    public T get(int i) {
        return data[i];
    }

    @Override
    public Stream<T> stream() {
        return Arrays.stream(data);
    }
}
