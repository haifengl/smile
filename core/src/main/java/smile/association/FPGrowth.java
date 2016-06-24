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
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import smile.association.FPTree.HeaderTableItem;
import smile.association.FPTree.Node;
import smile.util.MulticoreExecutor;

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
public class FPGrowth {
    private static final Logger logger = LoggerFactory.getLogger(FPGrowth.class);

    /**
     * The required minimum support of item sets.
     */
    private int minSupport;
    /**
     * FP-tree.
     */
    private FPTree T0;

    /**
     * Constructor. This is for mining frequent item sets by scanning database
     * twice. The user first scans the database to obtains the frequency of
     * single items and calls this constructor. Then the user add item sets to
     * the object by {@link #add(int[])} during the second scan of the database.
     * In this way, we don't need load the whole database into the main memory.
     * In the database, the item identifiers have to be in [0, n), where n is
     * the number of items.
     * @param frequency the frequency of single items.
     * @param minSupport the required minimum support of item sets in terms
     * of frequency.
     */
    public FPGrowth(int[] frequency, int minSupport) {
        this.minSupport = minSupport;
        T0 = new FPTree(frequency, minSupport);
    }

    /**
     * Constructor. This is a one-step construction of FP-tree if the database
     * is available in main memory.
     * @param itemsets the item set dataset. Each row is a item set, which
     * may have different length. The item identifiers have to be in [0, n),
     * where n is the number of items.
     * @param minSupport the required minimum support of item sets in terms
     * of percentage.
     */
    public FPGrowth(int[][] itemsets, double minSupport) {
        this(itemsets, (int) Math.ceil(itemsets.length * minSupport));
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
    public FPGrowth(int[][] itemsets, int minSupport) {
        this.minSupport = minSupport;
        T0 = new FPTree(itemsets, minSupport);
    }

    /**
     * Add an item set into the database.
     * @param itemset an item set, which should NOT contain duplicated items.
     * Note that it is reordered after the call.
     */
    public void add(int[] itemset) {
        T0.add(itemset);
    }
    
    /**
     * Returns the number transactions in the database.
     * @return the number transactions in the database
     */
    public int size() {
        return T0.size();
    }

    /**
     * Mines the frequent item sets. The discovered frequent item sets
     * will be returned in a list.
     * @return the list of frequent item sets
     */
    public List<ItemSet> learn() {
        List<ItemSet> list = new ArrayList<>();
        learn(null, list, null);
        return list;
    }

    /**
     * Mines the frequent item sets. The discovered frequent item sets
     * will be printed out to the provided stream.
     * @param out a print stream for output of frequent item sets.
     * @return the number of discovered frequent item sets.
     */
    public long learn(PrintStream out) {
        return learn(out, null, null);
    }

    /**
     * Mines the frequent item sets. The discovered frequent item sets
     * will be stored in a total support tree.
     */
    TotalSupportTree buildTotalSupportTree() {
        TotalSupportTree ttree = new TotalSupportTree(minSupport, T0.numFreqItems, T0.order);
        learn(null, null, ttree);
        return ttree;
    }
    
    /**
     * Mines the frequent item sets. The discovered frequent item sets
     * will be printed out to the provided stream.
     * @param out a print stream for output of frequent item sets.
     * @return the number of discovered frequent item sets.
     */
    private long learn(PrintStream out, List<ItemSet> list, TotalSupportTree ttree) {
        if (MulticoreExecutor.getThreadPoolSize() > 1) {
            return grow(out, list, ttree, T0, null, null, null);
        } else {
            return grow(out, list, ttree, T0, null);            
        }
    }

    /**
     * FP-Growth task to execute on each frequent item in the header table.
     */
    class FPGrowthTask implements Callable<Long> {
        /**
         * The header table item to start.
         */
        List<HeaderTableItem> headers;
        /**
         * A print stream for output of frequent item sets
         */
        PrintStream out;
        /**
         * A list to store frequent item sets.
         */
        List<ItemSet> list;
        /**
         * Total support tree to store frequent item sets. Used later for
         * association rule generation.
         */
        TotalSupportTree ttree;
        /**
         * A temporary buffer to store prefix of current item set during FP-growth.
         */
        int[] prefixItemset = null;
        /**
         * The local item support to generate conditional FP-tree.
         */
        int[] localItemSupport = null;

        /**
         * Constructor.
         */
        FPGrowthTask(List<HeaderTableItem> headers, PrintStream out, List<ItemSet> list, TotalSupportTree ttree) {
            this.headers = headers;
            this.out = out;
            this.list = list;
            this.ttree = ttree;
            prefixItemset = new int[T0.maxItemSetSize];
            localItemSupport = new int[T0.numItems];
        }

        @Override
        public Long call() {
            long n = 0;
            for (HeaderTableItem header : headers) {
                n += grow(out, list, ttree, header, null, localItemSupport, prefixItemset);
            }
            return n;
        }
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
    private long grow(PrintStream out, List<ItemSet> list, TotalSupportTree ttree, FPTree fptree, int[] itemset) {
        long n = 0;
        int[] prefixItemset = new int[T0.maxItemSetSize];
        int[] localItemSupport = new int[T0.numItems];
        
        // Loop through header table from end to start, item by item
        for (int i = fptree.headerTable.length; i-- > 0;) {
            n += grow(out, list, ttree, fptree.headerTable[i], itemset, localItemSupport, prefixItemset);
        }

        return n;
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
    private long grow(PrintStream out, List<ItemSet> list, TotalSupportTree ttree, FPTree fptree, int[] itemset, int[] localItemSupport, int[] prefixItemset) {
        if (fptree == T0) {
            int nprocs = MulticoreExecutor.getThreadPoolSize();
            List<List<HeaderTableItem>> headers = new ArrayList<>();
            for (int i = 0; i < 2*nprocs; i++) {
                headers.add(new ArrayList<>());
            }
            
            for (int i = fptree.headerTable.length; i-- > 0;) {
                headers.get(i % headers.size()).add(fptree.headerTable[i]);  
            }
            
            List<FPGrowthTask> tasks = new ArrayList<>();
            // Loop through header table from end to start, item by item
            for (int i = 0; i < headers.size(); i++) {
                // process trail of links from header table element
                tasks.add(new FPGrowthTask(headers.get(i), out, list, ttree));
            }

            long n = 0;
            try {
                List<Long> results = MulticoreExecutor.run(tasks);
                
                for (long i : results) {
                    n += i;
                }
            } catch (Exception e) {
                logger.error("Failed to run FPGrowth on multi-core", e);
            }         
            return n;
            
        } else {
            long n = 0;
            // Loop through header table from end to start, item by item
            for (int i = fptree.headerTable.length; i-- > 0;) {
                n += grow(out, list, ttree, fptree.headerTable[i], itemset, localItemSupport, prefixItemset);
            }

            return n;
        }
    }

    /**
     * Mines FP-tree with respect to a single element in the header table.
     * @param header the header table item of interest.
     * @param itemset the item set represented by the current FP-tree.
     */
    private long grow(PrintStream out, List<ItemSet> list, TotalSupportTree ttree, HeaderTableItem header, int[] itemset, int[] localItemSupport, int[] prefixItemset) {
        long n = 1;
        int support = header.count;
        int item = header.id;
        itemset = insert(itemset, item);
        
        if (list != null) {
            synchronized (list) {
                list.add(new ItemSet(itemset, support));
            }
        }
        if (out != null) {
            synchronized (out) {
                for (int i = 0; i < itemset.length; i++) {
                    out.format("%d ", itemset[i]);
                }
                out.format("(%d)%n", support);
            }
        }
        if (ttree != null) {
            synchronized (ttree) {
                ttree.add(itemset, support);
            }
        }
        
        if (header.node.next == null) {
            FPTree.Node node = header.node;
            while (node != null) {
                FPTree.Node parent = node.parent;
                int[] newItemset = itemset;
                while (parent != null) {
                    n++;
                    newItemset = insert(newItemset, parent.id);
                    if (list != null) {
                        synchronized (list) {
                            list.add(new ItemSet(newItemset, support));
                        }
                    }
                    if (out != null) {
                        synchronized (out) {
                            for (int i = 0; i < newItemset.length; i++) {
                                out.format("%d ", newItemset[i]);
                            }
                            out.format("(%d)%n", support);
                        }
                    }
                    if (ttree != null) {
                        synchronized (ttree) {
                            ttree.add(newItemset, support);
                        }
                    }
                    parent = parent.parent;
                }

                node = node.parent;
            }
            
        } else {
            // Count singles in linked list
            if (getLocalItemSupport(header.node, localItemSupport)) {
                // Create local FP tree
                FPTree fptree = getLocalFPTree(header.node, localItemSupport, prefixItemset);
                // Mine new FP-tree
                n += grow(out, list, ttree, fptree, itemset, localItemSupport, prefixItemset);
            }
        }

        return n;
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
        FPTree tree = new FPTree(localItemSupport, minSupport);

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
}
