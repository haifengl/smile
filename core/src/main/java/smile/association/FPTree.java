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

import java.util.Arrays;
import java.util.HashMap;
import java.util.function.Supplier;
import java.util.stream.Stream;
import smile.sort.QuickSort;

/**
 * FP-tree data structure used in FP-growth (frequent pattern growth)
 * algorithm for frequent item set mining. An FP-tree is basically a
 * prefix tree for the transactions. That is, each path represents a
 * set of transactions that share the same prefix, each node corresponds
 * to one item. In addition, all nodes referring to the same item are
 * linked together in a list, so that all transactions containing a specific
 * item can easily be found and counted by traversing this list.
 * The list can be accessed through a head element, which also
 * states the total number of occurrences of the item in the
 * database.
 *
 * @author Haifeng Li
 */
public class FPTree {

    /**
     * FP-tree node object.
     */
    class Node {
        /**
         * The item identifier.
         */
        int id = -1;
        /**
         * The number of transactions represented by the portion of the path reaching this node.
         */
        int count = 0;
        /**
         * The backward link to the parent node in FP tree.
         */
        Node parent = null;
        /**
         * The forward link to the next node in a linked list of nodes with
         * same item identifier starting with an element in the header table.
         */
        Node next = null;
        /**
         * The reference to the child branch (levels in FP-tree branches are
         * stored as a arrays of Node structures.
         */
        HashMap<Integer, Node> children = null;

        /**
         * Constructs the root node.
         */
        Node() {
        }

        /**
         * Constructor.
         */
        Node(int id, int support, Node parent) {
            this.id = id;
            this.count = support;
            this.parent = parent;
        }

        /**
         * Searches through the list of children for given item set.
         * If a node for current item set found, increments support count and
         * proceed down branch. Otherwise add a new child node.
         * @param index the current item index in the item set.
         * @param end the end index of item set to add into the database.
         * @param itemset the given item set.
         * @param support the associated support value for the given item set.
         */
        void add(int index, int end, int[] itemset, int support) {
            if (children == null) {
                children = new HashMap<>();
            }
            
            Node child = children.get(itemset[index]);
            if (child != null) {
                // Node already exists. Update its support.
                child.count += support;
                if (++index < end) {
                    child.add(index, end, itemset, support);
                }
            } else {
                // Node doesn't exist. Create a new one.
                append(index, end, itemset, support);
            }
        }

        /**
         * Appends nodes of items to the current path.
         * @param index the current item index in the item set.
         * @param end the end index of item set to append into the database.
         * @param itemset the given item set.
         * @param support the associated support value for the given item set.
         */
        void append(int index, int end, int[] itemset, int support) {
            if (children == null) {
                children = new HashMap<>();
            }
            
            if (index >= maxItemSetSize) {
                maxItemSetSize = index + 1;
            }
            
            // Create new item subtree node
            int item = itemset[index];
            Node child = new Node(item, support, id < 0 ? null : this);
            // Add link from header table
            child.addToHeaderTable();
            // Add into FP tree
            children.put(item, child);
            // Proceed down branch with rest of item set
            if (++index < end) {
                child.append(index, end, itemset, support);
            }
        }

        /**
         * Adds this node to header table.
         */
        void addToHeaderTable() {
            next = headerTable[order[id]].node;
            headerTable[order[id]].node = this;
        }
    }

    /**
     * Header table item. Array of these structures used to link into FP-tree.
     * All FP-tree nodes with the same identifier are linked together starting
     * from a node in a header table (made up of HeaderTableItem structures).
     * This cross linking gives the FP-tree most significant advantage.
     */
    static class HeaderTableItem implements Comparable<HeaderTableItem> {

        /**
         * The item identifier.
         */
        int id;
        /**
         * The support (frequency) of single item.
         */
        int count = 0;
        /**
         * The forward link to the next node in the link list of nodes.
         */
        Node node = null;

