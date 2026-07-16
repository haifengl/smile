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
    /**
     * Factory to recreate the NumberFormat for each thread.
     * {@link NumberFormat} is not thread-safe, so each thread gets its own
     * copy via a {@link ThreadLocal}.
     */
    private final transient ThreadLocal<NumberFormat> localFormat;

    /**
     * Constructor.
     * @param format the prototype number format (used as a template per thread).
     */
    public NumericalMeasure(NumberFormat format) {
        this.localFormat = ThreadLocal.withInitial(() -> (NumberFormat) format.clone());
    }

    /**
     * Returns the thread-local NumberFormat instance.
     * @return the thread-local format.
     */
    protected NumberFormat format() {
        return localFormat.get();
    }

    @Override
    public String toString(Object o) {
        return format().format(o);
    }

    @Override
    public Number valueOf(String s) throws NumberFormatException {
        try {
            return format().parse(s);
        } catch (ParseException ex) {
            throw new NumberFormatException(ex.getMessage());
        }
    }
}
