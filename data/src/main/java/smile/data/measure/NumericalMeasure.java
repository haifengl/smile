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

package smile.data.measure;

import java.text.NumberFormat;
import java.text.ParseException;

/**
 * Numerical data, also called quantitative data. Numerical data may be
 * discrete or continuous. Continuous data may be subdivided into interval
 * data and ratio data.
 *
 * @author Haifeng Li
 */
public abstract class NumericalMeasure implements Measure {
    /** For formatting and parsing numbers. */
    private final NumberFormat format;

    /**
     * Constructor.
     * @param format the number format.
     */
    public NumericalMeasure(NumberFormat format) {
        this.format = format;
    }

    @Override
    public String toString(Object o) {
        return format.format(o);
    }

    @Override
    public Number valueOf(String s) throws NumberFormatException {
        try {
            return format.parse(s);
        } catch (ParseException ex) {
            throw new NumberFormatException(ex.getMessage());
        }
    }
}
