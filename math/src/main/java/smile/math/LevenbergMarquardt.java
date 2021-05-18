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

import java.util.Arrays;
import smile.math.matrix.Matrix;

/**
 * The Levenberg–Marquardt algorithm.
 * The Levenberg–Marquardt algorithm (LMA), also known as the Damped
 * least-squares method, is used to solve non-linear least squares
 * problems for generic curve-fitting problems. However,
 * as with many fitting algorithms, the LMA finds only a local minimum,
 * which is not necessarily the global minimum. The LMA interpolates
 * between the Gauss–Newton algorithm (GNA) and the method of gradient
 * descent. The LMA is more robust than the GNA, which means that in
 * many cases it finds a solution even if it starts very far off the
 * final minimum. For well-behaved functions and reasonable starting
 * parameters, the LMA tends to be a bit slower than the GNA. LMA can
 * also be viewed as Gauss–Newton using a trust region approach.
 *
 * @author Haifeng Li
 */
public class LevenbergMarquardt {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(LevenbergMarquardt.class);

    /** The fitted parameters. */
    public final double[] parameters;
    /** The fitted values. */
    public final double[] fittedValues;
    /** The residuals. */
    public final double[] residuals;
    /** The sum of squares due to error. */
    public final double sse;

    /**
     * Constructor.
     * @param parameters The fitted parameters.
     * @param fittedValues The fitted values.
     * @param residuals The residuals.
     * @param sse The sum of squares due to error.
     */
    LevenbergMarquardt(double[] parameters, double[] fittedValues, double[] residuals, double sse) {
        this.parameters = parameters;
        this.fittedValues = fittedValues;
        this.residuals = residuals;
        this.sse = sse;
    }

    /**
     * Fits the nonlinear least squares.
     *
     * @param func the curve function.
     *
     * @param x independent variable.
     * @param y the observations.
     * @param p the initial parameters.
     *
     * @return the sum of squared errors.
     */
    public static LevenbergMarquardt fit(DifferentiableMultivariateFunction func, double[] x, double[] y, double[] p) {
        return fit(func, x, y, p, 0.0001, 20);
    }

    /**
     * Fits the nonlinear least squares.
     *
     * @param func the curve function. Of the input variable x, the first d
     *             elements are hyper-parameters to be fit. The rest is the
     *             independent variable.
     * @param x independent variable.
     * @param y the observations.
     * @param p the initial parameters.
     * @param stol the scalar tolerances on fractional improvement in sum of squares
     * @param maxIter the maximum number of allowed iterations.
     *
     * @return the sum of squared errors.
     */
    public static LevenbergMarquardt fit(DifferentiableMultivariateFunction func, double[] x, double[] y, double[] p, double stol, int maxIter) {
        if (stol <= 0.0) {
            throw new IllegalArgumentException("Invalid gradient tolerance: " + stol);
        }

        if (maxIter <= 0) {
            throw new IllegalArgumentException("Invalid maximum number of iterations: " + maxIter);
        }

        // set up for iterations
        int n = x.length;
        int d = p.length;
        double[] pbest = new double[d+1];
        double[] pprev = new double[d+1];
        double[] pnew = new double[d+1];
        System.arraycopy(p, 0, pbest, 0, d);

        double[] f = new double[n];
        double[] r = new double[n];
        double[] g = new double[d];
        double[] gse = new double[d];
        double[] dp = new double[d];
        double[] chg = new double[d];

        double[] norm = new double[d];
        Arrays.fill(norm, 1.0);

        Matrix J = new Matrix(n, d);

        double epsLlast = 1;
        double[] epstab = {0.1, 1, 1e2, 1e4, 1e6};

        // iterations
        for (int iter = 1; iter <= maxIter; iter++) {
            System.arraycopy(pbest, 0, pprev, 0, d);

            double ss = 0.0;
            for (int i = 0; i < n; i++) {
                pprev[d] = x[i];
                double fi = func.g(pprev, dp);
                r[i] = y[i] - fi;
                ss += r[i] * r[i];

                for (int j = 0; j < d; j++) {
                    J.set(i, j, dp[j]);
                }
            }

            double sbest = ss;
            double sgoal = (1 - stol) * sbest;

            for (int j = 0; j < d; j++) {
                double nrm = 0.0;
                for (int i = 0; i < n; i++) {
                    double dpi = J.get(i, j);
                    nrm += dpi * dpi;
                }

                if (nrm > 0) {
                    norm[j] = 1 / Math.sqrt(nrm);
                } else {
                    norm[j] = 1.0;
                }

                for (int i = 0; i < n; i++) {
                    J.mul(i, j, norm[j]);
                }
            }

            Matrix.SVD svd = J.svd(true, true);
            double[] s = svd.s;
            double s2 = MathEx.dot(s, s);
            Matrix U = svd.U;
            Matrix V = svd.V;
            U.tv(r, g);

            for (double eps : epstab) {
                double epsL = Math.max(epsLlast * eps, 1e-7);
                double se = Math.sqrt(s2 + epsL);
                for (int j = 0; j < d; j++) {
                    gse[j] = g[j] / se;
                }

                V.mv(gse, chg);

                for (int j = 0; j < d; j++) {
                    chg[j] *= norm[j];
                }

                for (int i = 0; i < d; i++) {
                    pnew[i] = chg[i] + pprev[i];
                }

                ss = 0.0;
                for (int i = 0; i < n; i++) {
                    pnew[d] = x[i];
                    double fi = func.f(pnew);
                    double ri = y[i] - fi;
                    ss += ri * ri;
                }

                if (ss < sbest) {
                    System.arraycopy(pnew, 0, pbest, 0, d);
                    sbest = ss;
                }

                if (ss <= sgoal) {
                    epsLlast = epsL;
                    break;
                }
            }

            logger.info(String.format("SSE after %3d iterations: %.5f", iter, sbest));
            if (ss < MathEx.EPSILON || ss > sgoal) {
                logger.info(String.format("converges on SSE after %d iterations", iter));
                break;
            }
        }

        double[] pfit = new double[d];
        System.arraycopy(pbest, 0, pfit, 0, d);
        double ss = 0.0;
        for (int i = 0; i < n; i++) {
            pbest[d] = x[i];
            f[i] = func.f(pbest);
            r[i] = y[i] - f[i];
            ss += r[i] * r[i];
        }

        return new LevenbergMarquardt(pfit, f, r, ss);
    }

