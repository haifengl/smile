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

package smile.math;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static smile.util.Regex.BOOLEAN_REGEX;
import static smile.util.Regex.DOUBLE_REGEX;

/**
 * A time-dependent function. When training a neural network model,
 * it is often recommended to lower the learning rate as the training
 * progresses. Besides the learning rate schedule, it may also be used
 * for 1-dimensional neighborhood function, etc.
 *
 * @author Haifeng Li
 */
public interface TimeFunction extends Serializable {
    /**
     * Returns the function value at time step t.
     * @param t the discrete time step.
     * @return the function value.
     */
    double apply(int t);

    /**
     * Returns the constant learning rate.
     *
     * @param alpha the learning rate.
     * @return the constant learning rate function.
     */
    static TimeFunction constant(double alpha) {
        return new TimeFunction() {
            @Override
            public double apply(int t) {
                return alpha;
            }

            @Override
            public String toString() {
                return String.format("%f", alpha);
            }
        };
    }

    /**
     * Returns the piecewise constant learning rate. This can be useful for
     * changing the learning rate value across different invocations of
     * optimizer functions.
     *
     * @param boundaries A list of integers with strictly increasing entries.
     * @param values	 The values for the intervals defined by boundaries.
     *                   It should have one more element than boundaries.
     * @return the piecewise learning rate function.
     */
    static TimeFunction piecewise(int[] boundaries, double[] values) {
        if (values.length != boundaries.length + 1) {
            throw new IllegalArgumentException("values should have one more element than boundaries");
        }

        return new TimeFunction() {
            @Override
            public double apply(int t) {
                int i = Arrays.binarySearch(boundaries, t);
                if (i < 0) i = -i - 1;
                return values[i];
            }

            @Override
            public String toString() {
                return String.format("Piecewise(%s, %s)", Arrays.toString(boundaries), Arrays.toString(values));
            }
        };
    }

    /**
     * Returns the linear learning rate decay function that ends at 0.0001.
     *
     * @param initLearningRate the initial learning rate.
     * @param decaySteps the decay steps.
     * @return the linear learning rate function.
     */
    static TimeFunction linear(double initLearningRate, double decaySteps) {
        return linear(initLearningRate, decaySteps, 0.0001);
    }

    /**
     * Returns the linear learning rate decay function that starts with
     * an initial learning rate and reach an end learning rate in the given
     * decay steps..
     *
     * @param initLearningRate the initial learning rate.
     * @param decaySteps the decay steps.
     * @param endLearningRate the end learning rate.
     * @return the linear learning rate function.
     */
    static TimeFunction linear(double initLearningRate, double decaySteps, double endLearningRate) {
        return polynomial(1.0, initLearningRate, decaySteps, endLearningRate, false);
    }

    /**
     * Returns the polynomial learning rate decay function that starts with
     * an initial learning rate and reach an end learning rate in the given
     * decay steps, without cycling.
     *
     * It is commonly observed that a monotonically decreasing learning rate,
     * whose degree of change is carefully chosen, results in a better performing
     * model.
     *
     * @param degree the degree of the polynomial.
     * @param initLearningRate the initial learning rate.
     * @param decaySteps the decay steps.
     * @param endLearningRate the end learning rate.
     * @return the polynomial learning rate function.
     */
    static TimeFunction polynomial(double degree, double initLearningRate, double decaySteps, double endLearningRate) {
        return polynomial(degree, initLearningRate, decaySteps, endLearningRate, false);
    }

    /**
     * Returns the polynomial learning rate decay function that starts with
     * an initial learning rate and reach an end learning rate in the given
     * decay steps.
     *
     * It is commonly observed that a monotonically decreasing learning rate,
     * whose degree of change is carefully chosen, results in a better performing
     * model.
     *
     * @param degree the degree of the polynomial.
     * @param initLearningRate the initial learning rate.
     * @param decaySteps the decay steps.
     * @param endLearningRate the end learning rate.
     * @param cycle the flag whether or not it should cycle beyond decaySteps.
     * @return the polynomial learning rate function.
     */
    static TimeFunction polynomial(double degree, double initLearningRate, double decaySteps, double endLearningRate, boolean cycle) {
        return new TimeFunction() {
            @Override
            public double apply(int t) {
                if (cycle) {
                    double T = decaySteps * Math.max(1, Math.ceil(t / decaySteps));
                    return ((initLearningRate - endLearningRate) * Math.pow(1 - t / T, degree)) + endLearningRate;
                } else {
                    double steps = Math.min(t, decaySteps);
                    return ((initLearningRate - endLearningRate) * Math.pow(1 - steps / decaySteps, degree)) + endLearningRate;
                }
            }

            @Override
            public String toString() {
                if (degree == 1.0) {
                    return String.format("LinearDecay(%f, %.0f, %f)", initLearningRate, decaySteps, endLearningRate);
                } else {
                    return String.format("PolynomialDecay(%f, %f, %.0f, %f, %s)", degree, initLearningRate, decaySteps, endLearningRate, cycle);
                }
            }
        };
    }

