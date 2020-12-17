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
 * Classification model validation results.
 *
 * @param <M> the model type.
 *
 * @author Haifeng
 */
public class ClassificationValidations<M> implements Serializable {
    private static final long serialVersionUID = 2L;

    /** The multiple round validations. */
    public final List<ClassificationValidation<M>> rounds;

    /** The average of metrics. */
    public final ClassificationMetrics avg;

    /** The standard deviation of metrics. */
    public final ClassificationMetrics sd;

    /**
     * Constructor.
     * @param rounds the validation metrics of multipl rounds.
     */
    public ClassificationValidations(List<ClassificationValidation<M>> rounds) {
        this.rounds = rounds;

        int k = rounds.size();
        double[] fitTime = new double[k];
        double[] scoreTime = new double[k];
        int[] size = new int[k];
        int[] error = new int[k];
        double[] accuracy = new double[k];
        double[] sensitivity = new double[k];
        double[] specificity = new double[k];
        double[] precision = new double[k];
        double[] f1 = new double[k];
        double[] mcc = new double[k];
        double[] auc = new double[k];
        double[] logloss = new double[k];
        double[] crossentropy = new double[k];

        for (int i = 0; i < k; i++) {
            ClassificationMetrics metrics = rounds.get(i).metrics;
            fitTime[i] = metrics.fitTime;
            scoreTime[i] = metrics.scoreTime;
            size[i] = metrics.size;
            error[i] = metrics.error;
            accuracy[i] = metrics.accuracy;
            sensitivity[i] = metrics.sensitivity;
            specificity[i] = metrics.specificity;
            precision[i] = metrics.precision;
            f1[i] = metrics.f1;
            mcc[i] = metrics.mcc;
            auc[i] = metrics.auc;
            logloss[i] = metrics.logloss;
            crossentropy[i] = metrics.crossentropy;
        }

        avg = new ClassificationMetrics(
                MathEx.mean(fitTime),
                MathEx.mean(scoreTime),
                (int) Math.round(MathEx.mean(size)),
                (int) Math.round(MathEx.mean(error)),
                MathEx.mean(accuracy),
                MathEx.mean(sensitivity),
                MathEx.mean(specificity),
                MathEx.mean(precision),
                MathEx.mean(f1),
                MathEx.mean(mcc),
                MathEx.mean(auc),
                MathEx.mean(logloss),
                MathEx.mean(crossentropy)
        );
        sd = new ClassificationMetrics(
                MathEx.sd(fitTime),
                MathEx.sd(scoreTime),
                (int) Math.round(MathEx.sd(size)),
                (int) Math.round(MathEx.sd(error)),
                MathEx.sd(accuracy),
                MathEx.sd(sensitivity),
                MathEx.sd(specificity),
                MathEx.sd(precision),
                MathEx.sd(f1),
                MathEx.sd(mcc),
                MathEx.sd(auc),
                MathEx.sd(logloss),
                MathEx.sd(crossentropy)
        );
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("{\n");
        sb.append(String.format("  fit time: %.3f ms ± %.3f,\n", avg.fitTime, sd.fitTime));
        sb.append(String.format("  score time: %.3f ms ± %.3f,\n", avg.scoreTime, sd.scoreTime));
        sb.append(String.format("  validation data size: %d ± %d,\n", avg.size, sd.size));
        sb.append(String.format("  error: %d ± %d,\n", avg.error, sd.error));
        sb.append(String.format("  accuracy: %.2f%% ± %.2f", 100 * avg.accuracy, 100 * sd.accuracy));
        if (!Double.isNaN(avg.sensitivity)) sb.append(String.format(",\n  sensitivity: %.2f%% ± %.2f", 100 * avg.sensitivity, 100 * sd.sensitivity));
        if (!Double.isNaN(avg.specificity)) sb.append(String.format(",\n  specificity: %.2f%% ± %.2f", 100 * avg.specificity, 100 * sd.specificity));
        if (!Double.isNaN(avg.precision)) sb.append(String.format(",\n  precision: %.2f%% ± %.2f", 100 * avg.precision, 100 * sd.precision));
        if (!Double.isNaN(avg.f1)) sb.append(String.format(",\n  F1 score: %.2f%% ± %.2f", 100 * avg.f1, 100 * sd.f1));
        if (!Double.isNaN(avg.mcc)) sb.append(String.format(",\n  MCC: %.2f%% ± %.2f", 100 * avg.mcc, 100 * sd.mcc));
        if (!Double.isNaN(avg.auc)) sb.append(String.format(",\n  AUC: %.2f%% ± %.2f", 100 * avg.auc, 100 * sd.auc));
        if (!Double.isNaN(avg.logloss)) sb.append(String.format(",\n  log loss: %.4f ± %.4f", avg.logloss, sd.logloss));
        else if (!Double.isNaN(avg.crossentropy)) sb.append(String.format(",\n  cross entropy: %.4f ± %.4f", avg.crossentropy, sd.crossentropy));
        sb.append("\n}");
        return sb.toString();
    }
}
