/*******************************************************************************
 * Copyright (c) 2010-2019 Haifeng Li
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
 *******************************************************************************/

package smile.base.cart;

import smile.math.MathEx;
import smile.sort.QuickSelect;

import java.util.Arrays;

/**
 * Regression loss function.
 */
public interface Loss {
    /**
     * Calculate the node output.
     *
     * @param nodeSamples the index of node samples to their original locations in training dataset.
     * @param sampleCount samples[i] is the number of sampling of dataset[i]. 0 means that the
     *               datum is not included and values of greater than 1 are
     *               possible because of sampling with replacement.
     * @return the node output
     */
    double output(int[] nodeSamples, int[] sampleCount);

    /**
     * Returns the intercept of model.
     * @param y the response variable.
     */
    double intercept(double[] y);

    /**
     * Returns the response variable for next iteration.
     */
    double[] response();

    /**
     * Returns the residual vector.
     */
    double[] residual();

    /** The type of loss. */
    enum Type {
        /**
         * Least squares regression. Least-squares is highly efficient for
         * normally distributed errors but is prone to long tails and outliers.
         */
        LeastSquares,
        /**
         * Quantile regression. The gradient tree boosting based
         * on this loss function is highly robust. The trees use only order
         * information on the input variables and the pseudo-response has only
         * two values {-1, +1}. The line searches (terminal node values) use
         * only specified quantile ratio.
         */
        Quantile,
        /**
         * Least absolute deviation regression. The gradient tree boosting based
         * on this loss function is highly robust. The trees use only order
         * information on the input variables and the pseudo-response has only
         * two values {-1, +1}. The line searches (terminal node values) use
         * only medians. This is a special case of quantile regression of q = 0.5.
         */
        LeastAbsoluteDeviation,
        /**
         * Huber loss function for M-regression, which attempts resistance to
         * long-tailed error distributions and outliers while maintaining high
         * efficency for normally distributed errors.
         */
        Huber
    }

    /**
     * Least squares regression. Least-squares is highly efficient for
     * normally distributed errors but is prone to long tails and outliers.
     */
    static Loss ls() {
        return new Loss() {
            /** The residual/response variable. */
            double[] residual;

            @Override
            public double output(int[] nodeSamples, int[] sampleCount) {
                int n = 0;
                double output = 0.0;
                for (int i : nodeSamples) {
                    n += sampleCount[i];
                    output += residual[i] * sampleCount[i];
                }

                return output / n;
            }

            @Override
            public double intercept(double[] y) {
                int n = y.length;
                residual = new double[n];

                double b = MathEx.mean(y);

                for (int i = 0; i < n; i++) {
                    residual[i] = y[i] - b;
                }
                return b;
            }

            @Override
            public double[] response() {
                return residual;
            }

            @Override
            public double[] residual() {
                return residual;
            }

            @Override
            public String toString() {
                return "LeastSquares";
            }
        };
    }

    /**
     * Least squares regression. Least-squares is highly efficient for
     * normally distributed errors but is prone to long tails and outliers.
     */
    static Loss ls(double[] y) {
        return new Loss() {
            /** The residual/response variable. */
            double[] residual = y;

            @Override
            public double output(int[] nodeSamples, int[] sampleCount) {
                int n = 0;
                double output = 0.0;
                for (int i : nodeSamples) {
                    n += sampleCount[i];
                    output += residual[i] * sampleCount[i];
                }

                return output / n;
            }

            @Override
            public double intercept(double[] y) {
                throw new IllegalStateException("This method should not be called.");
            }

            @Override
            public double[] response() {
                return residual;
            }

            @Override
            public double[] residual() {
                throw new IllegalStateException("This method should not be called.");
            }

            @Override
            public String toString() {
                return "LeastSquares";
            }
        };
    }

