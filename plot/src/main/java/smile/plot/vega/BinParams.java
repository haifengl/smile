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
package smile.plot.vega;

import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * To test a data point in a filter transform or a test property in conditional
 * encoding, a predicate definition of the following forms must be specified:
 * <p>
 * - a Vega expression string, where datum can be used to refer to the current
 *   data object. For example, datum.b2 > 60 would test if the value in the
 *   field b2 for each data point is over 60.
 * <p>
 * - one of the field predicates: equal, lt, lte, gt, gte, range, oneOf, or valid.
 * <p>
 * - a parameter predicate, which defines the names of a selection that the data
 *   point should belong to (or a logical composition of selections).
 * <p>
 * - a logical composition of (1), (2), or (3).
 *
 * @author Haifeng Li
 */
public class BinParams {
    /** The BinParams specification. */
    final ObjectNode spec = VegaLite.mapper.createObjectNode();

    /** Constructor. */
    public BinParams() {

    }

    /**
     * Sets the value in the binned domain at which to anchor the bins,
     * shifting the bin boundaries if necessary to ensure that a boundary
     * aligns with the anchor value.
     *
     * @param value the anchor value.
     * @return this object.
     */
    public BinParams anchor(double value) {
        spec.put("anchor", value);
        return this;
    }

    /**
     * Sets the number base to use for automatic bin determination
     * (default is base 10).
     *
     * @param base the base number.
     * @return this object.
     */
    public BinParams base(int base) {
        spec.put("base", base);
        return this;
    }

    /**
     * Sets the scale factors indicating allowable subdivisions. The default
     * value is [5, 2], which indicates that for base 10 numbers (the default
     * base), the method may consider dividing bin sizes by 5 and/or 2.
     * For example, for an initial step size of 10, the method can check
     * if bin sizes of 2 (= 10/5), 5 (= 10/2), or 1 (= 10/(5*2)) might also
     * satisfy the given constraints.
     *
     * @param factors the scale factors
     * @return this object.
     */
    public BinParams divide(int... factors) {
        ArrayNode node = spec.putArray("divide");
        for (var factor : factors) {
            node.add(factor);
        }
        return this;
    }

    /**
     * Sets the range of desired bin values
     *
     * @param min the lower bound of desired bin values.
     * @param max the upper bound of desired bin values.
     * @return this object.
     */
    public BinParams extent(double min, double max) {
        spec.putArray("extent").add(min).add(max);
        return this;
    }

    /**
     * Sets the maximum number of bins.
     *
     * @param bins the maximum number of bins.
     * @return this object.
     */
    public BinParams maxBins(int bins) {
        spec.put("maxbins", bins);
        return this;
    }

    /**
     * Sets the minimum allowable step size (particularly useful for
     * integer values).
     *
     * @param size the minimum allowable step size.
     * @return this object.
     */
    public BinParams minStep(double size) {
        spec.put("minstep", size);
        return this;
    }

    /**
     * Sets the exact step size between bins.
     *
     * @param size the exact step size between bins.
     * @return this object.
     */
    public BinParams step(double size) {
        spec.put("step", size);
        return this;
    }

    /**
     * Sets an array of allowable step sizes to choose from.
     *
     * @param steps an array of allowable step sizes to choose from.
     * @return this object.
     */
    public BinParams steps(double... steps) {
        ArrayNode node = spec.putArray("steps");
        for (var step : steps) {
            node.add(step);
        }
        return this;
    }

    /**
     * If true, attempts to make the bin boundaries use human-friendly
     * boundaries, such as multiples of ten.
     *
     * @param flag If true, attempts to make the bin boundaries use
     *            human-friendly boundaries, such as multiples of ten.
     * @return this object.
     */
    public BinParams nice(int flag) {
        spec.put("nice", flag);
        return this;
    }
}
