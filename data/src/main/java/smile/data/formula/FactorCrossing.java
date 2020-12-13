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

package smile.data.formula;

import java.util.*;
import java.util.stream.Collectors;
import smile.data.type.StructType;

/**
 * Factor crossing. The crossing of a*b interpreted as a+b+a:b.
 * The ^ operator indicates crossing to the specified degree.
 * For example (a+b+c)^2 is identical to (a+b+c)*(a+b+c) which
 * in turn expands to a formula containing the main effects
 * for a, b and c together with their second-order interactions.
 *
 * @author Haifeng Li
 */
public class FactorCrossing implements Term {
    /** The order of interactions. */
    private final int order;
    /** The children factors. */
    private final List<String> factors;
    /** The terms. */
    private final List<Term> terms;

    /**
     * Constructor.
     *
     * @param factors the factors to be crossed.
     */
    public FactorCrossing(String... factors) {
        this(factors.length, factors);
    }

    /**
     * Constructor.
     *
     * @param order the order of interactions.
     * @param factors the factors to be crossed.
     */
    public FactorCrossing(int order, String... factors) {
        if (factors.length < 2) {
            throw new IllegalArgumentException("FactorCrossing takes at least two factors");
        }

        if (order < 2 || order > factors.length) {
            throw new IllegalArgumentException("Invalid order of interactions: " + order);
        }

        this.order = order;
        this.factors = Arrays.asList(factors);

        terms = new ArrayList<>();
        String[] work = new String[order];
        combination(terms, factors, work, order, 0, factors.length-1, 0);
        Comparator<Term> compareBySize = (Term o1, Term o2) -> {
            int n1 = o1 instanceof FactorInteraction ? ((FactorInteraction) o1).size() : 1;
            int n2 = o2 instanceof FactorInteraction ? ((FactorInteraction) o2).size() : 1;
            return n1 - n2;
        };
        terms.sort(compareBySize);
    }

    /**
     * Generates all combinations of `order` elements in the array `factors`.
     */
    private static void combination(List<Term> terms, String[] factors, String[] data, int order, int start, int end, int index) {
        if (index == order) {
            return;
        }

        for (int i = start; i <= end; i++) {
            data[index] = factors[i];
            if (index == 0) {
                terms.add(new Variable(data[index]));
            } else {
                terms.add(new FactorInteraction(Arrays.copyOf(data, index+1)));
            }

            combination(terms, factors, data, order, i+1, end, index+1);
        }
    }

    @Override
    public String toString() {
        if (order < factors.size()) {
            return factors.stream().collect(Collectors.joining(" x ", "(", ")^"+order));
        } else {
            return factors.stream().collect(Collectors.joining(" x ", "(", ")"));
        }
    }

    @Override
    public Set<String> variables() {
        return new HashSet<>(factors);
    }

    @Override
    public List<Term> expand() {
        return terms;
    }

    @Override
    public List<Feature> bind(StructType schema) {
        List<Feature> features = new ArrayList<>();
        for (Term term : terms) {
            features.addAll(term.bind(schema));
        }
        return features;
    }
}
