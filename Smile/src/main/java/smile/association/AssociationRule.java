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
     * Constructor.
     * @param antecedent the antecedent itemset (LHS) of the association rule.
     * @param consequent the consequent itemset (RHS) of the association rule.
     * @param support    the associated support value.
     * @param confidence the associated confidence value.
     */
    public AssociationRule(int[] antecedent, int[] consequent, double support, double confidence) {
        this.antecedent = antecedent;
        this.consequent = consequent;
        this.support = support;
        this.confidence = confidence;
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

        sb.append(String.format(")\tsupport = %.2f%%\tconfidence = %.2f%%", 100*support, 100*confidence));

        return sb.toString();
    }
}