    /**
     * Returns the inverse decay function.
     * {@code initLearningRate * decaySteps / (t + decaySteps)}.
     *
     * @param initLearningRate the initial learning rate.
     * @param decaySteps the decay steps that should be a small percentage
     *                   of the number of iterations.
     * @return the inverse decay function.
     */
    static TimeFunction inverse(double initLearningRate, double decaySteps) {
        return new TimeFunction() {
            @Override
            public double apply(int t) {
                return initLearningRate * decaySteps / (decaySteps + t);
            }

            @Override
            public String toString() {
                return String.format("InverseTimeDecay(%f, %.0f)", initLearningRate, decaySteps);
            }
        };
    }

    /**
     * Returns the inverse decay function.
     * {@code initLearningRate / (1 + decayRate * t / decaySteps)}.
     *
     * @param initLearningRate the initial learning rate.
     * @param decaySteps how often to apply decay.
     * @param decayRate the decay rate.
     * @return the inverse decay function.
     */
    static TimeFunction inverse(double initLearningRate, double decaySteps, double decayRate) {
        return inverse(initLearningRate, decaySteps, decayRate, false);
    }

    /**
     * Returns the inverse decay function.
     * {@code initLearningRate / (1 + decayRate * t / decaySteps)}.
     *
     * @param initLearningRate the initial learning rate.
     * @param decaySteps how often to apply decay.
     * @param decayRate the decay rate.
     * @param staircase the flag whether to apply decay in a discrete staircase,
     *                  as opposed to continuous, fashion.
     * @return the inverse decay function.
     */
    static TimeFunction inverse(double initLearningRate, double decaySteps, double decayRate, boolean staircase) {
        return new TimeFunction() {
            @Override
            public double apply(int t) {
                if (staircase) {
                    return initLearningRate / (1 + decayRate * Math.floor(t / decaySteps));
                } else {
                    return initLearningRate / (1 + decayRate * t / decaySteps);
                }
            }

            @Override
            public String toString() {
                return String.format("InverseTimeDecay(%f, %.0f, %f, %s)", initLearningRate, decaySteps, decayRate, staircase);
            }
        };
    }

    /**
     * Returns the exponential decay function.
     * {@code initLearningRate * exp(-t / decaySteps)}.
     *
     * @param initLearningRate the initial learning rate.
     * @param decaySteps the decay steps that should be a small percentage
     *                   of the number of iterations.
     * @return the exponential decay function.
     */
    static TimeFunction exp(double initLearningRate, double decaySteps) {
        return new TimeFunction() {
            @Override
            public double apply(int t) {
                return initLearningRate * Math.exp(-t / decaySteps);
            }

            @Override
            public String toString() {
                return String.format("ExponentialDecay(%f, %.0f)", initLearningRate, decaySteps);
            }
        };
    }

    /**
     * Returns the exponential decay function.
     * {@code initLearningRate * pow(endLearningRate / initLearningRate, min(t, decaySteps) / decaySteps)}.
     *
     * @param initLearningRate the initial learning rate.
     * @param decaySteps the maximum decay steps.
     * @param endLearningRate the end learning rate.
     * @return the exponential decay function.
     */
    static TimeFunction exp(double initLearningRate, double decaySteps, double endLearningRate) {
        double decayRate = endLearningRate / initLearningRate;
        return new TimeFunction() {
            @Override
            public double apply(int t) {
                return initLearningRate * Math.pow(decayRate, Math.min(t, decaySteps) / decaySteps);
            }

            @Override
            public String toString() {
                return String.format("ExponentialDecay(%f, %.0f, %f)", initLearningRate, decaySteps, endLearningRate);
            }
        };
    }

