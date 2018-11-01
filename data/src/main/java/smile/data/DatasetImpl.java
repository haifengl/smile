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
