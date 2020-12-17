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
                return String.format("Constant(%f)", alpha);
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
                return String.format("PiecewiseConstant(%s, %s)", Arrays.toString(boundaries), Arrays.toString(values));
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
        return polynomial(initLearningRate, decaySteps, endLearningRate, false, 1.0);
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
     * @param initLearningRate the initial learning rate.
     * @param decaySteps the decay steps.
     * @param endLearningRate the end learning rate.
     * @param cycle the flag whether or not it should cycle beyond decaySteps.
     * @param power the power of the polynomial.
     * @return the polynomial learning rate function.
     */
    static TimeFunction polynomial(double initLearningRate, double decaySteps, double endLearningRate, boolean cycle, double power) {
        return new TimeFunction() {
            @Override
            public double apply(int t) {
                if (cycle) {
                    double T = decaySteps * Math.max(1, Math.ceil(t / decaySteps));
                    return ((initLearningRate - endLearningRate) * Math.pow(1 - t / T, power)) + endLearningRate;
                } else {
                    double steps = Math.min(t, decaySteps);
                    return ((initLearningRate - endLearningRate) * Math.pow(1 - steps / decaySteps, power)) + endLearningRate;
                }
            }

            @Override
            public String toString() {
                return String.format("PolynomialDecay(initial learning rate = %f, decay steps = %.0f, end learning rate = %f, cycle = %s, power = %f)", initLearningRate, decaySteps, endLearningRate, cycle, power);
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
                return String.format("InverseTimeDecay(initial learning rate = %f, decaySteps = %.0f)", initLearningRate, decaySteps);
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
                return String.format("InverseTimeDecay(initial learning rate = %f, decay steps = %.0f, decay rate = %f, staircase = %s)", initLearningRate, decaySteps, decayRate, staircase);
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
                return String.format("ExponentialDecay(initial learning rate = %f, decay steps = %.0f)", initLearningRate, decaySteps);
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
                return String.format("ExponentialDecay(initial learning rate = %f, decay steps = %.0f, end learning rate = %f)", initLearningRate, decaySteps, endLearningRate);
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
                return String.format("ExponentialDecay(initial learning rate = %f, decay steps = %.0f, decay rate = %f, staircase = %s)", initLearningRate, decaySteps, decayRate, staircase);
            }
        };
    }
}
