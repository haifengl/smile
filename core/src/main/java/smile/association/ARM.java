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
import smile.association.TotalSupportTree.Node;

/**
 * Association Rule Mining.
 * Let I = {i<sub>1</sub>, i<sub>2</sub>,..., i<sub>n</sub>} be a set of n
 * binary attributes called items. Let D = {t<sub>1</sub>, t<sub>2</sub>,..., t<sub>m</sub>}
 * be a set of transactions called the database. Each transaction in D has a
 * unique transaction ID and contains a subset of the items in I.
 * An association rule is defined as an implication of the form X &rArr; Y
 * where X, Y &sube; I and X &cap; Y = &Oslash;. The item sets X and Y are called
 * antecedent (left-hand-side or LHS) and consequent (right-hand-side or RHS)
 * of the rule, respectively. The support supp(X) of an item set X is defined as
 * the proportion of transactions in the database which contain the item set.
 * Note that the support of an association rule X &rArr; Y is supp(X &cup; Y).
 * The confidence of a rule is defined conf(X &rArr; Y) = supp(X &cup; Y) / supp(X).
 * Confidence can be interpreted as an estimate of the probability P(Y | X),
 * the probability of finding the RHS of the rule in transactions under the
 * condition that these transactions also contain the LHS.
 * Association rules are usually required to satisfy a user-specified minimum
 * support and a user-specified minimum confidence at the same time.
 * 
 * @author Haifeng Li
 */
public class ARM {

    /**
     * FP-growth algorithm object.
     */
    private FPGrowth fim;
    /**
     * Compressed set enumeration tree.
     */
    private TotalSupportTree ttree;

    /**
     * Constructor. This is for mining frequent item sets by scanning database twice.
     * The user first scans the database to obtains the frequency of single items and calls
     * this constructor. Then the user add item sets to the object by {@link #add(int[])} during the
     * second scan of the database. In this way, we don't need load the whole database
     * into the main memory.
     * @param frequency the frequency of single items.
     * @param minSupport the required minimum support of item sets in terms of frequency.
     */
    public ARM(int[] frequency, int minSupport) {
        fim = new FPGrowth(frequency, minSupport);
    }

    /**
     * Constructor. This is a one-step construction if the database
     * is available in main memory.
     * @param itemsets the item set dataset. Each row is a item set,
     * which may have different length. The item identifiers have to be in [0, n),
     * where n is the number of items. Item set should NOT contain duplicated
     * items.
     * @param minSupport the required minimum support of item sets in
     * terms of percentage.
     */
    public ARM(int[][] itemsets, double minSupport) {
        fim = new FPGrowth(itemsets, minSupport);
    }

    /**
     * Constructor. This is a one-step construction if the database
     * is available in main memory.
     * @param itemsets the item set database. Each row is a item set, which
     * may have different length. The item identifiers have to be in [0, n),
     * where n is the number of items. Item set should NOT contain duplicated
     * items. Note that it is reordered after the call.
     * @param minSupport the required minimum support of item sets in
     * terms of frequency.
     */
    public ARM(int[][] itemsets, int minSupport) {
        fim = new FPGrowth(itemsets, minSupport);
    }

    /**
     * Add an item set to the database.
     * @param itemset an item set, which should NOT contain duplicated items.
     * Note that it is reordered after the call.
     */
    public void add(int[] itemset) {
        fim.add(itemset);
    }

    /**
     * Mines the association rules. The discovered rules will be printed out
     * to the provided stream.
     * @param confidence the confidence threshold for association rules.
     * @return the number of discovered association rules.
     */
    public long learn(double confidence, PrintStream out) {
        long n = 0;
        ttree = fim.buildTotalSupportTree();
        for (int i = 0; i < ttree.root.children.length; i++) {
            if (ttree.root.children[i] != null) {
                int[] itemset = {ttree.root.children[i].id};
                n += learn(out, null, itemset, i, ttree.root.children[i], confidence);
            }
        }
        return n;
    }

    /**
     * Mines the association rules. The discovered frequent rules will be returned in a list.
     * @param confidence the confidence threshold for association rules.
     */
    public List<AssociationRule> learn(double confidence) {
        List<AssociationRule> list = new ArrayList<>();
        ttree = fim.buildTotalSupportTree();
        for (int i = 0; i < ttree.root.children.length; i++) {
            if (ttree.root.children[i] != null) {
                int[] itemset = {ttree.root.children[i].id};
                learn(null, list, itemset, i, ttree.root.children[i], confidence);
            }
        }
        return list;
    }

