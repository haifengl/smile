/*******************************************************************************
 * Copyright (c) 2010 Haifeng Li
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package smile.data.measure;

import java.text.NumberFormat;
import java.text.ParseException;

/**
 * Continuous data are numerical data that can theoretically be measured
 * in infinitely small units. The statistical analysis of continuous data
 * is more powerful than that of categorical data.
 *
 * @author Haifeng Li
 */
public abstract class ContinuousMeasure implements Measure {
    /** For formatting and parsing numbers. */
    private NumberFormat format;

    /** Constructor. */
    public ContinuousMeasure(NumberFormat format) {
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
