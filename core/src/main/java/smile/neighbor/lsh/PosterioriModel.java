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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.PriorityQueue;
import smile.stat.distribution.GaussianDistribution;
import smile.util.IntArrayList;

/**
 * Pre-computed posteriori probabilities for generating multiple probes.
 */
public class PosterioriModel implements Serializable {
    private static final long serialVersionUID = 2L;

    /**
     * The hash function to model.
     */
    private MultiProbeHash hash;
    /**
     * The posteriori probabilities lookup table.
     */
    private PrH[][][] lookup;

    /**
     * Constructor.
     * @param hash the hash function.
     * @param samples the training samples.
     * @param Nz the size of lookup table.
     * @param sigma the Parzen window width.
     */
    public PosterioriModel(MultiProbeHash hash, MultiProbeSample[] samples, int Nz, double sigma) {
        this.hash = hash;
        int k = hash.k;

        HashValueParzenModel parzen = new HashValueParzenModel(hash, samples, sigma);
        lookup = new PrH[k][][];

        // for each component u
        for (int m = 0; m < k; m++) {
            int minh = (int) Math.floor(hash.umin[m]);
            int maxh = (int) Math.floor(hash.umax[m]); // min & max inclusive
            int size = Math.min(maxh - minh + 1, Nz);
            double delta = (maxh - minh) / (double) size;

            lookup[m] = new PrH[size][];

            // for each quantum of u(q)
            for (int n = 0; n < size; n++) {
                parzen.estimate(m, minh + (n + 0.5) * delta);
                GaussianDistribution gaussian = new GaussianDistribution(parzen.mean(), parzen.sd());

                // This is the original method. However, a lots of h values
                // will have very small probability and are essentially not useful.
                    /*
                    lookup[m][n] = new PrH[size];
                    for (int h = 0; h < size; ++h) {
                        int u = h + minh;
                        lookup[m][n][h] = new PrH();
                        lookup[m][n][h].u = u;
                        lookup[m][n][h].pr = gaussian.cdf(u + 1) - gaussian.cdf(u);
                    }
                     */

                // Here we only generate those h values with reasonably large probability
                ArrayList<PrH> probs = new ArrayList<>();
                int h0 = (int) Math.floor(parzen.mean());
                for (int h = h0;; h++) {
                    PrH prh = new PrH(h, gaussian.cdf(h + 1) - gaussian.cdf(h));
                    if (prh.pr < 1E-7) {
                        break;
                    }
                    probs.add(prh);
                }

                for (int h = h0 - 1;; h--) {
                    PrH prh = new PrH(h, gaussian.cdf(h + 1) - gaussian.cdf(h));
                    if (prh.pr < 1E-7) {
                        break;
                    }
                    probs.add(prh);
                }

                lookup[m][n] = probs.toArray(new PrH[probs.size()]);
                Arrays.sort(lookup[m][n]);
            }
        }
    }

    /**
     * Generate query-directed probes.
     * @param x the query object.
     * @param recall the threshold of global probability of probes as a
     * quality control parameter.
     * @param T the maximum number of probes.
     * @return the list of probe buckets.
     */
    public IntArrayList getProbeSequence(double[] x, double recall, int T) {
        int k = hash.k;
        PrZ[] pz = new PrZ[k];

        for (int i = 0; i < k; i++) {
            double h = hash.hash(x, i);

            double hmin = h - hash.umin[i];
            if (hmin < 0.0) {
                hmin = 0.0;
            }

            if (h > hash.umax[i]) {
                hmin = hash.umax[i] - hash.umin[i];
            }

            int qh = (int) (hmin * lookup[i].length / (hash.umax[i] - hash.umin[i] + 1));

            pz[i] = new PrZ(i, lookup[i][qh]);
        }

        Arrays.sort(pz);

        // generate probe sequence
        IntArrayList seq = new IntArrayList();
        seq.add(hash.hash(x));

        int[] range = new int[k];
        for (int i = 0; i < k; i++) {
            range[i] = pz[i].prh.length;
        }

        PriorityQueue<Probe> heap = new PriorityQueue<>();
        heap.add(new Probe(range));

        heap.peek().setProb(pz);
        double pr = heap.peek().prob;
        seq.add(heap.peek().hash(hash, pz));

        heap.peek().bucket[0] = 0;
        heap.peek().last = 0;
        heap.peek().setProb(pz);

        while (!heap.isEmpty() && pr < recall && seq.size() < T) {
            Probe p = heap.poll();

            seq.add(p.hash(hash, pz));
            pr += p.prob;

            if (p.isShiftable()) {
                Probe p2 = p.shift();
                p2.setProb(pz);
                heap.offer(p2);
            }

            if (p.isExpandable()) {
                Probe p2 = p.expand();
                p2.setProb(pz);
                heap.offer(p2);
            }

            if (p.isExtendable()) {
                Probe p2 = p.extend();
                p2.setProb(pz);
                heap.offer(p2);
            }
        }

        return seq;
    }
}
