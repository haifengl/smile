package smile.model;

import java.io.Serial;
import java.io.Serializable;
import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.databind.annotation.JsonSerialize;

/**
 * Model prediction explanations.
 *
 * @param shap the SHAP values (double[] for regression, double[][] for classification,
 *             or String for unsupported error message).
 * @author Haifeng Li
 */
public record Explanations(
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonSerialize(using = ShapSerializer.class)
        Object shap
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
