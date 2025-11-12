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
package smile.validation;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import smile.math.MathEx;

/**
 * Regression model validation results.
 *
 * @param rounds The multiple round validations.
 * @param avg The average of metrics.
 * @param std The standard deviation of metrics.
 * @param <M> The regression model type.
 *
 * @author Haifeng Li
 */
public record RegressionValidations<M>(List<RegressionValidation<M>> rounds,
                                       RegressionMetrics avg,
                                       RegressionMetrics std) implements Serializable {
    @Serial
    private static final long serialVersionUID = 3L;

    /**
     * Factory method.
     * @param rounds the validation metrics of multiple rounds.
     * @param <M> the regression model type.
     * @return the validation object.
     */
    public static <M> RegressionValidations<M> of(List<RegressionValidation<M>> rounds) {
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
            RegressionMetrics metrics = rounds.get(i).metrics();
            fitTime[i] = metrics.fitTime();
            scoreTime[i] = metrics.scoreTime();
            size[i] = metrics.size();
            rss[i] = metrics.rss();
            mse[i] = metrics.mse();
            rmse[i] = metrics.rmse();
            mad[i] = metrics.mad();
            r2[i] = metrics.r2();
        }

        RegressionMetrics avg = new RegressionMetrics(
                MathEx.mean(fitTime),
                MathEx.mean(scoreTime),
                (int) Math.round(MathEx.mean(size)),
                MathEx.mean(rss),
                MathEx.mean(mse),
                MathEx.mean(rmse),
                MathEx.mean(mad),
                MathEx.mean(r2)
        );
        RegressionMetrics std = new RegressionMetrics(
                MathEx.stdev(fitTime),
                MathEx.stdev(scoreTime),
                (int) Math.round(MathEx.stdev(size)),
                MathEx.stdev(rss),
                MathEx.stdev(mse),
                MathEx.stdev(rmse),
                MathEx.stdev(mad),
                MathEx.stdev(r2)
        );
        return new RegressionValidations<>(rounds, avg, std);
    }

    @Override
    public String toString() {
        return "{\n" + String.format("  fit time: %.3f ms ± %.3f,\n", avg.fitTime(), std.fitTime()) +
                String.format("  score time: %.3f ms ± %.3f,\n", avg.scoreTime(), std.scoreTime()) +
                String.format("  validation data size: %d ± %d,\n", avg.size(), std.size()) +
                String.format("  RSS: %.4f ± %.4f,\n", avg.rss(), std.rss()) +
                String.format("  MSE: %.4f ± %.4f,\n", avg.mse(), std.mse()) +
                String.format("  RMSE: %.4f ± %.4f,\n", avg.rmse(), std.rmse()) +
                String.format("  MAD: %.4f ± %.4f,\n", avg.mad(), std.mad()) +
                String.format("  R2: %.2f%% ± %.2f\n}", 100 * avg.r2(), 100 * std.r2());
    }
}
