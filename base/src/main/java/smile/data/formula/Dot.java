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
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.data.formula;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import smile.data.type.StructType;

/**
 * The special term "." means all columns not otherwise in the formula
 * in the context of a data frame.
 *
 * @author Haifeng Li
 */
public class Dot implements Term {
    /**
     * Constructor.
     */
    public Dot() {

    }

    @Override
    public String toString() {
        return ".";
    }

    @Override
    public Set<String> variables() {
        // As bind() should not be called, we simply return an empty set.
        return Collections.emptySet();
    }

    @Override
    public List<Feature> bind(StructType schema) {
        throw new IllegalStateException("Dot.bind() should not be called.");
    }
}
