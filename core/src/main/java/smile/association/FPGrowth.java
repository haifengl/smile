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

import java.util.*;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import smile.association.FPTree.HeaderTableItem;
import smile.association.FPTree.Node;

/**
 * Frequent item set mining based on the FP-growth (frequent pattern growth)
 * algorithm, which employs an extended prefix-tree (FP-tree) structure to
 * store the database in a compressed form. The FP-growth algorithm is
 * currently one of the fastest approaches to discover frequent item sets.
 * FP-growth adopts a divide-and-conquer approach to decompose both the mining
 * tasks and the databases. It uses a pattern fragment growth method to avoid
 * the costly process of candidate generation and testing used by Apriori.
 * <p>
 * The basic idea of the FP-growth algorithm can be described as a
 * recursive elimination scheme: in a preprocessing step delete
 * all items from the transactions that are not frequent individually,
 * i.e., do not appear in a user-specified minimum
 * number of transactions. Then select all transactions that
 * contain the least frequent item (least frequent among those
 * that are frequent) and delete this item from them. Recurse
 * to process the obtained reduced (also known as projected)
 * database, remembering that the item sets found in the recursion
 * share the deleted item as a prefix. On return, remove
 * the processed item from the database of all transactions
 * and start over, i.e., process the second frequent item etc. In
 * these processing steps the prefix tree, which is enhanced by
 * links between the branches, is exploited to quickly find the
 * transactions containing a given item and also to remove this
 * item from the transactions after it has been processed.
 * 
 * <h2>References</h2>
 * <ol>
 * <li> Jiawei Han, Jian Pei, Yiwen Yin, and Runying Mao. Mining frequent patterns without candidate generation. Data Mining and Knowledge Discovery 8:53-87, 2004.</li>
 * <li> Gosta Grahne and Jianfei Zhu. Fast algorithms for frequent itemset mining using FP-trees. IEEE TRANS. ON KNOWLEDGE AND DATA ENGINEERING 17(10):1347-1362, 2005.</li>
 * <li> Christian Borgelt. An Implementation of the FP-growth Algorithm. OSDM, 1-5, 2005.</li>
 * </ol>
 * 
 * @author Haifeng Li
 */
public class FPGrowth implements Iterable<ItemSet> {
    /**
     * The required minimum support of item sets.
     */
    private int minSupport;
    /**
     * FP-tree.
     */
    private FPTree T0;
    /**
     * The buffer to collect mining results.
     */
    private Queue<ItemSet> buffer = new LinkedList<>();

    /**
     * Constructor.
     * @param tree the FP-tree.
     * of frequency.
     */
    FPGrowth(FPTree tree) {
        this.minSupport = tree.minSupport;
        T0 = tree;
    }

    /**
     * Returns the number transactions in the database.
     * @return the number transactions in the database
     */
    public int size() {
        return T0.size();
    }

