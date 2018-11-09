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
package smile.data.formula;

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
public class Cross<T> implements Term {
    /** The children factors. */
    private List<Factor<T, Double>> factors;

    /**
     * Constructor.
     *
     * @param factors the factors to be crossed.
     */
    @SafeVarargs
    public Cross(Factor<T, Double>... factors) {
        if (factors.length < 2) {
            throw new IllegalArgumentException("Cross constructor takes at least two factors");
        }
        this.factors = Arrays.asList(factors);
    }

    @Override
    public String toString() {
        return String.format("(%s)^2", factors.stream().map(f -> f.toString()).collect(Collectors.joining(" + ")));
    }

    @Override
    public List<Factor> factors() {
        List<Factor> crossings = new ArrayList<>(factors);
        int n = factors.size();
        for (int i = 0; i < n; i++) {
            Factor<T, Double> fi = factors.get(i);
            for (int j = i + 1; j < n; j++) {
                Factor<T, Double> fj = factors.get(j);
                Factor cross = new Mul<>(fi, fj);
                crossings.add(cross);
            }
        }
        return crossings;
    }

    @Override
    public Set<String> variables() {
        return factors.stream().flatMap(f -> f.variables().stream()).collect(Collectors.toSet());
    }
}
