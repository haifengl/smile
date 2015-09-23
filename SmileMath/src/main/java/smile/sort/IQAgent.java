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

package smile.sort;

import java.util.Arrays;

/**
 * This class provide a robust and extremely fast algorithm to estimate arbitary
 * quantile values from a continuing stream of data values. Basically, the data
 * values fly by in a stream. We look at each value only once and do a
 * constant-time process on it. From time to time, we can use this class to
 * report any arbitary p-quantile value of the data that we have seen thus far.
 * 
 * @author Haifeng Li
 */
public class IQAgent {

    private int nbuf;
    private int nq,  nt,  nd;
    private double[] pval;
    private double[] dbuf;
    private double[] qile;
    private double q0,  qm;

    /**
     * Constructor. The batch size is set to 1000.
     */
    public IQAgent() {
        this(1000);
    }

    /**
     * Constructor.
     * @param nbuf batch size. You may use 10000 if you expected
     * &gt; 10<sup>6</sup> data values. Otherwise, 1000 should be fine.
     */
    public IQAgent(int nbuf) {
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
     * Assimilate a new value from the stream.
     */
    public void add(double datum) {
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
     * Batch update. This method is called by add() or quantile().
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
                        if (tnew >= target) {
                            break;
                        }
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
                        if (tnew >= target) {
                            break;
                        }
                    }
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
     */
    public double quantile(double p) {
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
}
