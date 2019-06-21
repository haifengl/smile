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

package smile.neighbor;

/**
 * The simple neighbor object, in which key and object are the same.
 *
 * @param <T> the type of key/object.
 *
 * @author Haifeng Li
 */
public class SimpleNeighbor<T> extends Neighbor<T,T> {
    /**
     * Constructor.
     * @param key the neighbor object.
     * @param index the index of neighbor object in the dataset.
     * @param distance the distance between the query and the neighbor.
     */
    public SimpleNeighbor(T key, int index, double distance) {
        super(key, key, index, distance);
    }
}
