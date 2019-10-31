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

package smile.neighbor.lsh;

import smile.stat.distribution.GaussianDistribution;

public class HashValueParzenModel {

    /**
     * Gaussian kernel for Parzen window estimation.
     */
    private GaussianDistribution gaussian;
    /**
     * Query object's neighbor hash values model.
     */
    private NeighborHashValueModel[] neighborHashValueModels;
    /**
     * Estimated conditional mean.
     */
    private double mean;
    /**
     * Estimated conditional standard deviation.
     */
    private double sd;

    /**
     * Constructor.
     */
    public HashValueParzenModel(MultiProbeHash hash, MultiProbeSample[] samples, double sigma) {
        int k = hash.k;
        gaussian = new GaussianDistribution(0, sigma);

        int n = 0;
        for (int i = 0; i < samples.length; i++) {
            if (samples[i].neighbors.size() > 1) {
                n++;
            }
        }

        neighborHashValueModels = new NeighborHashValueModel[n];
        int l = 0;
        for (MultiProbeSample sample : samples) {
            if (sample.neighbors.size() > 1) {
                double[] H = new double[k];
                double[] mu = new double[k];
                double[] var = new double[k];

                for (int i = 0; i < k; i++) {
                    H[i] = hash.hash(sample.query, i);

                    double sum = 0.0;
                    double sumsq = 0.0;
                    for (double[] v : sample.neighbors) {
                        double h = hash.hash(v, i);
                        sum += h;
                        sumsq += h * h;
                    }

                    mu[i] = sum / sample.neighbors.size();
                    var[i] = sumsq / sample.neighbors.size() - mu[i] * mu[i];
                }

                neighborHashValueModels[l++] = new NeighborHashValueModel(H, mu, var);
            }
        }
    }

    /**
     * Given a hash value h, estimate the Gaussian model (mean and variance)
     * of neighbors existing in the corresponding bucket.
     * @param m the index of hash function.
     * @param h the given hash value.
     */
    public void estimate(int m, double h) {
        double mm = 0.0, vv = 0.0, ss = 0.0;
        for (int i = 0; i < neighborHashValueModels.length; i++) {
            double k = gaussian.p(neighborHashValueModels[i].H[m] - h);
            mm += k * neighborHashValueModels[i].mean[m];
            vv += k * neighborHashValueModels[i].var[m];
            ss += k;
        }

        if (ss > 1E-7) {
            mean = mm / ss;
            sd = Math.sqrt(vv / ss);
        } else {
            mean = h;
            sd = 0.0;
        }

        if (sd < 1E-5) {
            sd = 0.0;
            for (int i = 0; i < neighborHashValueModels.length; i++) {
                sd += neighborHashValueModels[i].var[m];
            }
            sd = Math.sqrt(sd / neighborHashValueModels.length);
        }
    }

    /** Returns the mean. */
    public double mean() {
        return mean;
    }

    /** Returns the standard deviation. */
    public double sd() {
        return sd;
    }
}
