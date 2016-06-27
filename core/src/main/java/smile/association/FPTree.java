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

import java.util.Arrays;
import java.util.HashMap;
import smile.sort.QuickSort;
import smile.math.Math;

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
final class FPTree {

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
         * Constructor.
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
         * @param header the header table.
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
            return o.count - count;
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
    Node root = null;
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
     * Constructor. This is two-step construction of FP-tree. The user first
     * scans the database to obtains the frequency of single items and calls
     * this constructor. Then the user add item sets to the FP-tree by
     * {@link #add(int[])} during the second scan of the database. In this way,
     * we don't need load the database into the main memory.
     * 
     * @param frequency the frequency of single items.
     * @param minSupport the required minimum support of item sets in terms of
     * frequency.
     */
    public FPTree(int[] frequency, int minSupport) {
        this.itemSupport = frequency;
        this.minSupport = minSupport;

        root = new Node();
        
        numItems = frequency.length;
        for (int f : frequency) {
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
            if (frequency[i] >= minSupport) {
                HeaderTableItem header = new HeaderTableItem(i);
                header.count = frequency[i];                        
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
     * Constructor. This is a one-step construction of FP-tree if the database
     * is available in main memory.
     * @param itemsets the item set database. Each row is a item set, which
     * may have different length. The item identifiers have to be in [0, n),
     * where n is the number of items. Item set should NOT contain duplicated
     * items. Note that it is reordered after the call.
     * @param minSupport the required minimum support of item sets in terms
     * of frequency.
     */
    public FPTree(int[][] itemsets, int minSupport) {
        this(freq(itemsets), minSupport);

        // Add each itemset into to the FP-tree.
        for (int[] itemset : itemsets) {
            add(itemset);
        }
    }
    
    /**
     * Returns the frequency of single items.
     * @param itemsets the transaction database.
     * @return the frequency of single items
     */
    private static int[] freq(int[][] itemsets) {
        int[] f = new int[Math.max(itemsets) + 1];
        for (int[] itemset : itemsets) {
            for (int i : itemset) {
                f[i]++;
            }
        }
        return f;
    }
    
    /**
     * Returns the number transactions in the database.
     * @return the number transactions in the database
     */
    public int size() {
        return numTransactions;
    }

    /**
     * Add an item set into the FP-tree.
     * @param itemset an item set, which should NOT contain duplicated items.
     * Note that it is reordered after the call.
     */
    public void add(int[] itemset) {
        numTransactions++;
        
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
    public void add(int index, int end, int[] itemset, int support) {
        root.add(index, end, itemset, support);
    }
}
