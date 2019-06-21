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

import smile.data.type.StructType;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Cross factor. One factor is crossed with another when each
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
class Cross implements HyperTerm {
    /** The children factors. */
    private List<Term> factors;

    /**
     * Constructor.
     *
     * @param factors the factors to be crossed.
     */
    @SafeVarargs
    public Cross(Term... factors) {
        if (factors.length < 2) {
            throw new IllegalArgumentException("Cross constructor takes at least two factors");
        }
        this.factors = Arrays.asList(factors);
    }

    @Override
    public String toString() {
        return factors.stream().map(Term::toString).collect(Collectors.joining(" + ", "(", ")^2"));
    }

    @Override
    public List<? extends Term> terms() {
        List<Term> crossings = new ArrayList<>(factors);
        int n = factors.size();
        for (int i = 0; i < n; i++) {
            Term fi = factors.get(i);
            for (int j = i + 1; j < n; j++) {
                Term cross = new Mul(fi, factors.get(j));
                crossings.add(cross);
            }
        }
        return crossings;
    }

    @Override
    public Set<String> variables() {
        return factors.stream().flatMap(f -> f.variables().stream()).collect(Collectors.toSet());
    }

    @Override
    public void bind(StructType schema) {
        factors.stream().forEach(factor -> factor.bind(schema));
    }
}
