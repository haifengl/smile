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
package smile.feature.selection;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.IntStream;

import smile.classification.ClassLabels;
import smile.data.DataFrame;
import smile.data.measure.NominalScale;
import smile.data.transform.ColumnTransform;
import smile.data.type.StructField;
import smile.data.type.StructType;
import smile.data.vector.ValueVector;
import smile.sort.QuickSort;
import smile.util.function.Function;

/**
 * Information Value (IV) measures the predictive strength of a feature
 * for a binary dependent variable. IV is essentially a weighted
 * sum of all the individual Weight of Evidence (WoE) values, where the
 * weights incorporate the absolute difference between the numerator and
 * the denominator (WoE captures the relative difference). Note that the
 * weight follows the same sign as WoE hence ensuring that the IV is always
 * a positive number.
 * <p>
 * IV is a good measure of the predictive power of a feature. It also helps
 * point out the suspicious feature. Unlike other feature selection methods
 * available, the features selected using IV might not be the best feature
 * set for a non-linear model building.
 *
 * <table>
 *     <caption>Interpretation of Information Value</caption>
 *     <tr><th>Information Value</th><th>Predictive power</th></tr>
 *     <tr><td>&lt;0.02</td><td>Useless</td></tr>
 *     <tr><td>0.02 to 0.1</td><td>Weak predictors</td></tr>
 *     <tr><td>0.1 to 0.3</td><td>Medium Predictors</td></tr>
 *     <tr><td>0.3 to 0.5</td><td>Strong predictors</td></tr>
 *     <tr><td>&gt;0.5</td><td>Suspicious</td></tr>
 * </table>
 *
 * Weight of Evidence (WoE) measures the predictive power of every
 * bin/category of a feature for a binary dependent variable.
 * WoE is calculated as
 * <pre>
 * WoE = ln (percentage of events / percentage of non-events).
 * </pre>
 * Note that the conditional log odds is exactly what a logistic
 * regression model tries to predict.
 * <p>
 * WoE values of a categorical variable can be used to convert
 * a categorical feature to a numerical feature. If a continuous
 * feature does not have a linear relationship with the log odds,
 * the feature can be binned into groups and a new feature created
 * by replaced each bin with its WoE value. Therefore, WoE is a good
 * variable transformation method for logistic regression.
 * <p>
 * On arranging a numerical feature in ascending order, if the WoE
 * values are all linear, we know that the feature has the right
 * linear relation with the target. However, if the feature's WoE
 * is non-linear, we should either discard it or consider some other
 * variable transformation to ensure the linearity. Hence, WoE helps
 * check the linear relationship of a feature with its dependent variable
 * to be used in the model. Though WoE and IV are highly useful,
 * always ensure that it is only used with logistic regression.
 * <p>
 * WoE is better than on-hot encoding as it does not increase the
 * complexity of the model.
 *
 * @param feature The feature name.
 * @param iv The information value.
 * @param woe The weight of evidence.
 * @param breaks The breakpoints of intervals for numerical variables.
 * @author Haifeng Li
 */
public record InformationValue(String feature, double iv, double[] woe, double[] breaks) implements Comparable<InformationValue> {
    @Override
    public int compareTo(InformationValue other) {
        return Double.compare(iv, other.iv);
    }

    @Override
    public String toString() {
        return String.format("InformationValue(%s, %.4f)", feature, iv);
    }

    /**
     * Returns the code of predictive power of information value.
     * @param iv information value
     * @return the code of predictive power
     */
    private static String predictivePower(double iv) {
        if (Double.isNaN(iv)) return "";
        if (iv < 0.02) return "Not useful";
        else if (iv <= 0.1) return "Weak";
        else if (iv <= 0.3) return "Medium";
        else if (iv <= 0.5) return "Strong";
        else return "Suspicious";
    }

    /**
     * Returns a string representation of the array of information values.
     * @param ivs the array of information values.
     * @return a string representation of information values
     */
    public static String toString(InformationValue[] ivs) {
        StringBuilder sb = new StringBuilder();

        sb.append("Feature                   Information Value    Predictive Power\n");
        for (var iv : ivs) {
            sb.append(String.format("%-25s %17.4f    %16s%n", iv.feature, iv.iv, predictivePower(iv.iv)));
        }

        return sb.toString();
    }

