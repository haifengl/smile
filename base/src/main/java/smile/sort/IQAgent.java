/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.sort;

import java.io.Serial;
import java.io.Serializable;
import java.util.Arrays;

/**
 * Incremental quantile estimation. This class provide a robust and extremely
 * fast algorithm to estimate arbitrary quantile values from a continuing
 * stream of data values. Basically, the data values fly by in a stream.
 * We look at each value only once and do a constant-time process on it.
 * From time to time, we can use this class to report any arbitrary
 * p-quantile value of the data that we have seen thus far.
 *
 * <h2>References</h2>
 * <ol>
 * <li> John M. Chambers, David A. James, Diane Lambert and Scott Vander Wiel. Monitoring Networked Applications With Incremental Quantile Estimation. Statistical Science 21(4):463–475, 2006.</li>
 * </ol>
 *
 * @author Haifeng Li
 */
public class IQAgent implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /** The batch buffer size. */
    private final int nbuf;
    /** The number of quantile grid points. */
    private final int nq;
    /** The total number of data values processed prior to the current batch. */
    private int nt;
    /** The number of data values buffered in the current batch. */
    private int nd;
    /** The target quantile probabilities. */
    private final double[] pval;
    /** The data batch buffer. */
    private final double[] dbuf;
    /** The current quantile estimates corresponding to {@link #pval}. */
    private double[] qile;
    /** The minimum data value seen so far. */
    private double q0;
    /** The maximum data value seen so far. */
    private double qm;

    /**
     * Constructor. The batch size is set to 1000.
     */
    public IQAgent() {
        this(1000);
    }

    /**
     * Constructor.
     * @param nbuf batch size. You may use 10000 if you expected
     * {@code > 1,000,000} data values. Otherwise, 1000 should be fine.
     */
    public IQAgent(int nbuf) {
        if (nbuf <= 0) {
            throw new IllegalArgumentException("Invalid batch size: " + nbuf);
        }
        this.nbuf = nbuf;

        nq = 251;
        nt = 0;
        nd = 0;
        q0 = 1.e99;
        qm = -1.e99;

        pval = new double[nq];
        dbuf = new double[nbuf];
        qile = new double[nq];

        for (int j = 85; j <= 165; j++) {
            pval[j] = (j - 75.) / 100.;
        }

        for (int j = 84; j >= 0; j--) {
            pval[j] = 0.87191909 * pval[j + 1];
            pval[250 - j] = 1. - pval[j];
        }
    }

    /**
     * Assimilate a new value from the stream. NaN values are ignored.
     * @param datum a new value.
     */
    public void add(double datum) {
        if (Double.isNaN(datum)) {
            return;
        }
        dbuf[nd++] = datum;
        if (datum < q0) {
            q0 = datum;
        }
        if (datum > qm) {
            qm = datum;
        }
        if (nd == nbuf) {
            update();
        }
    }

    /**
     * Assimilates all values from an array into the stream.
     * @param data the values to add.
     */
    public void add(double[] data) {
        for (double datum : data) {
            add(datum);
        }
    }

    /**
     * Returns the total number of non-NaN observations assimilated so far.
     * @return the number of observations.
     */
    public int count() {
        return nt + nd;
    }

    /**
     * Returns true if no observations have been assimilated yet.
     * @return true if empty.
     */
    public boolean isEmpty() {
        return nt + nd == 0;
    }

    /**
     * Returns the minimum value seen so far, or {@code Double.NaN} if empty.
     * @return the minimum value.
     */
    public double min() {
        return isEmpty() ? Double.NaN : q0;
    }

    /**
     * Returns the maximum value seen so far, or {@code Double.NaN} if empty.
     * @return the maximum value.
     */
    public double max() {
        return isEmpty() ? Double.NaN : qm;
    }

    /**
     * Resets this sketch to its initial empty state without reallocating buffers.
     */
    public void reset() {
        nt = 0;
        nd = 0;
        q0 = 1.e99;
        qm = -1.e99;
        Arrays.fill(qile, 0.0);
        for (int j = 85; j <= 165; j++) {
            pval[j] = (j - 75.) / 100.;
        }
        for (int j = 84; j >= 0; j--) {
            pval[j] = 0.87191909 * pval[j + 1];
            pval[250 - j] = 1. - pval[j];
        }
    }

    /**
     * Returns a copy of the internal quantile marker values.
     * @return the quantile markers.
     */
    public double[] quantiles() {
        if (nd > 0) {
            update();
        }
        return qile.clone();
    }

    /**
     * Returns a copy of the internal probability marker values.
     * @return the probability markers.
     */
    public double[] probabilities() {
        if (nd > 0) {
            update();
        }
        return pval.clone();
    }

    /**
     * Batch update. This method is called by add(), quantile(), or cdf().
     */
    private void update() {
        int jd = 0, jq = 1, iq;
        double target, told = 0., tnew = 0., qold, qnew;
        double[] newqile = new double[nq];
        Arrays.sort(dbuf, 0, nd);
        qold = qnew = qile[0] = newqile[0] = q0;
        qile[nq - 1] = newqile[nq - 1] = qm;
        pval[0] = Math.min(0.5 / (nt + nd), 0.5 * pval[1]);
        pval[nq - 1] = Math.max(1.0 - 0.5 / (nt + nd), 0.5 * (1. + pval[nq - 2]));
        for (iq = 1; iq < nq - 1; iq++) {
            target = (nt + nd) * pval[iq];
            if (tnew < target) {
                for (;;) {
                    if (jq < nq && (jd >= nd || qile[jq] < dbuf[jd])) {
                        qnew = qile[jq];
                        tnew = jd + nt * pval[jq++];
                    } else {
                        qnew = dbuf[jd];
                        tnew = told;
                        if (qile[jq] > qile[jq - 1]) {
                            tnew += nt * (pval[jq] - pval[jq - 1]) * (qnew - qold) / (qile[jq] - qile[jq - 1]);
                        }
                        jd++;
                        if (tnew >= target) {
                            break;
                        }
                        told = tnew++;
                        qold = qnew;
                    }
                    if (tnew >= target) break;
                    told = tnew;
                    qold = qnew;
                }
            }
            if (tnew == told) {
                newqile[iq] = 0.5 * (qold + qnew);
            } else {
                newqile[iq] = qold + (qnew - qold) * (target - told) / (tnew - told);
            }
            told = tnew;
            qold = qnew;
        }
        qile = newqile;
        nt += nd;
        nd = 0;
    }

    /**
     * Returns the estimated p-quantile for the data seen so far. For example,
     * p = 0.5 for median.
     *
     * @param p the percentile in [0, 1].
     * @return the quantile, or {@code Double.NaN} if the stream is empty.
     */
    public double quantile(double p) {
        if (p < 0.0 || p > 1.0 || Double.isNaN(p)) {
            throw new IllegalArgumentException("Invalid p: " + p);
        }
        if (isEmpty()) {
            return Double.NaN;
        }
        if (nd > 0) {
            update();
        }
        int jl = 0, jh = nq - 1, j;
        while (jh - jl > 1) {
            j = (jh + jl) >> 1;
            if (p > pval[j]) {
                jl = j;
            } else {
                jh = j;
            }
        }
        j = jl;
        double q = qile[j] + (qile[j + 1] - qile[j]) * (p - pval[j]) / (pval[j + 1] - pval[j]);
        return Math.max(qile[0], Math.min(qile[nq - 1], q));
    }

    /**
     * Returns the estimated empirical cumulative distribution function P(X &le; x)
     * for the data seen so far.
     *
     * @param x the query value.
     * @return the estimated cumulative probability in [0, 1], or {@code Double.NaN} if empty.
     */
    public double cdf(double x) {
        if (Double.isNaN(x) || isEmpty()) {
            return Double.NaN;
        }
        if (nd > 0) {
            update();
        }
        if (x < qile[0]) {
            return 0.0;
        }
        if (x >= qile[nq - 1]) {
            return 1.0;
        }

        // Binary search for the largest jl in [0, nq - 2] such that qile[jl] <= x < qile[jl + 1].
        int jl = 0, jh = nq - 1;
        while (jh - jl > 1) {
            int j = (jh + jl) >> 1;
            if (x >= qile[j]) {
                jl = j;
            } else {
                jh = j;
            }
        }

        double denom = qile[jl + 1] - qile[jl];
        if (denom <= 0.0) {
            return pval[jl];
        }
        double p = pval[jl] + (pval[jl + 1] - pval[jl]) * (x - qile[jl]) / denom;
        return Math.max(0.0, Math.min(1.0, p));
    }

    /**
     * Merges another {@code IQAgent} sketch into this one in-place and returns {@code this}.
     *
     * @param other the other sketch to merge.
     * @return this sketch after merging.
     */
    public IQAgent merge(IQAgent other) {
        if (other == null || other.isEmpty()) {
            return this;
        }
        if (other == this) {
            throw new IllegalArgumentException("Cannot merge an IQAgent with itself");
        }
        if (this.nd > 0) {
            this.update();
        }
        if (other.nd > 0) {
            other.update();
        }
        if (this.isEmpty()) {
            this.nt = other.nt;
            this.q0 = other.q0;
            this.qm = other.qm;
            System.arraycopy(other.qile, 0, this.qile, 0, nq);
            System.arraycopy(other.pval, 0, this.pval, 0, nq);
            return this;
        }

        double n1 = this.nt;
        double n2 = other.nt;
        int total = this.nt + other.nt;

        double newMin = Math.min(this.q0, other.q0);
        double newMax = Math.max(this.qm, other.qm);

        double[] newPval = this.pval.clone();
        newPval[0] = Math.min(0.5 / total, 0.5 * newPval[1]);
        newPval[nq - 1] = Math.max(1.0 - 0.5 / total, 0.5 * (1.0 + newPval[nq - 2]));

        double[] newqile = new double[nq];
        newqile[0] = newMin;
        newqile[nq - 1] = newMax;

        int i1 = 0, i2 = 0;
        double qold = newMin, qnew = newMin;
        double told = 0.0, tnew = 0.0;

        for (int iq = 1; iq < nq - 1; iq++) {
            double target = total * newPval[iq];
            while (tnew < target && (i1 < nq || i2 < nq)) {
                told = tnew;
                qold = qnew;
                if (i1 < nq && (i2 >= nq || this.qile[i1] <= other.qile[i2])) {
                    qnew = this.qile[i1];
                    double f1 = this.pval[i1++];
                    double f2 = other.cdf(qnew);
                    tnew = Math.max(told, n1 * f1 + n2 * f2);
                } else {
                    qnew = other.qile[i2];
                    double f2 = other.pval[i2++];
                    double f1 = this.cdf(qnew);
                    tnew = Math.max(told, n1 * f1 + n2 * f2);
                }
            }
            if (tnew == told) {
                newqile[iq] = 0.5 * (qold + qnew);
            } else {
                newqile[iq] = qold + (qnew - qold) * (target - told) / (tnew - told);
            }
        }

        this.q0 = newMin;
        this.qm = newMax;
        this.qile = newqile;
        System.arraycopy(newPval, 0, this.pval, 0, nq);
        this.nt = total;
        return this;
    }
}