    /**
     * Fits the nonlinear least squares.
     *
     * @param func the curve function.
     *
     * @param x independent variables.
     * @param y the observations.
     * @param p the initial parameters.
     *
     * @return the sum of squared errors.
     */
    public static LevenbergMarquardt fit(DifferentiableMultivariateFunction func, double[][] x, double[] y, double[] p) {
        return fit(func, x, y, p, 0.0001, 20);
    }

    /**
     * Fits the nonlinear least squares.
     *
     * @param func the curve function. Of the input variable x, the first d
     *             elements are hyper-parameters to be fit. The rest is the
     *             independent variable.
     * @param x independent variables.
     * @param y the observations.
     * @param p the initial parameters.
     * @param stol the scalar tolerances on fractional improvement in sum of squares
     * @param maxIter the maximum number of allowed iterations.
     *
     * @return the sum of squared errors.
     */
    public static LevenbergMarquardt fit(DifferentiableMultivariateFunction func, double[][] x, double[] y, double[] p, double stol, int maxIter) {
        if (stol <= 0.0) {
            throw new IllegalArgumentException("Invalid gradient tolerance: " + stol);
        }

        if (maxIter <= 0) {
            throw new IllegalArgumentException("Invalid maximum number of iterations: " + maxIter);
        }

        // set up for iterations
        int n = x.length;
        int m = x[0].length;
        int d = p.length;
        double[] pbest = new double[d+m];
        double[] pprev = new double[d+m];
        double[] pnew = new double[d+m];
        System.arraycopy(p, 0, pbest, 0, d);

        double[] f = new double[n];
        double[] r = new double[n];
        double[] g = new double[d];
        double[] gse = new double[d];
        double[] dp = new double[d];
        double[] chg = new double[d];

        double[] norm = new double[d];
        Arrays.fill(norm, 1.0);

        Matrix J = new Matrix(n, d);

        double epsLlast = 1;
        double[] epstab = {0.1, 1, 1e2, 1e4, 1e6};

        // iterations
        for (int iter = 1; iter <= maxIter; iter++) {
            System.arraycopy(pbest, 0, pprev, 0, d);

            double ss = 0.0;
            for (int i = 0; i < n; i++) {
                System.arraycopy(x[i], 0, pprev, d, m);
                double fi = func.g(pprev, dp);
                r[i] = y[i] - fi;
                ss += r[i] * r[i];

                for (int j = 0; j < d; j++) {
                    J.set(i, j, dp[j]);
                }
            }

            double sbest = ss;
            double sgoal = (1 - stol) * sbest;

            for (int j = 0; j < d; j++) {
                double nrm = 0.0;
                for (int i = 0; i < n; i++) {
                    double dpi = J.get(i, j);
                    nrm += dpi * dpi;
                }

                if (nrm > 0) {
                    norm[j] = 1 / Math.sqrt(nrm);
                } else {
                    norm[j] = 1.0;
                }

                for (int i = 0; i < n; i++) {
                    J.mul(i, j, norm[j]);
                }
            }

            Matrix.SVD svd = J.svd(true, true);
            double[] s = svd.s;
            double s2 = MathEx.dot(s, s);
            Matrix U = svd.U;
            Matrix V = svd.V;
            U.tv(r, g);

            for (double eps : epstab) {
                double epsL = Math.max(epsLlast * eps, 1e-7);
                double se = Math.sqrt(s2 + epsL);
                for (int j = 0; j < d; j++) {
                    gse[j] = g[j] / se;
                }

                V.mv(gse, chg);

                for (int j = 0; j < d; j++) {
                    chg[j] *= norm[j];
                }

                for (int i = 0; i < d; i++) {
                    pnew[i] = chg[i] + pprev[i];
                }

                ss = 0.0;
                for (int i = 0; i < n; i++) {
                    System.arraycopy(x[i], 0, pnew, d, m);
                    double fi = func.f(pnew);
                    double ri = y[i] - fi;
                    ss += ri * ri;
                }

                if (ss < sbest) {
                    System.arraycopy(pnew, 0, pbest, 0, d);
                    sbest = ss;
                }

                if (ss <= sgoal) {
                    epsLlast = epsL;
                    break;
                }
            }

            logger.info(String.format("SSE after %3d iterations: %.5f", iter, sbest));
            if (ss < MathEx.EPSILON || ss > sgoal) {
                logger.info(String.format("converges on SSE after %d iterations", iter));
                break;
            }
        }

        double[] pfit = new double[d];
        System.arraycopy(pbest, 0, pfit, 0, d);
        double ss = 0.0;
        for (int i = 0; i < n; i++) {
            System.arraycopy(x[i], 0, pbest, d, m);
            f[i] = func.f(pbest);
            r[i] = y[i] - f[i];
            ss += r[i] * r[i];
        }

        return new LevenbergMarquardt(pfit, f, r, ss);
    }
}
