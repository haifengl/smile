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
 * The density transform performs one-dimensional kernel density estimation
 * over an input data stream and generates a new data stream of samples of
 * the estimated densities.
 *
 * @author Haifeng Li
 */
public class DensityTransform {
    /** VegaLite's Density definition object. */
    final ObjectNode spec;

    /**
     * Hides the constructor so that users cannot create the instances directly.
     */
    DensityTransform(ObjectNode spec) {
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
     * Produces density estimates or cumulative density estimates.
     * @param flag If true, produce cumulative density estimates. Otherwise, density estimates.
     * @return this object.
     */
    public DensityTransform cumulative(boolean flag) {
        spec.put("cumulative", flag);
        return this;
    }

    /**
     * Produces probability estimates or smoothed counts.
     * @param flag If true, produce smoothed counts. Otherwise, probability estimates.
     * @return this object.
     */
    public DensityTransform counts(boolean flag) {
        spec.put("counts", flag);
        return this;
    }

    /**
     * Sets the bandwidth (standard deviation) of the Gaussian kernel.
     * If unspecified or set to zero, the bandwidth value is automatically
     * estimated from the input data using Scott's rule.
     * @param width the bandwidth (standard deviation) of the Gaussian kernel.
     * @return this object.
     */
    public DensityTransform bandwidth(double width) {
        spec.put("bandwidth", width);
        return this;
    }

    /**
     * Sets a [min, max] domain from which to sample the distribution.
     * If unspecified, the extent will be determined by the observed
     * minimum and maximum values of the density value field.
     * @param min the minimum value of the density value field.
     * @param max the maximum value of the density value field.
     * @return this object.
     */
    public DensityTransform extent(double min, double max) {
        spec.putArray("extent").add(min).add(max);
        return this;
    }

    /**
     * Sets the minimum number of samples to take along the extent domain
     * for plotting the density.
     * @param steps the minimum number of samples to take.
     * @return this object.
     */
    public DensityTransform minSteps(int steps) {
        spec.put("minsteps", steps);
        return this;
    }

    /**
     * Sets the maximum number of samples to take along the extent domain
     * for plotting the density.
     * @param steps the maximum number of samples to take.
     * @return this object.
     */
    public DensityTransform maxSteps(int steps) {
        spec.put("maxsteps", steps);
        return this;
    }

    /**
     * Sets the exact number of samples to take along the extent domain for
     * plotting the density. If specified, overrides both minsteps and maxsteps
     * to set an exact number of uniform samples. Potentially useful in
     * conjunction with a fixed extent to ensure consistent sample points
     * for stacked densities.
     * @param steps the exact number of samples to take.
     * @return this object.
     */
    public DensityTransform steps(int steps) {
        spec.put("steps", steps);
        return this;
    }

    /**
     * Sets the output fields for the sample value and corresponding density estimate.
     *
     * @param fields the output fields.
     * @return this object.
     */
    public DensityTransform as(String... fields) {
        ArrayNode node = spec.putArray("as");
        for (var field : fields) {
            node.add(field);
        }
        return this;
    }
}
