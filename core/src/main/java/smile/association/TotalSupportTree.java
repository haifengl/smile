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

package smile.association;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Total support tree is a kind of compressed set enumeration tree so that we
 * can generate association rules in a storage efficient way.
 * 
 * <h2>References</h2>
 * <ol>
 * <li> Frans Coenen, Paul Leng, and Shakil Ahmed. Data Structure for Association Rule Mining: T-Trees and P-Trees. IEEE TRANSACTIONS ON KNOWLEDGE AND DATA ENGINEERING, 16(6):774-778, 2004.</li>
 * </ol>
 * 
 * @author Haifeng Li
 */
class TotalSupportTree implements Iterable<ItemSet> {

    class Node {
        /**
         * The id of item.
         */
        int id = -1;
        /**
         * The support associate with the item set represented by the node.
         */
        int support = 0;
        /**
         * The set of children nodes.
         */
        Node[] children = null;

        /**
         * Constructor.
         */
        Node() {
        }

        /**
         * Constructor.
         * @param id the id of item.
         */
        Node(int id) {
            this.id = id;
        }
    }

    /**
     * The root of t-tree.
     */
    Node root = new Node();
    /**
     * The number transactions in the database.
     */
    int numTransactions = 0;
    /**
     * The required minimum support of item sets.
     */
    private int minSupport;
    /**
     * The index of items after sorting.
     */
    private int[] order;
    /**
     * The buffer to collect mining results.
     */
    private Queue<ItemSet> buffer = new LinkedList<>();

    /**
     * Constructor.
     */
    public TotalSupportTree(FPTree tree) {
        this.numTransactions = tree.numTransactions;
        this.minSupport = tree.minSupport;
        this.order = tree.order;
        root.children = new Node[tree.numFreqItems];
        FPGrowth.apply(tree).forEach(itemset -> add(itemset.items, itemset.support));
    }

    /**
     * Returns the number transactions in the database.
     */
    public int size() {
        return numTransactions;
    }

    @Override
    public Iterator<ItemSet> iterator() {
        return new Iterator<ItemSet>() {
            int i = 0;

            @Override
            public boolean hasNext() {
                if (buffer.isEmpty()) {
                    for (; i < root.children.length; i++) {
                        Node child = root.children[i];
                        if (child != null && child.support >= minSupport) {
                            int[] itemset = {child.id};
                            generate(itemset, i, child);

                            if (!buffer.isEmpty()) {
                                i++; // we will miss i++ in for loop once break
                                break;
                            }
                        }
                    }
                }

                return !buffer.isEmpty();
            }

            @Override
            public ItemSet next() {
                return buffer.poll();
            }
        };
    }

    /**
     * Adds an item set with its support value.
     * @param itemset the given item set. The items in the set has to be in the
     * descending order according to their frequency.
     * @param support the support value associated with the given item set.
     */
    private void add(int[] itemset, int support) {
        add(root, 0, itemset.length - 1, itemset, support);
    }

    /**
     * Inserts a node into a T-tree.
     * @param node the root of subtree.
     * @param size the size of the current array in T-tree.
     * @param index the index of the last item in the item set, which is also
     * used as a level counter.
     * @param itemset the given item set.
     * @param support the support value associated with the given item set.
     */
    private void add(Node node, int size, int index, int[] itemset, int support) {
        if (node.children == null) {
            node.children = new Node[size];
        }

        int item = order[itemset[index]];
        if (node.children[item] == null) {
            node.children[item] = new Node(itemset[index]);
        }

        if (index == 0) {
            node.children[item].support += support;
        } else {
            add(node.children[item], item, index - 1, itemset, support);
        }
    }

    /** 
     * Returns the support value for the given item set.
     * @param itemset the given item set. The items in the set has to be in the
     * descending order according to their frequency.
     * @return the support value (0 if not found)
     */
    public int getSupport(int[] itemset) {
        if (root.children != null) {
            return getSupport(itemset, itemset.length - 1, root);
        } else {
            return 0;
        }
    }

    /**
     * Returns the support value for the given item set if found in the T-tree
     * and 0 otherwise.
     * @param itemset the given item set.
     * @param index the current index in the given item set.
     * @return the support value (0 if not found)
     */
    private int getSupport(int[] itemset, int index, Node node) {
        int item = order[itemset[index]];
        Node child = node.children[item];
        if (child != null) {
            // If the index is 0, then this is the last element (i.e the
            // input is a 1 itemset)  and therefore item set found
            if (index == 0) {
                return child.support;
            } else {
                if (child.children != null) {
                    return getSupport(itemset, index - 1, child);
                }
            }
        }

        return 0;
    }
    
    /**
     * Mines the frequent item sets.
     * @return the stream of frequent item sets
     */
    public Stream<ItemSet> stream() {
        return StreamSupport.stream(spliterator(), false);
    }
    
    /**
     * Returns the set of frequent item sets.
     * @param node the path from root to this node matches the given item set.
     * @param itemset the frequent item set generated so far.
     * @param size the length/size of the current array level in the T-tree.
     */
    private void generate(int[] itemset, int size, Node node) {
        ItemSet set = new ItemSet(itemset, node.support);
        buffer.offer(set);

        if (node.children != null) {
            for (int i = 0; i < size; i++) {
                Node child = node.children[i];
                if (child != null && child.support >= minSupport) {
                    int[] newItemset = FPGrowth.insert(itemset, child.id);
                    generate(newItemset, i, child);
                }
            }
        }
    }
}
