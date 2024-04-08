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

/**
 * An immutable sample instance.
 *
 * @param <D> the data type.
 * @param <T> the target type.
 *
 * @author Haifeng Li
 */
public class Instance<D, T> {
    /**
     * Returns the instance.
     * @return the instance.
     */
    public final D x;

    /**
     * Returns the response variable of instance.
     * @return the response variable.
     */
    public final T y;

    public Instance(D x, T y) {
        this.x = x;
        this.y = y;
    }

    public Instance(D x) {
        this(x, null);
    }
}
