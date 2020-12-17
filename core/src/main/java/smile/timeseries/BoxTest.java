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

package smile.timeseries;

import smile.math.special.Gamma;

/**
 * Portmanteau test jointly that several autocorrelations of time series
 * are zero.
 *
 * @author Haifeng Li
 */
public class BoxTest {
    /** The type of test. */
    public enum Type {
        /** Box-Pierce test. */
        Box_Pierce {
            @Override
            public String toString() {
                return "Box-Pierce";
            }
        },

        /** Ljung-Box test. */
        Ljung_Box {
            @Override
            public String toString() {
                return "Ljung-Box";
            }
        }
    }

    /**
     * The type of test.
     */
    public final Type type;

    /**
     * The degree of freedom.
     */
    public final int df;

    /**
     * Box-Pierce or Ljung-Box statistic.
     */
    public final double q;

    /**
     * p-value
     */
    public final double pvalue;

    /**
     * Constructor.
     *
     * @param type the type of test.
     * @param q Box-Pierce or Ljung-Box statistic.
     * @param df the degree of freedom.
     * @param pvalue p-value.
     */
    private BoxTest(Type type, double q, int df, double pvalue) {
        this.type = type;
        this.q = q;
        this.df = df;
        this.pvalue = pvalue;
    }

    @Override
    public String toString() {
        return String.format("%s test(q = %.4f, df = %d, p-value = %G)", type, q, df, pvalue);
    }

    /**
     * Box-Pierce test.
     *
     * @param x time series
     * @param lag the statistic will be based on lag autocorrelation coefficients.
     * @return the test results.
     */
    public static BoxTest pierce(double[] x, int lag) {
        double q = 0.0;
        for (int l = 1; l <= lag; l++) {
            double r = TimeSeries.acf(x, l);
            q += r * r;
        }

        q *= x.length;
        double p = Gamma.regularizedUpperIncompleteGamma(0.5 * lag, 0.5 * q);

        return new BoxTest(Type.Box_Pierce, q, lag, p);
    }

    /**
     * Box-Pierce test.
     *
     * @param x time series
     * @param lag the statistic will be based on lag autocorrelation coefficients.
     * @return the test results.
     */
    public static BoxTest ljung(double[] x, int lag) {
        int n = x.length;
        double q = 0.0;
        for (int l = 1; l <= lag; l++) {
            double r = TimeSeries.acf(x, l);
            q += r * r / (n - l);
        }

        q *= n * (n + 2);
        double p = Gamma.regularizedUpperIncompleteGamma(0.5 * lag, 0.5 * q);

        return new BoxTest(Type.Ljung_Box, q, lag, p);
    }
}
