/*******************************************************************************
 * Copyright (c) 2010-2020 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 ******************************************************************************/

package smile.validation;

import java.io.Serializable;

/** The regression validation metrics. */
public class RegressionMetrics implements Serializable {
    private static final long serialVersionUID = 2L;

    /** The time in milliseconds of fitting the model. */
    public final double fitTime;
    /** The time in milliseconds of scoring the validation data. */
    public final double scoreTime;
    /** The residual sum of squares. on validation data. */
    public final double rss;
    /** The mean squared error on validation data. */
    public final double mse;
    /** The root mean squared error  on validation data. */
    public final double rmse;
    /** The mean absolute deviation on validation data. */
    public final double mad;
    /** The R-squared score on validation data. */
    public final double r2;

    /** Constructor. */
    public RegressionMetrics(double fitTime, double scoreTime, double rss, double mse, double rmse, double mad, double r2) {
        this.fitTime = fitTime;
        this.scoreTime = scoreTime;
        this.rss = rss;
        this.mse = mse;
        this.rmse = rmse;
        this.mad = mad;
        this.r2 = r2;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("{\n");
        sb.append(String.format("  fit time: %.3f ms,\n", fitTime));
        sb.append(String.format("  score time: %.3f ms,\n", scoreTime));
        sb.append(String.format("  RSS: %.4f,\n", rss));
        sb.append(String.format("  MSE: %.4f,\n", mse));
        sb.append(String.format("  RMSE: %.4f,\n", rmse));
        sb.append(String.format("  MAD: %.4f,\n", mad));
        sb.append(String.format("  R2: %.2f%%\n}", 100 * r2));
        return sb.toString();
    }
}
