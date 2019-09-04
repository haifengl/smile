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

import smile.data.DataFrame;
import smile.data.type.StructType;
import smile.math.MathEx;

import java.util.LinkedList;
import java.util.Queue;
import java.util.stream.IntStream;
import java.util.AbstractMap.SimpleEntry;

/** Classification and regression tree. */
public abstract class CART {

    /** The root of decision tree. */
    protected Node root;
    /**
     * The number of instances in a node below which the tree will
     * not split, setting nodeSize = 5 generally gives good results.
     */
    protected int nodeSize = 5;
    /**
     * The maximum number of leaf nodes in the tree.
     */
    protected int maxNodes = 6;
    /**
     * The number of input variables to be used to determine the decision
     * at a node of the tree.
     */
    protected int mtry = -1;

    public CART(int nodeSize, int maxNodes, int mtry) {
        this.nodeSize = nodeSize;
        this.maxNodes = maxNodes;
        this.mtry = mtry;
    }

    /** Finds the best split. */
    public Split findBestSplit(DataFrame df, int[] samples) {
        int n = MathEx.sum(samples);

        if (n <= nodeSize) {
            throw new IllegalStateException("Split a node with samples less than " + nodeSize);
        }

        double sum = node.output * n;

        StructType schema = df.schema();
        int p = schema.length();
        int[] columns = IntStream.range(0, p).toArray();

        if (mtry < p) {
            MathEx.permutate(columns);
        }

        Split best = findBestSplit(n, sum, columns[0]);
        for (int j = 1; j < mtry; j++) {
            Split split = findBestSplit(n, sum, columns[j]);
            if (split.splitScore > best.splitScore) {
                best = split;
            }
        }

        return best;
    }

    /** Finds the best split for given column. */
    public abstract Split findBestSplit(int column, DataFrame df, int[] samples);

    /**
     * Returns the graphic representation in Graphviz dot format.
     * Try http://viz-js.com/ to visualize the returned string.
     */
    public String dot() {
        StringBuilder builder = new StringBuilder();
        builder.append("digraph DecisionTree {\n node [shape=box, style=\"filled, rounded\", color=\"black\", fontname=helvetica];\n edge [fontname=helvetica];\n");

        String trueLabel  = " [labeldistance=2.5, labelangle=45, headlabel=\"True\"];\n";
        String falseLabel = " [labeldistance=2.5, labelangle=-45, headlabel=\"False\"];\n";

        Queue<SimpleEntry<Integer, Node>> queue = new LinkedList<>();
        queue.add(new SimpleEntry(1, root));

        while (!queue.isEmpty()) {
            // Dequeue a vertex from queue and print it
            SimpleEntry<Integer, Node> entry = queue.poll();
            int id = entry.getKey();
            Node node = entry.getValue();

            // leaf node
            builder.append(node.toDot(id));

            if (node instanceof InternalNode) {
                int tid = 2 * id;
                int fid = 2 * id + 1;
                InternalNode inode = (InternalNode) node;
                queue.add(new SimpleEntry(tid, inode.trueChild));
                queue.add(new SimpleEntry(fid, inode.falseChild));

                // add edge
                builder.append(' ').append(id).append(" -> ").append(tid).append(trueLabel);
                builder.append(' ').append(id).append(" -> ").append(fid).append(falseLabel);

                // only draw edge label at top
                if (id == 1) {
                    trueLabel = "\n";
                    falseLabel = "\n";
                }
            }
        }

        builder.append("}");
        return builder.toString();
    }
}
