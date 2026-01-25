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
package smile.plot.vega;

import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The density transform performs one-dimensional kernel density estimation
 * over an input data stream and generates a new data stream of samples of
 * the estimated densities.
 *
 * @author Haifeng Li
 */
public class LookupData {
    /** VegaLite's LookupData definition object. */
    final ObjectNode spec;
    /** The secondary data source to lookup in. */
    final Data data;

    /**
     * Hides the constructor so that users cannot create the instances directly.
     */
    LookupData(ObjectNode spec, Data data) {
        this.spec = spec;
        this.data = data;
    }

    @Override
    public String toString() {
        return spec.toString();
    }

    /**
     * Returns the specification in pretty print.
     * @return the specification in pretty print.
     */
    public String toPrettyString() {
        return spec.toPrettyString();
    }

    /**
     * Returns the secondary data source.
     *
     * @return the secondary data source.
     */
    public Data data() {
        return data;
    }

    /**
     * Returns the fields in foreign data or selection to lookup.
     *
     * @param fields the fields in foreign data or selection to lookup.
     *              If not specified, the entire object is queried.
     * @return this object.
     */
    public LookupData fields(String... fields) {
        ArrayNode node = spec.putArray("fields");
        for (String field : fields) {
            node.add(field);
        }
        return this;
    }
}