    @Override
    public Iterator<ItemSet> iterator() {
        return new Iterator<ItemSet>() {
            int[] prefixItemset = new int[T0.maxItemSetSize];
            int[] localItemSupport = new int[T0.numItems];
            int i = T0.headerTable.length;

            @Override
            public boolean hasNext() {
                if (buffer.isEmpty()) {
                    /*
                     * Mines frequent item sets. Start with the bottom of the header table and
                     * work upwards. For each available FP tree node:
                     *
                     * - Count the support.
                     * - Build up item set sofar.
                     * - Add to supported sets.
                     * - Build a new FP tree: (i) create a new local root, (ii) create a
                     *   new local header table and (iii) populate with ancestors.
                     * - If new local FP tree is not empty repeat mining operation.
                     *
                     * Otherwise end.
                     */
                    if (i-- > 0) {
                        grow(T0.headerTable[i], null, localItemSupport, prefixItemset);
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
     * Mines the frequent item sets.
     * @param tree the FP-tree of item sets.
     * @return the stream of frequent item sets.
     */
    public static Stream<ItemSet> apply(FPTree tree) {
        FPGrowth growth = new FPGrowth(tree);
        return StreamSupport.stream(growth.spliterator(), false);
    }

    /**
     * Mines frequent item sets. Start with the bottom of the header table and
     * work upwards. For each available FP tree node:
     * <OL>
     * <LI> Count the support.
     * <LI> Build up item set sofar.
     * <LI> Add to supported sets.
     * <LI> Build a new FP tree: (i) create a new local root, (ii) create a
     * new local header table and (iii) populate with ancestors.
     * <LI> If new local FP tree is not empty repeat mining operation.
     * </OL>
     * Otherwise end.
     * @param itemset the current item sets as generated so far (null at start).
     */
    private void grow(FPTree fptree, int[] itemset, int[] localItemSupport, int[] prefixItemset) {
        // Loop through header table from end to start, item by item
        for (int i = fptree.headerTable.length; i-- > 0;) {
            grow(fptree.headerTable[i], itemset, localItemSupport, prefixItemset);
        }
    }

    /**
     * Adds an item set to the result.
     */
    private void collect(int[] itemset, int support) {
        buffer.offer(new ItemSet(itemset, support));
    }

    /**
     * Mines all combinations along a single path tree
     */
    private void grow(FPTree.Node node, int[] itemset, int support) {
        int height = 0;
        for (FPTree.Node currentNode = node; currentNode != null; currentNode = currentNode.parent) {
            height ++;
        }

        if (height > 0) {
            int[] items = new int[height];
            int i = 0;
            for (FPTree.Node currentNode = node; currentNode != null; currentNode = currentNode.parent) {
                items[i ++] = currentNode.id;
            }

            int[] itemIndexStack = new int[height];
            int itemIndexStackPos = 0;
            itemset = insert(itemset, items[itemIndexStack[itemIndexStackPos]]);
            collect(itemset, support);

            while (itemIndexStack[0] < height - 1) {
                if (itemIndexStack[itemIndexStackPos] < height - 1) {
                    itemIndexStackPos ++;
                    itemIndexStack[itemIndexStackPos] = itemIndexStack[itemIndexStackPos - 1] + 1;
                    itemset = insert(itemset, items[itemIndexStack[itemIndexStackPos]]);
                    collect(itemset, support);
                } else {
                    itemset = drop(itemset);
                    if (itemset != null) {
                        itemIndexStackPos --;
                        itemIndexStack[itemIndexStackPos] = itemIndexStack[itemIndexStackPos] + 1;
                        itemset[0] = items[itemIndexStack[itemIndexStackPos]];
                        collect(itemset, support);
                    }
                }
            }
        }
    }

    /**
     * Mines FP-tree with respect to a single element in the header table.
     * @param header the header table item of interest.
     * @param itemset the item set represented by the current FP-tree.
     */
    private void grow(HeaderTableItem header, int[] itemset, int[] localItemSupport, int[] prefixItemset) {
        int support = header.count;
        int item = header.id;
        itemset = insert(itemset, item);

        collect(itemset, support);
        
        if (header.node.next == null) {
            FPTree.Node node = header.node;
            grow(node.parent, itemset, support);
        } else {
            // Count singles in linked list
            if (getLocalItemSupport(header.node, localItemSupport)) {
                // Create local FP tree
                FPTree fptree = getLocalFPTree(header.node, localItemSupport, prefixItemset);
                // Mine new FP-tree
                grow(fptree, itemset, localItemSupport, prefixItemset);
            }
        }
    }

    /**
     * Counts the supports of single items in ancestor item sets linked list.
     * @return true if there are condition patterns given this node
     */
    private boolean getLocalItemSupport(FPTree.Node node, int[] localItemSupport) {
        boolean end = true;
        Arrays.fill(localItemSupport, 0);
        while (node != null) {
            int support = node.count;
            Node parent = node.parent;
            while (parent != null) {
                localItemSupport[parent.id] += support;
                parent = parent.parent;
                end = false;
            }
            
            node = node.next;
        }

        return !end;
    }

    /**
     * Generates a local FP tree
     * @param node the conditional patterns given this node to construct the local FP-tree.
     * @rerurn the local FP-tree.
     */
    private FPTree getLocalFPTree(FPTree.Node node, int[] localItemSupport, int[] prefixItemset) {
        FPTree tree = new FPTree(minSupport, localItemSupport);

        while (node != null) {
            Node parent = node.parent;
            int i = prefixItemset.length;
            while (parent != null) {
                if (localItemSupport[parent.id] >= minSupport) {
                    prefixItemset[--i] = parent.id;
                }
                parent = parent.parent;
            }

            if (i < prefixItemset.length) {
                tree.add(i, prefixItemset.length, prefixItemset, node.count);
            }

            node = node.next;
        }

        return tree;
    }

    /**
     * Insert a item to the front of an item set.
     * @param itemset the original item set.
     * @param item the new item to be inserted.
     * @return the combined item set
     */
    static int[] insert(int[] itemset, int item) {
        if (itemset == null) {
            int[] newItemset = {item};
            return newItemset;
            
        } else {
            int n = itemset.length + 1;
            int[] newItemset = new int[n];

            newItemset[0] = item;
            System.arraycopy(itemset, 0, newItemset, 1, n - 1);

            return newItemset;
        }
    }

    /**
     * Drops an item form the front of an item set.
     * @param itemset the original item set.
     * @return the reduced item set or null if the original is empty
     */
    private static int[] drop(int[] itemset) {
        if (itemset.length >= 1) {
            int n = itemset.length - 1;
            int[] newItemset = new int[n];

            System.arraycopy(itemset, 1, newItemset, 0, n);

            return newItemset;
        } else {
            return null;
        }
    }
}
