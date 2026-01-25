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
package smile.data.formula;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import smile.data.type.StructType;

/**
 * The flag if intercept should be included in the model.
 *
 * @param bias The flag if the model has the intercept.
 * @author Haifeng Li
 */
public record Intercept(boolean bias) implements Term {
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
