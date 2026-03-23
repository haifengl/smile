/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.graph;

/**
 * A visitor is encapsulation of some operation on graph vertices during
 * traveling graph (DFS or BFS).
 *
 * @author Haifeng Li
 */
public interface VertexVisitor {
    /**
     * Performs some operations on the currently-visiting vertex during DFS or BFS.
     * @param vertex the index of vertex.
     */
    void accept(int vertex);
}
