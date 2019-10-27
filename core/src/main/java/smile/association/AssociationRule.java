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

/**
 * Association rule object. Let I = {i<sub>1</sub>, i<sub>2</sub>,..., i<sub>n</sub>}
 * be a set of n binary attributes called items. Let
 * D = {t<sub>1</sub>, t<sub>2</sub>,..., t<sub>m</sub>}
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
 * 
 * @author Haifeng Li
 */
public class AssociationRule {

    /**
     * Antecedent itemset.
     */
    public final int[] antecedent;
    /**
     * Consequent itemset.
     */
    public final int[] consequent;
    /**
     * The support value. The support supp(X) of an itemset X is defined as
     * the proportion of transactions in the database which contain the itemset.
     */
    public final double support;
    /**
     * The confidence value. The confidence of a rule is defined
     * conf(X &rArr; Y) = supp(X &cup; Y) / supp(X). Confidence can be
     * interpreted as an estimate of the probability P(Y | X), the probability
     * of finding the RHS of the rule in transactions under the condition
     * that these transactions also contain the LHS.
     */
    public final double confidence;
    /**
     * How many times more often antecedent and consequent occur together
     * than expected if they were statistically independent.
     * Lift is a measure of the performance of a targeting model
     * (association rule) at predicting or classifying cases as having
     * an enhanced response (with respect to the population as a whole),
     * measured against a random choice targeting model. A targeting model
     * is doing a good job if the response within the target is much better
     * than the average for the population as a whole.
     *
     * Lift is simply the ratio of these values: target response divided by
     * average response.
     *
     * For an association rule X ==> Y, if the lift is equal to 1,
     * it means that X and Y are independent. If the lift is higher than 1,
     * it means that X and Y are positively correlated.
     * If the lift is lower than 1, it means that X and Y are negatively correlated.
     */
    public final double lift;
    /**
     * The difference between the probability of the rule and the expected
     * probability if the items were statistically independent.
     */
    public final double leverage;

    /**
     * Constructor.
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
     */
    public AssociationRule(int[] antecedent, int[] consequent, double support, double confidence, double lift, double leverage) {
        this.antecedent = antecedent;
        this.consequent = consequent;
        this.support = support;
        this.confidence = confidence;
        this.lift = lift;
        this.leverage = leverage;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof AssociationRule) {
            AssociationRule a = (AssociationRule) o;
            if (support != a.support) {
                return false;
            }
            
            if (confidence != a.confidence) {
                return false;
            }
            
            if (antecedent.length != a.antecedent.length) {
                return false;
            }
            
            if (consequent.length != a.consequent.length) {
                return false;
            }
            
            for (int i = 0; i < antecedent.length; i++) {
                if (antecedent[i] != a.antecedent[i]) {
                    return false;
                }
            }
            
            for (int i = 0; i < consequent.length; i++) {
                if (consequent[i] != a.consequent[i]) {
                    return false;
                }
            }
            
            return true;
        }
        
        return false;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 13 * hash + Arrays.hashCode(this.antecedent);
        hash = 13 * hash + Arrays.hashCode(this.consequent);
        hash = 13 * hash + (int) (Double.doubleToLongBits(this.support) ^ (Double.doubleToLongBits(this.support) >>> 32));
        hash = 13 * hash + (int) (Double.doubleToLongBits(this.confidence) ^ (Double.doubleToLongBits(this.confidence) >>> 32));
        return hash;
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append('(');
        sb.append(antecedent[0]);
        for (int i = 1; i < antecedent.length; i++) {
            sb.append(", ");
            sb.append(antecedent[i]);
        }

        sb.append(") => (");

        sb.append(consequent[0]);
        for (int i = 1; i < consequent.length; i++) {
            sb.append(", ");
            sb.append(consequent[i]);
        }

        sb.append(String.format(") support = %.2f%% confidence = %.2f%% lift = %.2f leverage = %.4f", 100*support, 100*confidence, lift, leverage));

        return sb.toString();
    }
}
