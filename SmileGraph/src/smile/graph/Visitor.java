/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/

package smile.graph;

/**
 * A visitor is encapsulation of some operation on graph vertices during
 * traveling graph (DFS or BFS).
 *
 * @author Haifeng Li
 */
public interface Visitor {
    /**
     * Performs some operations on the currently-visiting vertex during DFS or BFS.
     * @param vertex the index of vertex.
     */
    public void visit(int vertex);
}
