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
public class ARM implements Iterable<AssociationRule> {

    /**
     * The number transactions in the database.
     */
    private int size;
    /**
     * The confidence threshold for association rules.
     */
    private double confidence;
    /**
     * Compressed set enumeration tree.
     */
    private TotalSupportTree ttree;
    /**
     * The buffer to collect mining results.
     */
    private Queue<AssociationRule> buffer = new LinkedList<>();

    /**
     * Constructor.
     * @param confidence the confidence threshold for association rules.
     */
    ARM(double confidence, TotalSupportTree ttree) {
        this.size = ttree.size();
        this.confidence = confidence;
        this.ttree = ttree;
    }

    @Override
    public Iterator<AssociationRule> iterator() {
        return new Iterator<AssociationRule>() {
            int i = 0;

            @Override
            public boolean hasNext() {
                if (buffer.isEmpty()) {
                    for (; i < ttree.root.children.length; i++) {
                        Node child = ttree.root.children[i];
                        if (ttree.root.children[i] != null) {
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
            public AssociationRule next() {
                return buffer.poll();
            }
        };
    }

    /**
     * Mines the association rules.
     * @param confidence the confidence threshold for association rules.
     */
    public static Stream<AssociationRule> apply(double confidence, FPTree tree) {
        TotalSupportTree ttree = new TotalSupportTree(tree);
        ARM arm = new ARM(confidence, ttree);
        return StreamSupport.stream(arm.spliterator(), false);
    }

    /**
     * Generates association rules from a T-tree.
     * @param itemset the label for a T-tree node as generated sofar.
     * @param size the size of the current array level in the T-tree.
     * @param node the current node in the T-tree.
     */
    private void generate(int[] itemset, int size, Node node) {
        if (node.children == null) {
            return;
        }

        for (int i = 0; i < size; i++) {
            if (node.children[i] != null) {
                int[] newItemset = FPGrowth.insert(itemset, node.children[i].id);
                // Generate ARs for current large itemset
                generate(newItemset, node.children[i].support);
                // Continue generation process
                generate(newItemset, i, node.children[i]);
            }
        }
    }

    /**
     * Generates all association rules for a given item set.
     * @param itemset the given frequent item set.
     * @param support the associated support value for the item set.
     */
    private void generate(int[] itemset, int support) {
        // Determine combinations
        int[][] combinations = getPowerSet(itemset);

        // Loop through combinations
        for (int i = 0; i < combinations.length; i++) {
            // Find complement of combination in given itemSet
            int[] complement = getComplement(combinations[i], itemset);
            // If complement is not empty generate rule
            if (complement != null) {
                double antecedentSupport = ttree.getSupport(combinations[i]);
                double arc = support / antecedentSupport;
                if (arc >= confidence) {
                    double supp = (double) support / size;
                    double consequentSupport = ttree.getSupport(complement);
                    double lift = support / (antecedentSupport * consequentSupport / size);
                    double leverage = supp - (antecedentSupport / size) * (consequentSupport / size);
                    AssociationRule ar = new AssociationRule(combinations[i], complement, supp, arc, lift, leverage);
                    buffer.offer(ar);
                }
            }
        }
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
