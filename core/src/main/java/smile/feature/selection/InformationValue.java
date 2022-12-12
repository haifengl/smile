package smile.feature.selection;

import smile.classification.ClassLabels;
import smile.data.DataFrame;
import smile.data.formula.Formula;
import smile.data.measure.NominalScale;
import smile.data.type.StructField;
import smile.data.type.StructType;
import smile.data.vector.BaseVector;
import smile.sort.QuickSort;

/**
 * Information Value (IV) measures the predictive strength of a feature
 * for a binary dependent variable. IV is essentially a weighted
 * sum of all the individual WoE values, where the weights incorporate the
 * absolute difference between the numerator and the denominator (WoE captures
 * the relative difference).Note that the weight follows the same sign as WoE
 * hence ensuring that the IV is always a positive number.
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
 * @see smile.feature.extraction.WoE
 *
 * @author Haifeng Li
 */
public class InformationValue {
    /** The predictors. */
    private final String[] predictors;
    /** Information value. */
    private final double[] iv;
    /** Weight of evidence. */
    private final double[][] woe;
    /** Boundary of intervals for numerical variables. */
    private final double[][] bins;

    /**
     * Constructor.
     * @param predictors The predictors.
     * @param iv Information value.
     * @param woe Weight of evidence.
     * @param bins Boundary of intervals for numerical variables.
     */
    public InformationValue(String[] predictors, double[] iv, double[][] woe, double[][] bins) {
        this.predictors = predictors;
        this.iv = iv;
        this.woe = woe;
        this.bins = bins;
    }

    /** Returns the information value. */
    public double[] iv() {
        return iv;
    }

    /** Returns the weight of evidence. */
    public double[][] woe() {
        return woe;
    }

    private String significance(double iv) {
        if (Double.isNaN(iv)) return "";
        if (iv < 0.02) return "Not useful";
        else if (iv <= 0.1) return "Weak";
        else if (iv <= 0.3) return "Medium";
        else if (iv <= 0.5) return "Strong";
        else return "Suspicious";
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();

        builder.append("                          Information Value    Predictive Power\n");
        for (int i = 0; i < iv.length; i++) {
            builder.append(String.format("%-25s %17.4f    %16s%n", predictors[i], iv[i], significance(iv[i])));
        }

        return builder.toString();
    }

    /**
     * Calculates the information value.
     *
     * @param formula a symbolic description of the model to be fitted.
     * @param data the data frame of the explanatory and response variables.
     * @return the information value.
     */
    public static InformationValue fit(Formula formula, DataFrame data) {
        return fit(formula, data, 10);
    }

    /**
     * Calculates the information value.
     *
     * @param formula a symbolic description of the model to be fitted.
     * @param data the data frame of the explanatory and response variables.
     * @param nbins the number of bins to discretize numeric variables in WOE calculation.
     * @return the information value.
     */
    public static InformationValue fit(Formula formula, DataFrame data, int nbins) {
        if (nbins < 2) {
            throw new IllegalArgumentException("Invalid number of bins: " + nbins);
        }

        formula = formula.expand(data.schema());
        BaseVector<?, ?, ?> y = formula.y(data);
        ClassLabels codec = ClassLabels.fit(y);

        if (codec.k != 2) {
            throw new UnsupportedOperationException("Information Value is applicable only to binary classification");
        }

        DataFrame x = formula.x(data);
        StructType schema = x.schema();
        int n = x.nrow();
        int p = schema.length();
        double[] iv = new double[p];
        double[][] woe = new double[p][];
        double[][] bins = new double[p][];

        for (int i = 0; i < p; i++) {
            int[] events;
            int[] nonevents;

            StructField field = schema.field(i);
            if (field.measure instanceof NominalScale) {
                int k = ((NominalScale) field.measure).size();
                woe[i] = new double[k];

                int[] xi = x.column(i).toIntArray();
                events = new int[k];
                nonevents = new int[k];

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
                bins[i] = new double[nbins - 1];

                double[] xi = x.column(i).toDoubleArray();
                int[] order = QuickSort.sort(xi);

                int begin = 0;
                for (int j = 0; j < nbins; j++) {
                    int end = (j + 1) * n / nbins;
                    if (j < nbins - 1) bins[i][j] = xi[end];

                    for (int k = begin; k < end; k++) {
                        if (codec.y[order[k]] == 1) {
                            events[j]++;
                        } else {
                            nonevents[j]++;
                        }
                    }
                    begin = end;
                }
            } else{
                events = null;
                nonevents = null;
            }

            if (events != null) {
                int k = events.length;
                woe[i] = new double[k];
                for (int j = 0; j < k; j++) {
                    double pnonevents = Math.max(nonevents[j], 0.5) / codec.ni[0];
                    double pevents = Math.max(events[j], 0.5) / codec.ni[1];
                    woe[i][j] = Math.log(pnonevents / pevents);
                    iv[i] += (pnonevents - pevents) * woe[i][j];
                }
            }
        }

        return new InformationValue(x.schema().names(), iv, woe, bins);
    }
}
