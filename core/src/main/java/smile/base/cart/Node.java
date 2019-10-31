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

package smile.base.cart;

import java.io.Serializable;
import java.math.BigInteger;
import java.util.List;
import smile.data.Tuple;
import smile.data.type.StructField;
import smile.data.type.StructType;
import smile.math.MathEx;

/**
 * CART tree node.
 */
public interface Node extends Serializable {
    /**
     * Evaluate the tree over an instance.
     */
    LeafNode predict(Tuple x);

    /**
     * Returns a dot representation for visualization.
     * @param schema the schema of data
     * @param response the schema of response variable
     * @param id node id
     */
    String dot(StructType schema, StructField response, int id);

    /** Returns the number of samples in the node. */
    int size();

    /** Returns the number of leaf nodes in the subtree. */
    int leafs();

    /**
     * Returns the maximum depth of the tree -- the number of
     * nodes along the longest path from this node
     * down to the farthest leaf node.
     */
    int depth();

    /** Returns the deviance of node. */
    double deviance();

    /**
     * Try to merge the children nodes and return a leaf node.
     * If not able to merge, return this node itself.
     */
    Node merge();

    /**
     * Adds the string representation (R's rpart format) to a collection.
     * @param schema the schema of data
     * @param response the schema of response variable
     * @param parent the parent node
     * @param depth the depth of node in the tree. The root node is at depth 0.
     * @param id node id
     * @param lines the collection of node's string representation.
     * @return the sample count of each class for decision tree; single element array [node size] for regression tree.
     */
    int[] toString(StructType schema, StructField response, InternalNode parent, int depth, BigInteger id, List<String> lines);
}