    /**
     * Quantile regression. The gradient tree boosting based
     * on this loss function is highly robust. The trees use only order
     * information on the input variables and the pseudo-response has only
     * two values {-1, +1}. The line searches (terminal node values) use
     * only specified quantile ratio.
     *
     * @param p the percentile.
     */
    static Loss quantile(double p) {
        if (p <= 0.0 || p >= 1.0) {
            throw new IllegalArgumentException("Invalid percentile: " + p);
        }

        return new Loss() {
            /** The response variable. */
            double[] response;
            /** The residuals. */
            double[] residual;

            @Override
            public double output(int[] nodeSamples, int[] sampleCount) {
                double[] r = Arrays.stream(nodeSamples).mapToDouble(i -> residual[i]).toArray();
                return QuickSelect.select(r, (int) (r.length * p));
            }

            @Override
            public double intercept(double[] y) {
                int n = y.length;
                response = new double[n];
                residual = new double[n];
                System.arraycopy(y, 0, response, 0, n);

                double b = QuickSelect.select(response, (int) (n * p));

                for (int i = 0; i < n; i++) {
                    residual[i] = y[i] - b;
                }
                return b;
            }

            @Override
            public double[] response() {
                for (int i = 0; i < residual.length; i++) {
                    response[i] = Math.signum(residual[i]);
                }
                return response;
            }

            @Override
            public double[] residual() {
                return residual;
            }

            @Override
            public String toString() {
                return String.format("Quantile(%3.1f%%)", 100*p);
            }
        };
    }

    /**
     * Least absolute deviation regression. The gradient tree boosting based
     * on this loss function is highly robust. The trees use only order
     * information on the input variables and the pseudo-response has only
     * two values {-1, +1}. The line searches (terminal node values) use
     * only medians. This is a special case of quantile regression of q = 0.5.
     */
    static Loss lad() {
        return new Loss() {
            /** The response variable. */
            double[] response;
            /** The residuals. */
            double[] residual;

            @Override
            public double output(int[] nodeSamples, int[] sampleCount) {
                double[] r = Arrays.stream(nodeSamples).mapToDouble(i -> residual[i]).toArray();
                return QuickSelect.median(r);
            }

            @Override
            public double intercept(double[] y) {
                int n = y.length;
                response = new double[n];
                residual = new double[n];
                System.arraycopy(y, 0, response, 0, n);

                double b = QuickSelect.median(response);

                for (int i = 0; i < n; i++) {
                    residual[i] = y[i] - b;
                }
                return b;
            }

            @Override
            public double[] response() {
                for (int i = 0; i < residual.length; i++) {
                    response[i] = Math.signum(residual[i]);
                }
                return response;
            }

            @Override
            public double[] residual() {
                return residual;
            }

            @Override
            public String toString() {
                return "LeastAbsoluteDeviation";
            }
        };
    }

    /**
     * Huber loss function for M-regression, which attempts resistance to
     * long-tailed error distributions and outliers while maintaining high
     * efficiency for normally distributed errors.
     * @param p of residuals
     */
    static Loss huber(double p) {
        if (p <= 0.0 || p >= 1.0) {
            throw new IllegalArgumentException("Invalid percentile: " + p);
        }

        return new Loss() {
            /** The response variable. */
            double[] response;
            /** The residuals. */
            double[] residual;
            /** The cutoff. */
            private double delta;

            @Override
            public double output(int[] nodeSamples, int[] sampleCount) {
                double r = QuickSelect.median(Arrays.stream(nodeSamples).mapToDouble(i -> residual[i]).toArray());
                double output = 0.0;
                for (int i : nodeSamples) {
                    double d = residual[i] - r;
                    output += Math.signum(d) * Math.min(delta, Math.abs(d));
                }

                output = r + output / nodeSamples.length;
                return output;
            }

            @Override
            public double intercept(double[] y) {
                int n = y.length;
                response = new double[n];
                residual = new double[n];
                System.arraycopy(y, 0, response, 0, n);

                double b = QuickSelect.median(response);

                for (int i = 0; i < n; i++) {
                    residual[i] = y[i] - b;
                }
                return b;
            }

            @Override
            public double[] response() {
                int n = residual.length;
                for (int i = 0; i < n; i++) {
                    response[i] = Math.abs(residual[i]);
                }

                delta = QuickSelect.select(response, (int) (n * p));

                for (int i = 0; i < n; i++) {
                    if (Math.abs(residual[i]) <= delta) {
                        response[i] = residual[i];
                    } else {
                        response[i] = delta * Math.signum(residual[i]);
                    }
                }

                return response;
            }

            @Override
            public double[] residual() {
                return residual;
            }

            @Override
            public String toString() {
                return String.format("Huber(%3.1f%%)", 100*p);
            }
        };
    }

