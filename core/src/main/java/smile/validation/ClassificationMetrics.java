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

/** The classification validation metrics. */
public class ClassificationMetrics implements Serializable {
    private static final long serialVersionUID = 2L;

    /** The time in milliseconds of fitting the model. */
    public final double fitTime;
    /** The time in milliseconds of scoring the validation data. */
    public final double scoreTime;
    /** The validation data size. */
    public final int size;
    /** The number of errors. */
    public final int error;
    /** The accuracy on validation data. */
    public final double accuracy;
    /** The sensitivity on validation data. */
    public final double sensitivity;
    /** The specificity on validation data. */
    public final double specificity;
    /** The precision on validation data. */
    public final double precision;
    /** The F-1 score on validation data. */
    public final double f1;
    /** The Matthews correlation coefficient on validation data. */
    public final double mcc;
    /** The AUC on validation data. */
    public final double auc;
    /** The log loss on validation data. */
    public final double logloss;
    /** The cross entropy on validation data. */
    public final double crossentropy;

    /** Constructor. */
    public ClassificationMetrics(double fitTime, double scoreTime, int size, int error, double accuracy) {
        this(fitTime, scoreTime, size, error, accuracy, Double.NaN);
    }

    /** Constructor of multiclass soft classifier validation. */
    public ClassificationMetrics(double fitTime, double scoreTime, int size, int error, double accuracy, double crossentropy) {
        this.fitTime = fitTime;
        this.scoreTime = scoreTime;
        this.size = size;
        this.error = error;
        this.accuracy = accuracy;
        this.crossentropy = crossentropy;
        this.sensitivity = Double.NaN;
        this.specificity = Double.NaN;
        this.precision = Double.NaN;
        this.f1 = Double.NaN;
        this.mcc = Double.NaN;
        this.auc = Double.NaN;
        this.logloss = Double.NaN;
    }

    /** Constructor of binary classifier validation. */
    public ClassificationMetrics(double fitTime, double scoreTime, int size, int error,
                                 double accuracy, double sensitivity, double specificity,
                                 double precision, double f1, double mcc) {
        this(fitTime, scoreTime, size, error, accuracy, sensitivity, specificity, precision, f1, mcc, Double.NaN, Double.NaN);
    }

    /** Constructor of binary soft classifier validation. */
    public ClassificationMetrics(double fitTime, double scoreTime, int size, int error,
                                 double accuracy, double sensitivity, double specificity,
                                 double precision, double f1, double mcc, double auc,
                                 double logloss) {
        this.fitTime = fitTime;
        this.scoreTime = scoreTime;
        this.size = size;
        this.error = error;
        this.accuracy = accuracy;
        this.sensitivity = sensitivity;
        this.specificity = specificity;
        this.precision = precision;
        this.f1 = f1;
        this.mcc = mcc;
        this.auc = auc;
        this.logloss = logloss;
        this.crossentropy = logloss;
    }

    /** Constructor. */
    public ClassificationMetrics(double fitTime, double scoreTime, int size, int error,
                                 double accuracy, double sensitivity, double specificity,
                                 double precision, double f1, double mcc, double auc,
                                 double logloss, double crossentropy) {
        this.fitTime = fitTime;
        this.scoreTime = scoreTime;
        this.size = size;
        this.error = error;
        this.accuracy = accuracy;
        this.sensitivity = sensitivity;
        this.specificity = specificity;
        this.precision = precision;
        this.f1 = f1;
        this.mcc = mcc;
        this.auc = auc;
        this.logloss = logloss;
        this.crossentropy = crossentropy;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("{\n");
        sb.append(String.format("  fit time: %.3f ms,\n", fitTime));
        sb.append(String.format("  score time: %.3f ms,\n", scoreTime));
        sb.append(String.format("  validation data size: %d,\n", size));
        sb.append(String.format("  error: %d,\n", error));
        sb.append(String.format("  accuracy: %.2f%%", 100 * accuracy));
        if (!Double.isNaN(sensitivity)) sb.append(String.format(",\n  sensitivity: %.2f%%", 100 * sensitivity));
        if (!Double.isNaN(specificity)) sb.append(String.format(",\n  specificity: %.2f%%", 100 * specificity));
        if (!Double.isNaN(precision)) sb.append(String.format(",\n  precision: %.2f%%", 100 * precision));
        if (!Double.isNaN(f1)) sb.append(String.format(",\n  F1 score: %.2f%%", 100 * f1));
        if (!Double.isNaN(mcc)) sb.append(String.format(",\n  MCC: %.2f%%", 100 * mcc));
        if (!Double.isNaN(auc)) sb.append(String.format(",\n  AUC: %.2f%%", 100 * auc));
        if (!Double.isNaN(logloss)) sb.append(String.format(",\n  log loss: %.4f", logloss));
        else if (!Double.isNaN(crossentropy)) sb.append(String.format(",\n  cross entropy: %.4f", crossentropy));
        sb.append("\n}");
        return sb.toString();
    }
}
