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

/**
 * Probe to check for nearest neighbors.
 */
public class Probe implements Comparable<Probe> {

    /**
     * The valid range of buckets.
     */
    private int[] range;
    /**
     * The bucket for probing.
     */
    int[] bucket;
    /**
     * The last non-zero component.
     */
    int last;
    /**
     * The probability of this probe.
     */
    double prob;

    /**
     * Constructor.
     */
    public Probe(int[] range) {
        this.range = range;
        bucket = new int[range.length];
        last = 0;
    }

    @Override
    protected Probe clone() {
        Probe p = new Probe(range);
        p.last = last;
        System.arraycopy(bucket, 0, p.bucket, 0, bucket.length);
        return p;
    }

    public boolean isShiftable() {
        if (bucket[last] != 1 || last + 1 >= bucket.length || range[last + 1] <= 1) {
            return false;
        }
        return true;
    }

    /**
     * This operation shifts to the right the last nonzero component if
     * it is equal to one and if it is not the last one.
     */
    public Probe shift() {
        Probe p = clone();
        p.bucket[last] = 0;
        p.last++;
        p.bucket[last] = 1;
        return p;
    }

    public boolean isExpandable() {
        if (last + 1 >= bucket.length || range[last + 1] <= 1) {
            return false;
        }
        return true;
    }

    /**
     * This operation sets to one the component following the last nonzero
     * component if it is not the last one.
     */
    public Probe expand() {
        Probe p = clone();
        p.last++;
        p.bucket[last] = 1;
        return p;
    }

    public boolean isExtendable() {
        if (bucket[last] + 1 >= range[last]) {
            return false;
        }
        return true;
    }

    /**
     * This operation adds one to the last nonzero component.
     */
    public Probe extend() {
        Probe p = clone();
        p.bucket[last]++;
        return p;
    }

    @Override
    public int compareTo(Probe o) {
        return Double.compare(prob, o.prob);
    }

    /**
     * Calculate the probability of the probe.
     */
    public void setProb(PrZ[] pz) {
        prob = 1.0;
        for (int i = 0; i < bucket.length; i++) {
            prob *= pz[i].prh[bucket[i]].pr;
        }
    }

    /**
     * Returns the bucket number of the probe.
     */
    public int hash(Hash hash, PrZ[] pz) {
        long r = 0;
        int[] c = hash.c;

        for (int i = 0; i < hash.k; i++) {
            r += c[pz[i].m] * pz[i].prh[bucket[i]].u;
        }

        int h = (int) (r % hash.P);
        if (h < 0) {
            h += hash.P;
        }

        return h;
    }
}
