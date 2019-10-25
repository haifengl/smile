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
class FactorCrossing implements HyperTerm {
    /** The order of interactions. */
    private int order;
    /** The children factors. */
    private List<String> factors;
    /** The terms after binding. */
    private List<Term> terms;

    /**
     * Constructor.
     *
     * @param factors the factors to be crossed.
     */
    @SafeVarargs
    public FactorCrossing(String... factors) {
        this(factors.length, factors);
    }

    /**
     * Constructor.
     *
     * @param order the order of interactions.
     * @param factors the factors to be crossed.
     */
    @SafeVarargs
    public FactorCrossing(int order, String... factors) {
        if (factors.length < 2) {
            throw new IllegalArgumentException("Cross constructor takes at least two factors");
        }

        if (order < 2 || order > factors.length) {
            throw new IllegalArgumentException("Invalid order of interactions: " + order);
        }

        this.order = order;
        this.factors = Arrays.asList(factors);
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
    public List<? extends Term> terms() {
        return terms;
    }

    @Override
    public Set<String> variables() {
        return new HashSet<>(factors);
    }

    @Override
    public void bind(StructType schema) {
        List<List<OneHotEncoder>> encoders = new ArrayList<>();

        OneHot factor = new OneHot(factors.get(factors.size() - 1));
        factor.bind(schema);
        encoders.addAll(factor.terms().stream()
                        .map(term -> Collections.singletonList(term))
                        .collect(Collectors.toList())
        );

        for (int i = factors.size() - 2; i >= 0; i--) {
            factor = new OneHot(factors.get(i));
            factor.bind(schema);
            List<OneHotEncoder> terms = factor.terms();

            // combine terms with existing combinations
            encoders.addAll(encoders.stream().flatMap(list ->
                    terms.stream().map(term -> {
                        List<OneHotEncoder> newList = new ArrayList<>();
                        newList.add(term);
                        newList.addAll(list);
                        return newList;
                    })
            ).collect(Collectors.toList()));

            // add new single terms
            encoders.addAll(terms.stream()
                    .map(term -> Collections.singletonList(term))
                    .collect(Collectors.toList())
            );
        }

        // add single factor terms
        terms = factors.stream()
                .map(name -> {
                    Variable column = new Variable(name);
                    column.bind(schema);
                    return column;
                })
                .collect(Collectors.toList());

        // add interactions in ascending order
        for (int i = 2; i <= order; i++) {
            final int size = i;
            terms.addAll(encoders.stream()
                    .filter(list -> list.size() == size)
                    .map(list -> new OneHotEncoderInteraction(list))
                    .collect(Collectors.toList()));
        }
    }
}