    /**
     * Logistic regression loss for binary classification.
     * @param labels the class labels.
     */
    static Loss logistic(int[] labels) {
        int n = labels.length;

        return new Loss() {
            /** The class labels of +1 and -1. */
            int[] y = Arrays.stream(labels).map(yi -> 2 * yi - 1).toArray();
            /** The response variable. */
            double[] response = new double[n];
            /** The residuals. */
            double[] residual = new double[n];

            @Override
            public double output(int[] nodeSamples, int[] sampleCount) {
                double nu = 0.0;
                double de = 0.0;
                for (int i : nodeSamples) {
                    double abs = Math.abs(response[i]);
                    nu += response[i];
                    de += abs * (2.0 - abs);
                }

                return nu / de;
            }

            @Override
            public double intercept(double[] $y) {
                double mu = MathEx.mean(y);
                double b = 0.5 * Math.log((1 + mu) / (1 - mu));
                Arrays.fill(residual, b);
                return b;
            }

            @Override
            public double[] response() {
                for (int i = 0; i < n; i++) {
                    response[i] = 2.0 * y[i] / (1 + Math.exp(2 * y[i] * residual[i]));
                }
                return response;
            }

            @Override
            public double[] residual() {
                return residual;
            }

            @Override
            public String toString() {
                return "Logistic";
            }
        };
    }

    /**
     * Logistic regression loss for multi-class classification.
     * @param c the class id that this loss function fits on.
     * @param k the number of classes.
     * @param labels the class labels.
     * @param p the posteriori probabilities.
     */
    static Loss logistic(int c, int k, int[] labels, double[][] p) {
        int n = labels.length;

        return new Loss() {
            /** The class labels of binary case. */
            int[] y = Arrays.stream(labels).map(yi -> yi == c ? 1 : 0).toArray();
            /** The response variable. */
            double[] response = new double[n];
            /** The residuals. */
            double[] residual = new double[n];

            @Override
            public double output(int[] nodeSamples, int[] sampleCount) {
                double nu = 0.0;
                double de = 0.0;
                for (int i : nodeSamples) {
                    double abs = Math.abs(response[i]);
                    nu += response[i];
                    de += abs * (1.0 - abs);
                }

                if (de < 1E-10) {
                    return nu / nodeSamples.length;
                }

                return ((k-1.0) / k) * (nu / de);
            }

            @Override
            public double intercept(double[] $y) {
                throw new IllegalStateException("This method should not be called.");
            }

            @Override
            public double[] response() {
                for (int i = 0; i < n; i++) {
                    response[i] = y[i] - p[i][c];
                }
                return response;
            }

            @Override
            public double[] residual() {
                return residual;
            }

            @Override
            public String toString() {
                return String.format("Logistic(%d)", k);
            }
        };
    }

    /** Parses the loss. */
    static Loss valueOf(String s) {
        switch (s) {
            case "LeastSquares": return ls();
            case "LeastAbsoluteDeviation": return lad();
        }

        if (s.startsWith("Quantile(") && s.endsWith(")")) {
            double p = Double.parseDouble(s.substring(9, s.length()-1));
            return quantile(p);
        }

        if (s.startsWith("Huber(") && s.endsWith(")")) {
            double p = Double.parseDouble(s.substring(6, s.length()-1));
            return huber(p);
        }

        throw new IllegalArgumentException("Unsupported loss: " + s);
    }
}
