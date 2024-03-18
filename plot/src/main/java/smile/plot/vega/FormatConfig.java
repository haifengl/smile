/*
 * Copyright (c) 2010-2021 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */
package smile.plot.vega;

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * These config properties define the default number and time formats
 * for text marks as well as axes, headers, tooltip, and legends.
 *
 * @author Haifeng Li
 */
public class FormatConfig {
    /** VegaLite's FormatConfig object. */
    final ObjectNode spec;

    /**
     * Hides the constructor so that users cannot create the instances directly.
     */
    FormatConfig(ObjectNode spec) {
        this.spec = spec;
    }

    /**
     * Sets custom number format.
     */
    public FormatConfig numberFormat(String format) {
        spec.put("numberFormat", format);
        return this;
    }

    /**
     * Sets custom number format type.
     */
    public FormatConfig numberFormatType(String formatType) {
        spec.put("numberFormatType", formatType);
        return this;
    }

    /**
     * Sets custom normalized number format.
     */
    public FormatConfig normalizedNumberFormat(String format) {
        spec.put("normalizedNumberFormat", format);
        return this;
    }

    /**
     * Sets custom normalized number format type.
     */
    public FormatConfig normalizedNumberFormatType(String formatType) {
        spec.put("normalizedNumberFormatType", formatType);
        return this;
    }

    /**
     * Sets custom time format.
     */
    public FormatConfig timeFormat(String format) {
        spec.put("timeFormat", format);
        return this;
    }

    /**
     * Sets custom time format type.
     */
    public FormatConfig timeFormatType(String formatType) {
        spec.put("timeFormatType", formatType);
        return this;
    }

    /**
     * Allow the formatType property for text marks and guides to accept
     * a custom formatter function registered as a Vega expression.
     */
    public FormatConfig customFormatTypes(boolean flag) {
        spec.put("customFormatTypes", flag);
        return this;
    }
}
