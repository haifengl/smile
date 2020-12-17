/*
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
 */

package smile.validation;

import java.io.Serializable;
import java.util.List;
import smile.math.MathEx;

/**
 * Regression model validation results.
 *
 * @param <M> the regression model type.
 *
 * @author Haifeng
 */
public class RegressionValidations<M> implements Serializable {
    private static final long serialVersionUID = 2L;

    /** The multiple round validations. */
    public final List<RegressionValidation<M>> rounds;

    /** The average of metrics. */
    public final RegressionMetrics avg;

    /** The standard deviation of metrics. */
    public final RegressionMetrics sd;

    /**
     * Constructor.
     * @param rounds the validation metrics of multipl rounds.
     */
    public RegressionValidations(List<RegressionValidation<M>> rounds) {
        this.rounds = rounds;

        int k = rounds.size();
        double[] fitTime = new double[k];
        double[] scoreTime = new double[k];
        int[] size = new int[k];
        double[] rss = new double[k];
        double[] mse = new double[k];
        double[] rmse = new double[k];
        double[] mad = new double[k];
        double[] r2 = new double[k];

        for (int i = 0; i < k; i++) {
            RegressionMetrics metrics = rounds.get(i).metrics;
            fitTime[i] = metrics.fitTime;
            scoreTime[i] = metrics.scoreTime;
            size[i] = metrics.size;
            rss[i] = metrics.rss;
            mse[i] = metrics.mse;
            rmse[i] = metrics.rmse;
            mad[i] = metrics.mad;
            r2[i] = metrics.r2;
        }

        avg = new RegressionMetrics(
                MathEx.mean(fitTime),
                MathEx.mean(scoreTime),
                (int) Math.round(MathEx.mean(size)),
                MathEx.mean(rss),
                MathEx.mean(mse),
                MathEx.mean(rmse),
                MathEx.mean(mad),
                MathEx.mean(r2)
        );
        sd = new RegressionMetrics(
                MathEx.sd(fitTime),
                MathEx.sd(scoreTime),
                (int) Math.round(MathEx.sd(size)),
                MathEx.sd(rss),
                MathEx.sd(mse),
                MathEx.sd(rmse),
                MathEx.sd(mad),
                MathEx.sd(r2)
        );
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("{\n");
        sb.append(String.format("  fit time: %.3f ms ± %.3f,\n", avg.fitTime, sd.fitTime));
        sb.append(String.format("  score time: %.3f ms ± %.3f,\n", avg.scoreTime, sd.scoreTime));
        sb.append(String.format("  validation data size:: %d ± %d,\n", avg.size, sd.size));
        sb.append(String.format("  RSS: %.4f ± %.4f,\n", avg.rss, sd.rss));
        sb.append(String.format("  MSE: %.4f ± %.4f,\n", avg.mse, sd.mse));
        sb.append(String.format("  RMSE: %.4f ± %.4f,\n", avg.rmse, sd.rmse));
        sb.append(String.format("  MAD: %.4f ± %.4f,\n", avg.mad, sd.mad));
        sb.append(String.format("  R2: %.2f%% ± %.2f\n}", 100 * avg.r2, 100 * sd.r2));
        return sb.toString();
    }
}