        /**
         * Constructor.
         * @param id the item identifier.
         */
        HeaderTableItem(int id) {
            this.id = id;
        }

        @Override
        public int compareTo(HeaderTableItem o) {
            // Since we want to sort into descending order, we return the
            // reversed signum here.
            return Integer.compare(o.count, count);
        }
    }

    /**
     * The number transactions in the database.
     */
    int numTransactions = 0;
    /**
     * The required minimum support of item sets.
     */
    int minSupport;
    /**
     * Start reference for FP-tree. Root is just a dummy node for building the
     * FP-tree as a starting point. It is used during mining maximal frequent
     * item sets. No other nodes should use it as a parent node even if they
     * are root's children nodes.
     */
    Node root = new Node();
    /**
     * The support of single items.
     */
    int[] itemSupport;
    /**
     * Header table.
     */
    HeaderTableItem[] headerTable;
    /**
     * The number of items.
     */
    int numItems = 0;
    /**
     * The number of frequent items with sufficient supports.
     */
    int numFreqItems = 0;
    /**
     * The size of largest item set (with only frequent items) in the database.
     */
    int maxItemSetSize = -1;
    /**
     * The order of items according to their supports.
     */
    int[] order;

    /**
     * Constructor.
     *
     * @param minSupport the required minimum support of item sets in terms of frequency.
     * @param itemSupport the frequency of single items.
     */
    FPTree(int minSupport, int[] itemSupport) {
        this.itemSupport = itemSupport;
        this.minSupport = minSupport;
        init();
    }

    /**
     * Constructor.
     * 
     * @param minSupport the required minimum support of item sets in terms of frequency.
     * @param itemsets the item sets.
     */
    FPTree(int minSupport, Stream<int[]> itemsets) {
        this.itemSupport = freq(itemsets);
        this.minSupport = minSupport;
        init();
    }

    /**
     * Constructor.
     *
     * @param minSupport the required minimum support of item sets in terms of percentage.
     * @param itemsets the item sets.
     */
    FPTree(double minSupport, Stream<int[]> itemsets) {
        this.itemSupport = freq(itemsets);
        this.minSupport = (int) Math.round(minSupport * numTransactions);
        init();
    }

    /** Initialize the FP-tree after the first scan of data. */
    private void init() {
        numItems = itemSupport.length;
        for (int f : itemSupport) {
            if (f >= minSupport) {
                numFreqItems++;
            }
        }
        
        // It greatly improves the performance by making header table of
        // size numFreqItems instead of numItems. The reason is that numFreqItems
        // is usually much smaller than numItems and it is time consuming to
        // sort a large array.
        headerTable = new HeaderTableItem[numFreqItems];
        for (int i = 0, j = 0; i < numItems; i++) {
            if (itemSupport[i] >= minSupport) {
                HeaderTableItem header = new HeaderTableItem(i);
                header.count = itemSupport[i];
                headerTable[j++] = header;
            }
        }
        
        Arrays.sort(headerTable);
        order = new int[numItems];
        Arrays.fill(order, numItems);
        for (int i = 0; i < numFreqItems; i++) {
            order[headerTable[i].id] = i;
        }
    }

    /**
     * Returns the frequency of single items.
     * @param itemsets the transaction database.
     * @return the frequency of single items
     */
    private int[] freq(Stream<int[]> itemsets) {
        int n = Integer.parseInt(System.getProperty("smile.arm.items", "65536"));
        int[] f = new int[n];
        itemsets.forEach(itemset -> {
            numTransactions++;
            for (int i : itemset) f[i]++;
        });
        while (f[--n] == 0);
        return Arrays.copyOf(f, n+1);
    }
    
