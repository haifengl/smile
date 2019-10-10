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

/**
 * This class provides a skeletal implementation of the Term interface,
 * to minimize the effort required to implement this interface.
 *
 * @author Haifeng Li
 */
public abstract class AbstractTerm implements Term {
    /**
     * Constructor.
     */
    public AbstractTerm() {

    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }

        if (o instanceof Term) {
            Term t = (Term) o;
            return name().equals(t.name());
        }

        return false;
    }

    @Override
    public int hashCode() {
        return name().hashCode();
    }
}