    /**
     * Returns the data transformation that covert feature value to its weight of evidence.
     * @param values the information value objects of features.
     * @return the transform.
     */
    public static ColumnTransform toTransform(InformationValue[] values) {
        Map<String, Function> transforms = new HashMap<>();
        for (InformationValue iv : values) {
            Function transform = new Function() {
                @Override
                public double f(double x) {
                    int i;
                    if (iv.breaks == null) {
                        i = (int) x;
                        if (i < 0 || i >= iv.woe.length) {
                            throw new IllegalArgumentException("Invalid nominal value: " + i);
                        }
                        return iv.woe[i];
                    } else {
                        i = Arrays.binarySearch(iv.breaks, x);
                        if (i < 0) i = -i - 1;
                    }
                    return iv.woe[i];
                }

                @Override
                public String toString() {
                    return iv.feature + "_WoE";
                }
            };

            transforms.put(iv.feature, transform);
        }

        return new ColumnTransform("WoE", transforms);
    }

    /**
     * Calculates the information value.
     *
     * @param data the data frame of the explanatory and response variables.
     * @param clazz the column name of binary class labels.
     * @return the information value.
     */
    public static InformationValue[] fit(DataFrame data, String clazz) {
        return fit(data, clazz, 10);
    }

    /**
     * Calculates the information value.
     *
     * @param data the data frame of the explanatory and response variables.
     * @param clazz the column name of binary class labels.
     * @param nbins the number of bins to discretize numeric variables in WOE calculation.
     * @return the information value.
     */
    public static InformationValue[] fit(DataFrame data, String clazz, int nbins) {
        if (nbins < 2) {
            throw new IllegalArgumentException("Invalid number of bins: " + nbins);
        }

        ValueVector y = data.column(clazz);
        ClassLabels codec = ClassLabels.fit(y);

        if (codec.k != 2) {
            throw new UnsupportedOperationException("Information Value is applicable only to binary classification");
        }

        int n = data.size();
        StructType schema = data.schema();

        return IntStream.range(0, schema.length()).mapToObj(i -> {
            int[] events;
            int[] nonevents;
            double[] breaks = null;

            StructField field = schema.field(i);
            if (field.measure() instanceof NominalScale scale) {
                int k = scale.size();
                events = new int[k];
                nonevents = new int[k];

                int[] xi = data.column(i).toIntArray();
                for (int j = 0; j < n; j++) {
                    if (codec.y[j] == 1) {
                        events[xi[j]]++;
                    } else {
                        nonevents[xi[j]]++;
                    }
                }
            } else if (field.isNumeric()) {
                events = new int[nbins];
                nonevents = new int[nbins];
                breaks = new double[nbins - 1];

                double[] xi = data.column(i).toDoubleArray();
                int[] order = QuickSort.sort(xi);

                int begin = 0;
                for (int j = 0; j < nbins; j++) {
                    int end = (j + 1) * n / nbins;
                    if (j < nbins - 1) breaks[j] = xi[end];

                    for (int k = begin; k < end; k++) {
                        if (codec.y[order[k]] == 1) {
                            events[j]++;
                        } else {
                            nonevents[j]++;
                        }
                    }
                    begin = end;
                }
            } else {
                return null;
            }

            int k = events.length;
            double[] woe = new double[k];
            double iv = 0.0;
            for (int j = 0; j < k; j++) {
                double pnonevents = Math.max(nonevents[j], 0.5) / codec.ni[0];
                double pevents = Math.max(events[j], 0.5) / codec.ni[1];
                woe[j] = Math.log(pnonevents / pevents);
                iv += (pnonevents - pevents) * woe[j];
            }

            return new InformationValue(field.name(), iv, woe, breaks);
        }).filter(iv -> iv != null && !iv.feature.equals(clazz)).toArray(InformationValue[]::new);
    }
}
