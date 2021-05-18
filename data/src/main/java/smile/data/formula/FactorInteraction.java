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
import smile.data.Tuple;
import smile.data.measure.CategoricalMeasure;
import smile.data.measure.NominalScale;
import smile.data.type.DataTypes;
import smile.data.type.StructField;
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
public class FactorInteraction implements Term {
    /** The factors of interaction. */
    private final List<String> factors;

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

    /**
     * Returns the number of factors in the interaction.
     * @return the number of factors in the interaction.
     */
    public int size() {
        return factors.size();
    }

    @Override
    public String toString() {
        return String.join(":", factors);
    }

    @Override
    public Set<String> variables() {
        return new HashSet<>(factors);
    }

    @Override
    public List<Feature> bind(StructType schema) {
        List<StructField> fields = factors.stream()
                .map(schema::field)
                .collect(Collectors.toList());

        for (StructField field : fields) {
            if (!(field.measure instanceof CategoricalMeasure)) {
                throw new IllegalStateException(String.format("%s is not a categorical variable: %s", field.name, field.measure));
            }
        }

        List<String> levels = new ArrayList<>();
        levels.add("");
        for (StructField field : fields) {
            CategoricalMeasure cat = (CategoricalMeasure) field.measure;
            levels = levels.stream()
                    .flatMap(l -> Arrays.stream(cat.levels()).map(level -> l.isEmpty() ? level : l + ":" + level))
                    .collect(Collectors.toList());
        }
        NominalScale measure = new NominalScale(levels);

        Feature feature = new Feature() {
            final StructField field = new StructField(
                    String.join(":", factors),
                    DataTypes.IntegerType,
                    measure
            );

            @Override
            public String toString() {
                return field.name;
            }

            @Override
            public StructField field() {
                return field;
            }

            @Override
            public int applyAsInt(Tuple o) {
                String level = factors.stream().map(o::getString).collect(Collectors.joining(":"));
                return measure.valueOf(level).intValue();
            }

            @Override
            public Object apply(Tuple o) {
                String level = factors.stream().map(o::getString).collect(Collectors.joining(":"));
                return measure.valueOf(level);
            }
        };

        return Collections.singletonList(feature);
    }
}
