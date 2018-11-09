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
package smile.data.formula;

import smile.data.type.DataType;
import smile.data.type.DataTypes;

import java.util.function.Function;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * A property of a Java object.
 *
 * @param <T> the type of object.
 * @param <R> the type of property.
 *
 * @author Haifeng Li
 */
public class Property<T, R> implements Factor<T, R> {
    /** Property name. */
    private String name;
    /** Function to get the property. */
    private Function<? super T,? extends R> getter;
    /** The class of return type. */
    private Class<R> clazz;

    /**
     * Constructor.
     *
     * @param name the property name.
     * @param getter the function to retrieve the property.
     */
    public Property(String name, Function<? super T,? extends R> getter, Class<R> clazz) {
        this.name = name;
        this.getter = getter;
        this.clazz = clazz;
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public List<Factor> factors() {
        return Collections.singletonList(this);
    }

    @Override
    public Set<String> variables() {
        return Collections.singleton(name);
    }

    @Override
    public R apply(T o) {
        return getter.apply(o);
    }

    @Override
    public DataType type() {
        return DataTypes.object(clazz);
    }
}
