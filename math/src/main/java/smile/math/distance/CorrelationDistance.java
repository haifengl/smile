/*******************************************************************************
 * Copyright (c) 2010 Haifeng Li
 *   
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *  
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/

package smile.math.distance;

import smile.math.MathEx;

import java.util.function.ToDoubleBiFunction;

/**
 * Correlation distance is defined as 1 - correlation coefficient.
 *
 * @author Haifeng Li
 */
public class CorrelationDistance implements Distance<double[]> {
    private static final long serialVersionUID = 1L;

    /** A character string indicating what type of correlation is employed. */
    private String method;
    /** Correlation lambda. */
    private ToDoubleBiFunction<double[], double[]> cor;

    /**
     * Constructor of Pearson correlation distance.
     */
    public CorrelationDistance() {
        this("pearson");
    }

    /**
     * Constructor.
     */
    public CorrelationDistance(String method) {
        this.method = method.trim().toLowerCase();
        switch (this.method) {
            case "pearson":
                cor = (x, y) -> 1 - MathEx.cor(x, y);
                break;
            case "spearman":
                cor = (x, y) -> 1 - MathEx.spearman(x, y);
                break;
            case "kendall":
                cor = (x, y) -> 1 - MathEx.kendall(x, y);
                break;
            default:
                throw new IllegalArgumentException("Invalid correlation: " + method);
        }

    }

    @Override
    public String toString() {
        return String.format("Correlation Distance(%s)", method);
    }

    /**
     * Pearson correlation  distance between the two arrays of type double.
     */
    @Override
    public double d(double[] x, double[] y) {
        return cor.applyAsDouble(x, y);
    }
}
