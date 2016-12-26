/*******************************************************************************
 * Copyright (c) 2010 Haifeng Li
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/

package smile.classification;

import java.io.Serializable;
import static java.lang.Math.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Platt scaling or Platt calibration is a way of transforming the outputs
 * of a classification model into a probability distribution over classes.
 * The method was invented by John Platt in the context of support vector
 * machines, but can be applied to other classification models.
 * Platt scaling works by fitting a logistic regression model to
 * a classifier's scores.
 *
 * Platt suggested using the Levenberg–Marquardt algorithm to optimize
 * the parameters, but a Newton algorithm was later proposed that should
 * be more numerically stable, which is implemented in this class.
 *
 * @author Haifeng Li
 */
public class PlattScaling implements Serializable {
    private static final long serialVersionUID = 1L;

    /** The scalar parameters to be learned by the algorithm. */
    private double alpha;
    private double beta;
    private static final Logger logger = LoggerFactory.getLogger(PlattScaling.class);

    /**
     * Trains the Platt scaling.
     * @param scores The predicted scores.
     * @param y The training labels.
     */
    public PlattScaling(double[] scores, int[] y) {
        this(scores, y, 100);
    }

    /**
     * Trains the Platt scaling.
     * @param scores The predicted scores.
     * @param y The training labels.
     * @param maxIters The maximal number of iterations.
     */
    public PlattScaling(double[] scores, int[] y, int maxIters) {
        int l = scores.length;
        double prior1 = 0, prior0 = 0;
        int i;

        for (i = 0; i < l; i++) {
            if (y[i] > 0) prior1 += 1;
            else prior0 += 1;
        }

        double min_step = 1e-10;    // Minimal step taken in line search
        double sigma = 1e-12;    // For numerically strict PD of Hessian
        double eps = 1e-5;
        double hiTarget = (prior1 + 1.0) / (prior1 + 2.0);
        double loTarget = 1 / (prior0 + 2.0);
        double[] t = new double[l];

        // Initial Point and Initial Fun Value
        alpha = 0.0;
        beta = Math.log((prior0 + 1.0) / (prior1 + 1.0));
        double fval = 0.0;

        for (i = 0; i < l; i++) {
            if (y[i] > 0) t[i] = hiTarget;
            else t[i] = loTarget;

            double fApB = scores[i] * alpha + beta;

            if (fApB >= 0)
                fval += t[i] * fApB + log(1 + exp(-fApB));
            else
                fval += (t[i] - 1) * fApB + log(1 + exp(fApB));
        }

        int iter = 0;
        for (; iter < maxIters; iter++) {
            // Update Gradient and Hessian (use H' = H + sigma I)
            double h11 = sigma; // numerically ensures strict PD
            double h22 = sigma;
            double h21 = 0.0;
            double g1 = 0.0;
            double g2 = 0.0;
            for (i = 0; i < l; i++) {
                double fApB = scores[i] * alpha + beta;
                double p, q;
                if (fApB >= 0) {
                    p = exp(-fApB) / (1.0 + exp(-fApB));
                    q = 1.0 / (1.0 + exp(-fApB));
                } else {
                    p = 1.0 / (1.0 + exp(fApB));
                    q = exp(fApB) / (1.0 + exp(fApB));
                }
                double d2 = p * q;
                h11 += scores[i] * scores[i] * d2;
                h22 += d2;
                h21 += scores[i] * d2;
                double d1 = t[i] - p;
                g1 += scores[i] * d1;
                g2 += d1;
            }

            // Stopping Criteria
            if (abs(g1) < eps && abs(g2) < eps)
                break;

            // Finding Newton direction: -inv(H') * g
            double det = h11 * h22 - h21 * h21;
            double dA = -(h22 * g1 - h21 * g2) / det;
            double dB = -(-h21 * g1 + h11 * g2) / det;
            double gd = g1 * dA + g2 * dB;


            double stepsize = 1;        // Line Search
            while (stepsize >= min_step) {
                double newA = alpha + stepsize * dA;
                double newB = beta + stepsize * dB;

                // New function value
                double newf = 0.0;
                for (i = 0; i < l; i++) {
                    double fApB = scores[i] * newA + newB;
                    if (fApB >= 0)
                        newf += t[i] * fApB + log(1 + exp(-fApB));
                    else
                        newf += (t[i] - 1) * fApB + log(1 + exp(fApB));
                }
                // Check sufficient decrease
                if (newf < fval + 0.0001 * stepsize * gd) {
                    alpha = newA;
                    beta = newB;
                    fval = newf;
                    break;
                } else
                    stepsize = stepsize / 2.0;
            }

            if (stepsize < min_step) {
                logger.error("Line search fails.");
                break;
            }
        }

        if (iter >= maxIters) {
            logger.warn("Reaches maximal iterations");
        }
    }

    /**
     * Returns the posterior probability estimate P(y = 1 | x).
     *
     * @param y the binary classifier output score.
     * @return the estimated probability.
     */
    public double predict(double y) {
        double fApB = y * alpha + beta;

        if (fApB >= 0)
            return exp(-fApB) / (1.0 + exp(-fApB));
        else
            return 1.0 / (1 + exp(fApB));
    }

    /**
     * Estimates the multiclass probabilies.
     */
    public static void multiclass(int k, double[][] r, double[] p) {
        double[][] Q = new double[k][k];
        double[] Qp = new double[k];
        double pQp, eps = 0.005 / k;

        for (int t = 0; t < k; t++) {
            p[t] = 1.0 / k;  // Valid if k = 1
            Q[t][t] = 0;
            for (int j = 0; j < t; j++) {
                Q[t][t] += r[j][t] * r[j][t];
                Q[t][j] = Q[j][t];
            }
            for (int j = t + 1; j < k; j++) {
                Q[t][t] += r[j][t] * r[j][t];
                Q[t][j] = -r[j][t] * r[t][j];
            }
        }

        int iter = 0;
        int maxIter = max(100, k);
        for (; iter < maxIter; iter++) {
            // stopping condition, recalculate QP,pQP for numerical accuracy
            pQp = 0;
            for (int t = 0; t < k; t++) {
                Qp[t] = 0;
                for (int j = 0; j < k; j++)
                    Qp[t] += Q[t][j] * p[j];
                pQp += p[t] * Qp[t];
            }
            double max_error = 0;
            for (int t = 0; t < k; t++) {
                double error = abs(Qp[t] - pQp);
                if (error > max_error)
                    max_error = error;
            }
            if (max_error < eps) break;

            for (int t = 0; t < k; t++) {
                double diff = (-Qp[t] + pQp) / Q[t][t];
                p[t] += diff;
                pQp = (pQp + diff * (diff * Q[t][t] + 2 * Qp[t])) / (1 + diff) / (1 + diff);
                for (int j = 0; j < k; j++) {
                    Qp[j] = (Qp[j] + diff * Q[t][j]) / (1 + diff);
                    p[j] /= (1 + diff);
                }
            }
        }

        if (iter >= maxIter) {
            logger.warn("Reaches maximal iterations");
        }
    }
}
