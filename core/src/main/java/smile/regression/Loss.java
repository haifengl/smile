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

package smile.regression;

/**
 * Regression loss function.
 */
public interface Loss {
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

    /** Returns the loss type. */
    Type type();

    /** Returns the optional parameter of loss function. */
    default double[] parameters() {
        throw new UnsupportedOperationException();
    }

    /** Abstract loss. */
    abstract class AbstractLoss implements Loss {
        @Override
        public String toString() {
            return type().toString();
        }
    }

    /**
     * Constructs the loss for least squares regression.
     */
    static Loss ls() {
        return new AbstractLoss() {
            @Override
            public Type type() {
                return Type.LeastSquares;
            }
        };
    }

    /**
     * Constructs the loss for quantile regression.
     * @param q the percentile.
     */
    static Loss quantile(double q) {
        return new Loss() {
            double[] parameters = {q};

            @Override
            public Type type() {
                return Type.Quantile;
            }

            @Override
            public double[] parameters() {
                return parameters;
            }

            @Override
            public String toString() {
                return String.format("Quantile(%.2f)", q);
            }
        };
    }

    /**
     * Constructs the loss for least absolute deviation regression.
     */
    static Loss lad() {
        return new AbstractLoss() {
            @Override
            public Type type() {
                return Type.LeastAbsoluteDeviation;
            }
        };
    }

    /**
     * Constructs the loss for Huber regression.
     */
    static Loss huber() {
        return new AbstractLoss() {
            @Override
            public Type type() {
                return Type.Huber;
            }
        };
    }

    /** Parses the loss. */
    static Loss valueOf(String s) {
        switch (s) {
            case "LeastSquares": return ls();
            case "LeastAbsoluteDeviation": return lad();
            case "Huber": return huber();
        }

        if (s.startsWith("Quantile(") && s.endsWith(")")) {
            double q = Double.parseDouble(s.substring(9, s.length()-1));
            return quantile(q);
        }

        throw new IllegalArgumentException("Invalid loss: " + s);
    }
}
