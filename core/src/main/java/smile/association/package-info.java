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

/**
 * Frequent item set mining and association rule mining.
 * Association rule learning is a popular and well researched method for
 * discovering interesting relations between variables in large databases.
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
 * <p>
 * For example, the rule {onions, potatoes} &rArr; {burger} found in the sales
 * data of a supermarket would indicate that if a customer buys onions and
 * potatoes together, he or she is likely to also buy burger. Such information
 * can be used as the basis for decisions about marketing activities such as
 * promotional pricing or product placements.
 * <p>
 * Association rules are usually required to satisfy a user-specified minimum
 * support and a user-specified minimum confidence at the same time. Association
 * rule generation is usually split up into two separate steps:
 * <ul>
 * <li> First, minimum support is applied to find all frequent item sets
 * in a database (i.e. frequent item set mining).
 * <li> Second, these frequent item sets and the minimum confidence constraint
 * are used to form rules.
 * </ul>
 * <p>
 * Finding all frequent item sets in a database is difficult since it involves
 * searching all possible item sets (item combinations). The set of possible
 * item sets is the power set over I (the set of items) and has size 2<sup>n</sup> - 1
 * (excluding the empty set which is not a valid item set). Although the size
 * of the power set grows exponentially in the number of items n in I, efficient
 * search is possible using the downward-closure property of support
 * (also called anti-monotonicity) which guarantees that for a frequent item set
 * also all its subsets are frequent and thus for an infrequent item set, all
 * its supersets must be infrequent.
 * <p>
 * In practice, we may only consider the frequent item set that has the maximum
 * number of items bypassing all the sub item sets. An item set is maximal
 * frequent if none of its immediate supersets is frequent.
 * <p>
 * For a maximal frequent item set, even though we know that all the sub item
 * sets are frequent, we don't know the actual support of those sub item sets,
 * which are very important to find the association rules within the item sets.
 * If the final goal is association rule mining, we would like to discover
 * closed frequent item sets. An item set is closed if none of its immediate
 * supersets has the same support as the item set.
 * <p>
 * Some well known algorithms of frequent item set mining are Apriori,
 * Eclat and FP-Growth. Apriori is the best-known algorithm to mine association
 * rules. It uses a breadth-first search strategy to counting the support of
 * item sets and uses a candidate generation function which exploits the downward
 * closure property of support. Eclat is a depth-first search algorithm using
 * set intersection.
 * <p>
 * FP-growth (frequent pattern growth) uses an extended prefix-tree (FP-tree)
 * structure to store the database in a compressed form. FP-growth adopts a
 * divide-and-conquer approach to decompose both the mining tasks and the
 * databases. It uses a pattern fragment growth method to avoid the costly
 * process of candidate generation and testing used by Apriori.
 * 
 * <h2>References</h2>
 * <ol>
 * <li> R. Agrawal, T. Imielinski and A. Swami. Mining Association Rules Between Sets of Items in Large Databases, SIGMOD, 207-216, 1993.</li>
 * <li> Rakesh Agrawal and Ramakrishnan Srikant. Fast algorithms for mining association rules in large databases. VLDB, 487-499, 1994.</li>
 * <li> Mohammed J. Zaki. Scalable algorithms for association mining. IEEE Transactions on Knowledge and Data Engineering, 12(3):372-390, 2000.</li>
 * <li> Jiawei Han, Jian Pei, Yiwen Yin, and Runying Mao. Mining frequent patterns without candidate generation. Data Mining and Knowledge Discovery 8:53-87, 2004.</li>
 * </ol>
 * 
 * @author Haifeng Li
 */
package smile.association;
