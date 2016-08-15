/*******************************************************************************
 * Copyright (c) 2010 Haifeng Li
 *   
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *  
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package smile.association;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

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
class TotalSupportTree {

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
     * The index of items after sorting.
     */
    private int[] order;
    /**
     * The required minimum support of item sets.
     */
    private int minSupport;

    /**
     * Constructor.
     * @param order the index of items after sorting.
     * @param numItems the number of items with sufficient support.
     * @param order the index of items after sorting.
     */
    public TotalSupportTree(int minSupport, int numItems, int[] order) {
        this.minSupport = minSupport;
        this.order = order;
        root.children = new Node[numItems];
    }

    /**
     * Adds an item set with its support value.
     * @param itemset the given item set. The items in the set has to be in the
     * descending order according to their frequency.
     * @param support the support value associated with the given item set.
     */
    public void add(int[] itemset, int support) {
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
     * @param nodes the nodes of the current T-tree level.
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
     * Mines the frequent item sets. The discovered frequent item sets
     * will be returned in a list.
     * @return the list of frequent item sets
     */
    public List<ItemSet> getFrequentItemsets() {
        List<ItemSet> list = new ArrayList<>();
        getFrequentItemsets(null, list);
        return list;
    }
    
    /**
     * Mines the frequent item sets. The discovered frequent item sets
     * will be printed out to the provided stream.
     * @param out a print stream for output of frequent item sets.
     * @return the number of discovered frequent item sets
     */
    public long getFrequentItemsets(PrintStream out) {
        return getFrequentItemsets(out, null);
    }
    
    /**
     * Returns the set of frequent item sets.
     * @param out a print stream for output of frequent item sets.
     * @param list a container to store frequent item sets on output.
     * @return the number of discovered frequent item sets
     */
    private long getFrequentItemsets(PrintStream out, List<ItemSet> list) {
        long n = 0;
        if (root.children != null) {
            for (int i = 0; i < root.children.length; i++) {
                Node child = root.children[i];
                if (child != null && child.support >= minSupport) {
                    int[] itemset = {child.id};
                    n += getFrequentItemsets(out, list, itemset, i, child);
                }
            }
        }
        
        return n;
    }

    /**
     * Returns the set of frequent item sets.
     * @param out a print stream for output of frequent item sets.
     * @param list a container to store frequent item sets on output.
     * @param node the path from root to this node matches the given item set.
     * @param itemset the frequent item set generated so far.
     * @param size the length/size of the current array level in the T-tree.
     * @return the number of discovered frequent item sets
     */
    private long getFrequentItemsets(PrintStream out, List<ItemSet> list, int[] itemset, int size, Node node) {
        ItemSet set = new ItemSet(itemset, node.support);
        if (out != null) {
            out.println(set);
        }

        if (list != null) {
            list.add(set);
        }

        long n = 1;
        if (node.children != null) {
            for (int i = 0; i < size; i++) {
                Node child = node.children[i];
                if (child != null && child.support >= minSupport) {
                    int[] newItemset = FPGrowth.insert(itemset, child.id);
                    n += getFrequentItemsets(out, list, newItemset, i, child);
                }
            }
        }

        return n;
    }
}
