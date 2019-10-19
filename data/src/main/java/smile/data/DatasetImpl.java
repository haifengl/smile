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

import java.util.ArrayList;
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
    private ArrayList<T> data;

    /**
     * Constructor
     * @param data The underlying data collection.
     */
    public DatasetImpl(Collection<T> data) {
        this.data = new ArrayList<>(data);
    }

    @Override
    public int size() {
        return data.size();
    }

    @Override
    public T get(int i) {
        return data.get(i);
    }

    @Override
    public Stream<T> stream() {
        return data.stream();
    }
}