    /**
     * Returns the exponential decay function.
     * {@code initLearningRate * pow(decayRate, t / decaySteps)}.
     *
     * @param initLearningRate the initial learning rate.
     * @param decaySteps how often to apply decay.
     * @param decayRate the decay rate.
     * @param staircase the flag whether to apply decay in a discrete staircase,
     *                  as opposed to continuous, fashion.
     * @return the exponential decay function.
     */
    static TimeFunction exp(double initLearningRate, double decaySteps, double decayRate, boolean staircase) {
        return new TimeFunction() {
            @Override
            public double apply(int t) {
                if (staircase) {
                    return initLearningRate * Math.pow(decayRate, Math.floor(t / decaySteps));
                } else {
                    return initLearningRate * Math.pow(decayRate, t / decaySteps);
                }
            }

            @Override
            public String toString() {
                return String.format("ExponentialDecay(%f, %.0f, %f, %s)", initLearningRate, decaySteps, decayRate, staircase);
            }
        };
    }

    /**
     * Parses a time function.
     *
     * @param time the time function representation.
     * @return the time function.
     */
    static TimeFunction of(String time) {
        time = time.trim().toLowerCase(Locale.ROOT);

        Pattern linear = Pattern.compile(String.format("linear(?:decay)?\\((%s),\\s*(%s),\\s*(%s)\\)", DOUBLE_REGEX, DOUBLE_REGEX, DOUBLE_REGEX));
        Matcher m = linear.matcher(time);
        if (m.matches()) {
            double initLearningRate = Double.parseDouble(m.group(1));
            double decaySteps = Double.parseDouble(m.group(2));
            double endLearningRate = Double.parseDouble(m.group(3));
            return linear(initLearningRate, decaySteps, endLearningRate);
        }

        Pattern polynomial = Pattern.compile(String.format("polynomial(?:decay)?\\((%s),\\s*(%s),\\s*(%s),\\s*(%s)(,\\s*(%s))?\\)", DOUBLE_REGEX, DOUBLE_REGEX, DOUBLE_REGEX, DOUBLE_REGEX, BOOLEAN_REGEX));
        m = polynomial.matcher(time);
        if (m.matches()) {
            double degree = Double.parseDouble(m.group(1));
            double initLearningRate = Double.parseDouble(m.group(2));
            double decaySteps = Double.parseDouble(m.group(3));
            double endLearningRate = Double.parseDouble(m.group(4));
            boolean cycle = m.group(5) == null ? false : m.group(6).equals("true");
            return polynomial(degree, initLearningRate, decaySteps, endLearningRate, cycle);
        }

        if (time.startsWith("piecewise([") && time.endsWith("])")) {
            String[] tokens = time.substring(11, time.length()-2).split("\\],\\s*\\[");
            if (tokens.length == 2) {
                int[] boundaries = Arrays.stream(tokens[0].split(",\\s*")).mapToInt(Integer::parseInt).toArray();
                double[] values = Arrays.stream(tokens[1].split(",\\s*")).mapToDouble(Double::parseDouble).toArray();
                return piecewise(boundaries, values);
            }
        }

        Pattern inverse = Pattern.compile(String.format("inverse(?:timedecay)?\\((%s),\\s*(%s)(,\\s*(%s))?(,\\s*(%s))?\\)", DOUBLE_REGEX, DOUBLE_REGEX, DOUBLE_REGEX, BOOLEAN_REGEX));
        m = inverse.matcher(time);
        if (m.matches()) {
            double initLearningRate = Double.parseDouble(m.group(1));
            double decaySteps = Double.parseDouble(m.group(2));
            if (m.group(3) == null) {
                return inverse(initLearningRate, decaySteps);
            } else {
                double endLearningRate = Double.parseDouble(m.group(4));
                boolean staircase = m.group(5) == null ? false : m.group(6).equals("true");
                return inverse(initLearningRate, decaySteps, endLearningRate, staircase);
            }
        }

        Pattern exp = Pattern.compile(String.format("exp(?:onentialdecay)?\\((%s),\\s*(%s)(,\\s*(%s))?(,\\s*(%s))?\\)", DOUBLE_REGEX, DOUBLE_REGEX, DOUBLE_REGEX, BOOLEAN_REGEX));
        m = exp.matcher(time);
        if (m.matches()) {
            double initLearningRate = Double.parseDouble(m.group(1));
            double decaySteps = Double.parseDouble(m.group(2));
            if (m.group(3) == null) {
                return exp(initLearningRate, decaySteps);
            } else {
                double endLearningRate = Double.parseDouble(m.group(4));
                boolean staircase = m.group(5) == null ? false : m.group(6).equals("true");
                return exp(initLearningRate, decaySteps, endLearningRate, staircase);
            }
        }

        try {
            double alpha = Double.parseDouble(time);
            return constant(alpha);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Unknown time function: " + time);
        }
    }
}
