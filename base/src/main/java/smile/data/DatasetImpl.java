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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Collection;
import java.util.stream.Stream;

/**
 * A simple implementation of Dataset that store data in single machine's memory.
 *
 * @param <D> the data type.
 * @param <T> the target type.
 *
 * @author Haifeng Li
 */
class DatasetImpl<D, T> implements Dataset<D, T> {
    /**
     * The sample instances.
     */
    private final ArrayList<Instance<D, T>> instances;

    /**
     * Constructor
     * @param instances The sample instances.
     */
    public DatasetImpl(Collection<Instance<D, T>> instances) {
        this.instances = new ArrayList<>(instances);
    }

    @Override
    public int size() {
        return instances.size();
    }

    @Override
    public Instance<D, T> get(int i) {
        return instances.get(i);
    }

    @Override
    public Stream<Instance<D, T>> stream() {
        return instances.stream();
    }

    @Override
    public Iterator<Instance<D, T>> iterator() {
        return instances.iterator();
    }

    @Override
    public List<Instance<D, T>> toList() {
        return new ArrayList<>(instances);
    }
}
