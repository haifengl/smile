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
package smile.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.annotation.JsonSerialize;

/**
 * The model prediction containing the predicted output value, optional class
 * probability estimates for soft classification models, and optional explanations.
 *
 * @param output        the predicted value (class label or regression output).
 * @param probabilities posteriori class probabilities for soft classifiers;
 *                      {@code null} for hard classifiers and regressors.
 * @param explanations  the prediction explanations; {@code null} if not requested.
 * @author Haifeng Li
 */
public record Prediction(
        @JsonProperty("prediction")
        Number output,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonSerialize(using = ProbabilitySerializer.class)
        double[] probabilities,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        Explanations explanations
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Constructs a prediction without probability estimates or explanations.
     *
     * @param output the predicted value.
     */
    public Prediction(Number output) {
        this(output, null, null);
    }

    /**
     * Constructs a prediction with optional probability estimates and no explanations.
     *
     * @param output        the predicted value.
     * @param probabilities posteriori class probabilities.
     */
    public Prediction(Number output, double[] probabilities) {
        this(output, probabilities, null);
    }

    /**
     * Serializes this prediction to a JSON string.
     *
     * @return the JSON string representation.
     */
    public String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize Prediction to JSON", e);
        }
    }

    @Override
    public String toString() {
        if (output == null) return "null";
        String s = (output instanceof Double d)
                ? smile.util.Strings.format(d.doubleValue())
                : (output instanceof Float f)
                ? smile.util.Strings.format(f.floatValue())
                : output.toString();
        if (probabilities != null) {
            s += Arrays.stream(probabilities)
                    .mapToObj(p -> String.format(Locale.US, "%.4f", p))
                    .collect(Collectors.joining(" ", " ", ""));
        }
        return s;
    }
}
