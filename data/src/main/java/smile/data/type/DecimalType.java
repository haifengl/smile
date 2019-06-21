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

import java.math.BigDecimal;

/**
 * Arbitrary-precision decimal data type.
 *
 * @author Haifeng Li
 */
public class DecimalType implements DataType {

    /** Singleton instance. */
    static DecimalType instance = new DecimalType();

    /**
     * Private constructor for singleton design pattern.
     */
    private DecimalType() {
    }

    @Override
    public String name() {
        return "Decimal";
    }

    @Override
    public ID id() {
        return ID.Decimal;
    }

    @Override
    public String toString() {
        return "Decimal";
    }

    @Override
    public BigDecimal valueOf(String s) {
        return new BigDecimal(s);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof DecimalType;
    }
}