    /**
     * Generates association rules from a T-tree.
     * @param itemset the label for a T-tree node as generated sofar.
     * @param size the size of the current array level in the T-tree.
     * @param node the current node in the T-tree.
     * @param confidence the confidence threshold for association rules.
     */
    private long learn(PrintStream out, List<AssociationRule> list, int[] itemset, int size, Node node, double confidence) {
        long n = 0;
        if (node.children == null) {
            return n;
        }

        for (int i = 0; i < size; i++) {
            if (node.children[i] != null) {
                int[] newItemset = FPGrowth.insert(itemset, node.children[i].id);
                // Generate ARs for current large itemset
                n += learn(out, list, newItemset, node.children[i].support, confidence);
                // Continue generation process
                n += learn(out, list, newItemset, i, node.children[i], confidence);
            }
        }
        
        return n;
    }

    /**
     * Generates all association rules for a given item set.
     * @param itemset the given frequent item set.
     * @param support the associated support value for the item set.
     * @param confidence the confidence threshold for association rules.
     */
    private long learn(PrintStream out, List<AssociationRule> list, int[] itemset, int support, double confidence) {
        long n = 0;
        // Determine combinations
        int[][] combinations = getPowerSet(itemset);

        // Loop through combinations
        for (int i = 0; i < combinations.length; i++) {
            // Find complement of combination in given itemSet
            int[] complement = getComplement(combinations[i], itemset);
            // If complement is not empty generate rule
            if (complement != null) {
                double arc = getConfidence(combinations[i], support);
                if (arc >= confidence) {
                    double supp = (double) support / fim.size();
                    AssociationRule ar = new AssociationRule(combinations[i], complement, supp, arc);
                    n++;

                    if (out != null) {
                        out.println(ar);
                    }

                    if (list != null) {
                        list.add(ar);
                    }
                }
            }
        }
        
        return n;
    }

    /**
     * Returns the confidence for a rule given the antecedent item set and
     * the support for the whole item set.
     * @param antecedent the antecedent (LHS) of the AR.
     * @param support the support for the whole item set.
     */
    private double getConfidence(int[] antecedent, double support) {
        return support / ttree.getSupport(antecedent);
    }

    /**
     * Returns the complement of subset.
     */
    private static int[] getComplement(int[] subset, int[] fullset) {
        int size = fullset.length - subset.length;

        // Returns null if no complement
        if (size < 1) {
            return null;
        }

        // Otherwsise define combination array and determine complement
        int[] complement = new int[size];
        int index = 0;
        for (int i = 0; i < fullset.length; i++) {
            int item = fullset[i];
            boolean member = false;
            for (int j = 0; j < subset.length; j++) {
                if (item == subset[j]) {
                    member = true;
                    break;
                }
            }

            if (!member) {
                complement[index++] = item;
            }

        }

        return complement;
    }

    /**
     * Returns all possible subsets except null and full set.
     */
    private static int[][] getPowerSet(int[] set) {
        int[][] sets = new int[getPowerSetSize(set.length)][];
        getPowerSet(set, 0, null, sets, 0);
        return sets;
    }

    /**
     * Recursively calculates all possible subsets.
     * @param set the input item set.
     * @param inputIndex the index within the input set marking current
     * element under consideration (0 at start).
     * @param sofar the current combination determined sofar during the
     * recursion (null at start).
     * @param sets the power set to store all combinations when recursion ends.
     * @param outputIndex the current location in the output set.
     * @return revised output index.
     */
    private static int getPowerSet(int[] set, int inputIndex, int[] sofar, int[][] sets, int outputIndex) {
        for (int i = inputIndex; i < set.length; i++) {
            int n = sofar == null ? 0 : sofar.length;
            if (n < set.length-1) {
                int[] subset = new int[n + 1];
                subset[n] = set[i];
                if (sofar != null) {
                    System.arraycopy(sofar, 0, subset, 0, n);
                }

                sets[outputIndex] = subset;
                outputIndex = getPowerSet(set, i + 1, subset, sets, outputIndex + 1);
            }
        }

        return outputIndex;
    }

    /**
     * Returns the size of power set except null and full set.
     * @param n the size of set.
     */
    private static int getPowerSetSize(int n) {
        return (int) Math.pow(2.0, n) - 2;
    }
}
