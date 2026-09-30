/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Serve is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * SMILE Serve is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with SMILE Serve. If not, see
 * <https://www.gnu.org/licenses/>.
 */
package smile.serve;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/**
 * The inference response containing a prediction and optional class
 * probability estimates for soft classification models and optional
 * model explanations.
 *
 * @param prediction    the predicted value (class label or regression output).
 * @param probabilities posteriori class probabilities for soft classifiers;
 *                      {@code null} for hard classifiers and regressors.
 * @param explanations  model explanations (e.g. SHAP values); {@code null} when
 *                      explanations were not requested.
 * @author Haifeng Li
 */
public record InferenceResponse(
        Number prediction,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonSerialize(using = ProbabilitySerializer.class)
        double[] probabilities,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        Explanations explanations) {

    /**
     * Constructs a response without probability estimates or explanations (hard classifier
     * or regressor output).
     *
     * @param prediction the predicted value.
     */
    public InferenceResponse(Number prediction) {
        this(prediction, null, null);
    }

    /**
     * Constructs a response with probability estimates but without explanations.
     *
     * @param prediction    the predicted value.
     * @param probabilities posteriori class probabilities.
     */
    public InferenceResponse(Number prediction, double[] probabilities) {
        this(prediction, probabilities, null);
    }

    @Override
    public String toString() {
        if (prediction == null) return "null";
        String s = prediction.toString();
        if (probabilities != null) {
            s += Arrays.stream(probabilities)
                    .mapToObj(p -> String.format(Locale.US, "%.3f", p))
                    .collect(Collectors.joining(" ", " ", ""));
        }
        return s;
    }
}
