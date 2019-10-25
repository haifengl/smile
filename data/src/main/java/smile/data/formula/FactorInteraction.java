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
 * The interaction of all the factors appearing in the term.
 * The interaction of one factor with another is that each
 * of its levels is tested in each level of the other factor.
 * For example, in a test of influences on crop yield, a watering
 * regime factor (W: wet and dry) is crossed with a sowing density
 * factor (D: high and low) when the response to the wet regime
 * is tested at both high and low sowing density, and so is the
 * response to the dry regime. If each of the four combinations
 * of levels has replicate observations, then a cross-factored
 * analysis can test for an interaction between the two treatment
 * factors in their effect on the response.
 *
 * @author Haifeng Li
 */
class FactorInteraction implements HyperTerm {
    /** The factors of interaction. */
    private List<String> factors;
    /** The terms after binding. */
    private List<OneHotEncoderInteraction> terms;

    /**
     * Constructor.
     *
     * @param factors the factors of interaction.
     */
    public FactorInteraction(String... factors) {
        if (factors.length < 2) {
            throw new IllegalArgumentException("Interaction() takes at least two factors");
        }

        this.factors = Arrays.asList(factors);
    }

    @Override
    public String toString() {
        return factors.stream().collect(Collectors.joining(":"));
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

        terms = encoders.stream()
                .filter(list -> list.size() == factors.size())
                .map(list -> new OneHotEncoderInteraction(list))
                .collect(Collectors.toList());
    }
}