    /**
     * One-step construction of FP-tree if the database is available as stream.
     * @param minSupport the required minimum support of item sets in terms
     * of frequency.
     * @param supplier a supplier provides an itemset stream. For example, a code block to
     *                 open a file and parse lines into a stream of itemsets.
     *                 This function will be called twice.
     * @return a full built FP-tree.
     */
    public static FPTree of(int minSupport, Supplier<Stream<int[]>> supplier) {
        FPTree tree = new FPTree(minSupport, supplier.get());
        tree.add(supplier.get());
        return tree;
    }

    /**
     * One-step construction of FP-tree if the database is available as stream.
     * @param minSupport the required minimum support of item sets in terms
     *                   of percentage.
     * @param supplier a supplier provides an itemset stream. For example, a code block to
     *                 open a file and parse lines into a stream of itemsets.
     *                 This function will be called twice.
     * @return a full built FP-tree.
     */
    public static FPTree of(double minSupport, Supplier<Stream<int[]>> supplier) {
        FPTree tree = new FPTree(minSupport, supplier.get());
        tree.add(supplier.get());
        return tree;
    }

    /**
     * One-step construction of FP-tree if the database is available in main memory.
     * @param itemsets the item set database. Each row is a item set, which
     *                 may have different length. The item identifiers have to be in [0, n),
     *                 where n is the number of items. Item set should NOT contain duplicated
     *                 items. Note that it is reordered after the call.
     * @param minSupport the required minimum support of item sets in terms
     *                   of frequency.
     */
    public static FPTree of(int minSupport, int[][] itemsets) {
        FPTree tree = new FPTree(minSupport, Arrays.stream(itemsets));
        tree.add(Arrays.stream(itemsets));
        return tree;
    }

    /**
     * One-step construction of FP-tree if the database is available in main memory.
     * @param itemsets the item set database. Each row is a item set, which
     *                 may have different length. The item identifiers have to be in [0, n),
     *                 where n is the number of items. Item set should NOT contain duplicated
     *                 items. Note that it is reordered after the call.
     * @param minSupport the required minimum support of item sets in terms
     *                   of percentage.
     */
    public static FPTree of(double minSupport, int[][] itemsets) {
        FPTree tree = new FPTree(minSupport, Arrays.stream(itemsets));
        tree.add(Arrays.stream(itemsets));
        return tree;
    }

    /**
     * Returns the number transactions in the database.
     */
    public int size() {
        return numTransactions;
    }

    /**
     * Returns the required minimum support of item sets in terms
     * of frequency.
     */
    public int minSupport() {
        return minSupport;
    }

    /** Adds a stream of item sets into the FP-tree. */
    private void add(Stream<int[]> itemsets) {
        itemsets.forEach(this::add);
    }

    /**
     * Add an item set into the FP-tree.
     * @param itemset an item set, which should NOT contain duplicated items.
     * Note that it is reordered after the call.
     */
    private void add(int[] itemset) {
        int m = 0;
        int t = itemset.length;
        int[] o = new int[t];
        for (int i = 0; i < t; i++) {
            int item = itemset[i];
            o[i] = order[item];
            if (itemSupport[item] >= minSupport) {
                m++;
            }
        }

        if (m > 0) {
            // Order all items in itemset in frequency descending order
            // Note that some items may have same frequency. We have to make
            // sure that items are in the same order of header table.
            QuickSort.sort(o, itemset, t);
            
            // Note that itemset may contain duplicated items. We should keep
            // only one in case of getting incorrect support value.
            for (int i = 1; i < m; i++) {
                if (itemset[i] == itemset[i-1]) {
                    m--;
                    for (int j = i; j < m; j++) {
                        itemset[j] = itemset[j+1];
                    }
                }
            }
            
            root.add(0, m, itemset, 1);
        }
    }

    /**
     * Add an item set into the FP-tree. The items in the set is already in the
     * descending order of frequency.
     * @param index the current item index in the item set.
     * @param end the end index of item set to append into the database.
     * @param itemset an item set.
     * @param support the support/frequency of the item set.
     */
    void add(int index, int end, int[] itemset, int support) {
        root.add(index, end, itemset, support);
    }
}
