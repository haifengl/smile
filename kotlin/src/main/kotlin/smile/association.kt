/*
 * Copyright (c) 2010-2020 Haifeng Li. All rights reserved.
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
 */

package smile.association

import java.util.function.Supplier
import java.util.stream.Stream

/**
 * Builds a FP-tree.
 * @param supplier the lambda to retrun a stream of item set database. Each item set
 *                 may have different length. The item identifiers have to be in [0, n),
 *                 where n is the number of items. Item set should NOT contain duplicated
 *                 items. Note that it is reordered after the call.
 * @param minSupport the required minimum support of item sets in terms
 *                   of frequency.
 * @return the FP-tree.
 */
fun fptree(minSupport: Int, supplier: Supplier<Stream<IntArray>>): FPTree {
    return FPTree.of(minSupport, supplier)
}

/**
 * Frequent item set mining based on the FP-growth (frequent pattern growth)
 * algorithm, which employs an extended prefix-tree (FP-tree) structure to
 * store the database in a compressed form. The FP-growth algorithm is
 * currently one of the fastest approaches to discover frequent item sets.
 * FP-growth adopts a divide-and-conquer approach to decompose both the mining
 * tasks and the databases. It uses a pattern fragment growth method to avoid
 * the costly process of candidate generation and testing used by Apriori.
 *
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
 * @param itemsets the item set database. Each row is a item set, which
 *                 may have different length. The item identifiers have to be in [0, n),
 *                 where n is the number of items. Item set should NOT contain duplicated
 *                 items. Note that it is reordered after the call.
 * @param minSupport the required minimum support of item sets in terms
 *                   of frequency.
 * @return the stream of frequent item sets.
 */
fun fpgrowth(minSupport: Int, itemsets: Array<IntArray>): Stream<ItemSet> {
    val tree = FPTree.of(minSupport, itemsets)
    return FPGrowth.apply(tree)
}

/**
 * Frequent item set mining based on the FP-growth (frequent pattern growth)
 * algorithm, which employs an extended prefix-tree (FP-tree) structure to
 * store the database in a compressed form. The FP-growth algorithm is
 * currently one of the fastest approaches to discover frequent item sets.
 * FP-growth adopts a divide-and-conquer approach to decompose both the mining
 * tasks and the databases. It uses a pattern fragment growth method to avoid
 * the costly process of candidate generation and testing used by Apriori.
 *
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
 * @param tree the FP-tree of item set database.
 * @return the stream of frequent item sets.
 */
fun fpgrowth(tree: FPTree): Stream<ItemSet> {
    return FPGrowth.apply(tree)
}

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
 * @param itemsets the item set database. Each row is a item set, which
 *                 may have different length. The item identifiers have to be in [0, n),
 *                 where n is the number of items. Item set should NOT contain duplicated
 *                 items. Note that it is reordered after the call.
 * @param minSupport the required minimum support of item sets in terms
 *                   of frequency.
 * @param confidence the confidence threshold for association rules.
 * @return the stream of discovered association rules.
 */
fun arm(minSupport: Int, confidence: Double, itemsets: Array<IntArray>): Stream<AssociationRule> {
    val tree = FPTree.of(minSupport, itemsets)
    return ARM.apply(confidence, tree)
}

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
 * @param tree the FP-tree of item set database.
 * @param confidence the confidence threshold for association rules.
 * @return the stream of discovered association rules.
 */
fun arm(confidence: Double, tree: FPTree): Stream<AssociationRule> {
    return ARM.apply(confidence, tree)
}

