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

package smile.stat.distribution;

/**
 * The logistic distribution is a continuous probability distribution whose
 * cumulative distribution function is the logistic function, which appears
 * in logistic regression and feedforward neural networks. It resembles
 * the normal distribution in shape but has heavier tails (higher kurtosis).
 * <p>
 * The cumulative distribution function of the logistic distribution is given by:
 * <pre>
 *                   1
 * F(x; &mu;,s) = -------------
 *              1 + e<sup>-(x-&mu;)/s</sup>
 * </pre>
 * The probability density function of the logistic distribution is given by:
 * <pre>
 *                  e<sup>-(x-&mu;)/s</sup>
 * f(x; &mu;,s) = -----------------
 *              s(1 + e<sup>-(x-&mu;)/s</sup>)<sup>2</sup>
 * </pre>
 * <p>
 * The logistic distribution and the S-shaped pattern that results from it
 * have been extensively used in many different areas such as:
 * <ul>
 * <li> Biology - to describe how species populations grow in competition.
 * <li> Epidemiology - to describe the spreading of epidemics.
 * <li> Psychology - to describe learning.
 * <li> Technology - to describe how new technologies diffuse and substitute
 * for each other.
 * <li> Market - the diffusion of new-product sales.
 * <li> Energy - the diffusion and substitution of primary energy sources.
 * </ul>
 * 
 * @author Haifeng Li
 */
public class LogisticDistribution extends AbstractDistribution {
    private static final long serialVersionUID = 2L;

    private static final double PI_SQRT3 = Math.PI / Math.sqrt(3);
    private static final double PI2_3 = Math.PI * Math.PI / 3;
    /** The location parameter. */
    public final double mu;
    /** The scale parameter. */
    public final double scale;

    /**
     * Constructor.
     */
    public LogisticDistribution(double mu, double scale) {
        if (scale <= 0.0) {
            throw new IllegalArgumentException("Invalid scale: " + scale);
        }

        this.mu = mu;
        this.scale = scale;
    }

    @Override
    public int length() {
        return 2;
    }

    @Override
    public double mean() {
        return mu;
    }

    @Override
    public double variance() {
        return PI2_3 * scale * scale;
    }

    @Override
    public double sd() {
        return PI_SQRT3 * scale;
    }

    @Override
    public double entropy() {
        return Math.log(scale) + 2;
    }

    @Override
    public String toString() {
        return String.format("Logistic Distribution(%.4f, %.4f)", mu, scale);
    }

    @Override
    public double rand() {
        return inverseTransformSampling();
    }

    @Override
    public double p(double x) {
        double e = Math.exp(-(x - mu) / scale);
        return e / (scale * (1.0 + e) * (1.0 + e));
    }

    @Override
    public double logp(double x) {
        return Math.log(p(x));
    }

    @Override
    public double cdf(double x) {
        double e = Math.exp(-(x - mu) / scale);
        return 1.0 / (1.0 + e);
    }

    @Override
    public double quantile(double p) {
        if (p < 0.0 || p > 1.0) {
            throw new IllegalArgumentException();
        }

        return mu + scale * Math.log(p / (1.0 - p));
    }
}
