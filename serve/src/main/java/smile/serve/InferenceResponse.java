/*
 * Copyright (c) 2010-2025 Haifeng Li. All rights reserved.
 *
 * Smile Shell is free software: you can redistribute it and/or modify
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile Shell is distributed in the hope that it will be useful,
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.serve;

import java.util.Arrays;
import java.util.stream.Collectors;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The generic inference request.
 *
 * @author Haifeng Li
 */
public class InferenceResponse {
    /** The predicted value. */
    public Number prediction;
    /** Posteriori probabilities in case of soft classification. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public double[] probabilities;

    /** Constructor. */
    public InferenceResponse() {
    }

    /** Constructor. */
    public InferenceResponse(Number prediction) {
        this.prediction = prediction;
    }

    /** Constructor. */
    public InferenceResponse(Number prediction, double[] probabilities) {
        this.prediction = prediction;
        this.probabilities = probabilities;
    }

    @Override
    public String toString() {
        String s = prediction.toString();
        if (probabilities != null) {
            s += Arrays.stream(probabilities).mapToObj(p -> String.format("%.3f", p)).collect(Collectors.joining(" ", " ", ""));
        }
        return s;
    }
}
