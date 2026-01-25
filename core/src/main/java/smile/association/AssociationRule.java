/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.association;

import java.util.Arrays;

/**
 * Association rule object. Let
 * <code>I = {i<sub>1</sub>, i<sub>2</sub>,..., i<sub>n</sub>}</code>
 * be a set of <code>n</code> binary attributes called items. Let
 * <code>D = {t<sub>1</sub>, t<sub>2</sub>,..., t<sub>m</sub>}</code>
 * be a set of transactions called the database. Each transaction in
 * <code>D</code> has a unique transaction ID and contains a subset
 * of the items in <code>I</code>. An association rule is defined
 * as an implication of the form <code>X &rArr; Y</code>
 * where <code>X, Y &sube; I</code> and <code>X &cap; Y = &Oslash;</code>.
 * The item sets <code>X</code> and <code>Y</code> are called
 * antecedent (left-hand-side or LHS) and consequent (right-hand-side or RHS)
 * of the rule, respectively.
 * <p>
 * The support <code>supp(X)</code> of an item
 * set <code>X</code> is defined as the proportion of transactions
 * in the database which contain the item set. Note that the support of
 * an association rule <code>X &rArr; Y</code> is <code>supp(X &cup; Y)</code>.
 * <p>
 * The confidence of a rule is defined as
 * <code>conf(X &rArr; Y) = supp(X &cup; Y) / supp(X)</code>.
 * Confidence can be interpreted as an estimate of the probability
 * <code>P(Y | X)</code>, the probability of finding the RHS of the rule
 * in transactions under the condition that these transactions also contain
 * the LHS.
 * <p>
 * Lift is a measure of the performance of a targeting model
 * (association rule) at predicting or classifying cases as having
 * an enhanced response (with respect to the population as a whole),
 * measured against a random choice targeting model. A targeting model
 * is doing a good job if the response within the target is much better
 * than the average for the population as a whole. Lift is simply the ratio
 * of these values: target response divided by average response.
 * For an association rule <code>X &rArr; Y</code>, if the lift is equal
 * to 1, it means that X and Y are independent. If the lift is higher
 * than 1, it means that X and Y are positively correlated.
 * If the lift is lower than 1, it means that X and Y are negatively
 * correlated.
 *
 * @param antecedent the antecedent itemset (LHS) of the association rule.
 * @param consequent the consequent itemset (RHS) of the association rule.
 * @param support    the proportion of instances in the dataset that contain an itemset.
 * @param confidence the percentage of instances that contain the consequent
 *                   and antecedent together over the number of instances that
 *                   only contain the antecedent.
 * @param lift       how many times more often antecedent and consequent occur together
 *                   than expected if they were statistically independent.
 * @param leverage   the difference between the probability of the rule and the expected
 *                   probability if the items were statistically independent.
 * @author Haifeng Li
 */
public record AssociationRule(int[] antecedent, int[] consequent, double support, double confidence, double lift, double leverage) {

    @Override
    public boolean equals(Object o) {
        if (o instanceof AssociationRule a) {
            return support == a.support && confidence != a.confidence
                    && Arrays.equals(antecedent, a.antecedent)
                    && Arrays.equals(consequent, a.consequent);
        }
        return false;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 13 * hash + Arrays.hashCode(this.antecedent);
        hash = 13 * hash + Arrays.hashCode(this.consequent);
        hash = 13 * hash + Long.hashCode(Double.doubleToLongBits(this.support));
        hash = 13 * hash + Long.hashCode(Double.doubleToLongBits(this.confidence));
        return hash;
    }
    
    @Override
    public String toString() {
        return "AssociationRule(" +
                Arrays.toString(antecedent) +
                " => " +
                Arrays.toString(consequent) +
                String.format(", support=%.1f%%, confidence=%.1f%%, lift=%.2f, leverage=%.3f)", 100 * support, 100 * confidence, lift, leverage);
    }
}
