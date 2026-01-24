/*
 * Copyright (c) 2010-2025 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful, but
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
 * The quantile transform calculates empirical quantile values for an input
 * data stream. If a groupby parameter is provided, quantiles are estimated
 * separately per group. Among other uses, the quantile transform is useful
 * for creating quantile-quantile (Q-Q) plots.
 *
 * @author Haifeng Li
 */
public class QuantileTransform {
    /** VegaLite's Quantile definition object. */
    final ObjectNode spec;

    /**
     * Hides the constructor so that users cannot create the instances directly.
     */
    QuantileTransform(ObjectNode spec) {
        this.spec = spec;
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
     * Sets a probability step size (default 0.01) for sampling quantile
     * values. All values from one-half the step size up to 1 (exclusive)
     * will be sampled. This parameter is only used if the probs parameter
     * is not provided.
     *
     * @param step a probability step size for sampling quantile values.
     * @return this object.
     */
    public QuantileTransform step(double step) {
        spec.put("step", step);
        return this;
    }

    /**
     * Sets an array of probabilities in the range (0, 1) for which to compute
     * quantile values. If not specified, the step parameter will be used.
     *
     * @param probs an array of probabilities in the range (0, 1).
     * @return this object.
     */
    public QuantileTransform probs(double[] probs) {
        ArrayNode node = spec.putArray("probs");
        for (var prob : probs) {
            node.add(prob);
        }
        return this;
    }

    /**
     * Sets the data fields to group by. If not specified, a single group
     * containing all data objects will be used.
     *
     * @param fields The data fields to group by. If not specified,
     *              a single group containing all data objects will be used.
     * @return this object.
     */
    public QuantileTransform groupby(String... fields) {
        ArrayNode node = spec.putArray("groupby");
        for (var field : fields) {
            node.add(field);
        }
        return this;
    }

    /**
     * Sets the output field names for the probability and quantile values.
     *
     * @param fields The output field names for the probability and quantile values.
     * @return this object.
     */
    public QuantileTransform as(String... fields) {
        ArrayNode node = spec.putArray("as");
        for (var field : fields) {
            node.add(field);
        }
        return this;
    }
}
