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

/**
 * An immutable instance.
 *
 * @param <T> the type of instance.
 *
 * @author Haifeng Li
 */
public interface Instance <T> {
    /**
     * Returns the instance.
     */
    T x();

    /**
     * Returns the response variable of instance.
     */
    default double y() {
        throw new UnsupportedOperationException("The instance doesn't have response variable.");
    }

    /**
     * Returns the class label of instance.
     */
    default int label() {
        throw new UnsupportedOperationException("The instance doesn't have class label.");
    }

    /**
     * Return the (optional) name associated with instance.
     * Note that this is not the class label.
     */
    default String name() {
        return null;
    }

    /**
     * Return the (optional) weight associated with instance.
     */
    default double weight() {
        return 1.0;
    }
}
