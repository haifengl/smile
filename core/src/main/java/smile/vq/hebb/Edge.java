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

package smile.vq.hebb;

import java.io.Serializable;

/**
 * The connection between neurons.
 */
public class Edge implements Serializable {
    private static final long serialVersionUID = 2L;
    /**
     * The neighbor neuron.
     */
    public final Neuron neighbor;
    /**
     * The age of this edges.
     */
    public int age;

    /**
     * Constructor.
     */
    public Edge(Neuron neighbor) {
        this(neighbor, 0);
    }

    /**
     * Constructor.
     */
    public Edge(Neuron neighbor, int age) {
        this.neighbor = neighbor;
        this.age = age;
    }
}
