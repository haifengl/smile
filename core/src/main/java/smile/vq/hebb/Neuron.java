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
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import smile.math.MathEx;

/**
 * The neuron vertex in the growing neural gas network.
 */
public class Neuron implements Comparable<Neuron>, Serializable {
    private static final long serialVersionUID = 2L;
    /**
     * The reference vector.
     */
    public final double[] w;
    /**
     * The direct connected neighbors.
     */
    public final List<Edge> edges;
    /**
     * The distance between the neuron and an input signal.
     */
    public double distance = Double.MAX_VALUE;
    /**
     * The local counter variable (e.g. the accumulated error, freshness, etc.)
     */
    public double counter;

    /**
     * Constructor.
     */
    public Neuron(double[] w) {
        this(w, 0.0);
    }

    /**
     * Constructor.
     */
    public Neuron(double[] w, double counter) {
        this.w = w;
        this.counter = counter;
        this.edges = new LinkedList<>();
    }

    /**
     * Updates the reference vector by w += eps * (x - w).
     * @param x a signal.
     * @param eps the learning rate.
     */
    public void update(double[] x, double eps) {
        for (int i = 0; i < x.length; i++) {
            w[i] += eps * (x[i] - w[i]);
        }
    }

    /** Adds an edge. */
    public void addEdge(Neuron neighbor) {
        addEdge(neighbor, 0);
    }

    /** Adds an edge. */
    public void addEdge(Neuron neighbor, int age) {
        edges.add(new Edge(neighbor, age));
    }

    /** Removes an edge. */
    public void removeEdge(Neuron neighbor) {
        for (Iterator<Edge> iter = edges.iterator(); iter.hasNext();) {
            Edge edge = iter.next();
            if (edge.neighbor == neighbor) {
                iter.remove();
                return;
            }
        }
    }

    /** Sets the age of edge. */
    public void setEdgeAge(Neuron neighbor, int age) {
        for (Iterator<Edge> iter = edges.iterator(); iter.hasNext();) {
            Edge edge = iter.next();
            if (edge.neighbor == neighbor) {
                edge.age = age;
                return;
            }
        }
    }

    /** Increments the age of all edges emanating from the neuron. */
    public void age() {
        for (Edge edge : edges) {
            edge.age++;
        }
    }

    /**
     * Computes the distance between the neuron and a signal.
     */
    public void distance(double[] x) {
        distance = MathEx.distance(w, x);
    }

    @Override
    public int compareTo(Neuron o) {
        return Double.compare(distance, o.distance);
    }
}
