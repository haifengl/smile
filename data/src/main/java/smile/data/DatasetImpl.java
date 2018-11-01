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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Spliterator;
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
    private List<T> data;

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
        Spliterator<T> spliterator = new LocalDatasetSpliterator<>(this, Spliterator.ORDERED);
        return java.util.stream.StreamSupport.stream(spliterator, true);
    }
}
