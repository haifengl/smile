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

package smile.data.type;

/**
 * Short data type.
 *
 * @author Haifeng Li
 */
public class ShortType implements DataType {

    /** Singleton instance. */
    static ShortType instance = new ShortType();

    /**
     * Private constructor for singleton design pattern.
     */
    private ShortType() {
    }

    @Override
    public boolean isShort() {
        return true;
    }

    @Override
    public String name() {
        return "short";
    }

    @Override
    public ID id() {
        return ID.Short;
    }

    @Override
    public String toString() {
        return "short";
    }

    @Override
    public Short valueOf(String s) {
        return Short.valueOf(s);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ShortType;
    }
}
