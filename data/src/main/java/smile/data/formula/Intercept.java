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

import java.util.Collections;
import java.util.List;
import java.util.Set;
import smile.data.type.StructType;

/**
 * The flag if intercept should be included in the model.
 *
 * @author Haifeng Li
 */
final class Intercept implements Term {
    /** The flag if the model has the intercept. */
    private final boolean bias;

    /**
     * Constructor.
     *
     * @param bias The flag if the model has the intercept.
     */
    public Intercept(boolean bias) {
        this.bias = bias;
    }

    /** Returns the flag if the intercept is included in the model. */
    public boolean bias() {
        return bias;
    }

    @Override
    public String toString() {
        return bias ? "1" : "0";
    }

    @Override
    public Set<String> variables() {
        return Collections.emptySet();
    }

    @Override
    public List<Feature> bind(StructType schema) {
        throw new IllegalStateException("Intercept.bind() should not be called.");
    }
}
