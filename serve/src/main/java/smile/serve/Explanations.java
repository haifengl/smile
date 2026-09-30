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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/**
 * Explanations container for model inference.
 *
 * @param shap SHAP values: {@code double[]} for regression, {@code double[][]} for
 *             classification ({@code [classes][features]}), or {@code "Not supported"}
 *             if the model does not support SHAP.
 * @author Haifeng Li
 */
public record Explanations(
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonSerialize(using = ShapSerializer.class)
        Object shap) {
}
