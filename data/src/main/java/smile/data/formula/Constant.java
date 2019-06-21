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

import java.util.Collections;
import java.util.Set;
import smile.data.type.StructType;

/**
 * A variable in the formula. A variable can be regarded as the
 * identity function that always returns the same value that was
 * used as its argument.
 *
 * @author Haifeng Li
 */
public abstract class Constant implements Term {
    @Override
    public boolean isConstant() {
        return true;
    }

    @Override
    public Set<String> variables() {
        return Collections.emptySet();
    }

    @Override
    public void bind(StructType schema) {
        // nop
    }
}
