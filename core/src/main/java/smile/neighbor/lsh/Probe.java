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

package smile.neighbor.lsh;

/**
 * Probe to check for nearest neighbors.
 *
 * @author Haifeng Li
 */
public class Probe implements Comparable<Probe> {

    /**
     * The valid range of buckets.
     */
    private final int[] range;
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
     * @param range the valid range of buckets.
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

    /**
     * Returns true if the probe is shiftable.
     * @return true if the probe is shiftable.
     */
    public boolean isShiftable() {
        return bucket[last] == 1 && last + 1 < bucket.length && range[last + 1] > 1;
    }

    /**
     * This operation shifts to the right the last nonzero component if
     * it is equal to one and if it is not the last one.
     * @return the shifted probe.
     */
    public Probe shift() {
        Probe p = clone();
        p.bucket[last] = 0;
        p.last++;
        p.bucket[last] = 1;
        return p;
    }

    /**
     * Returns true if the probe is expandable.
     * @return true if the probe is expandable.
     */
    public boolean isExpandable() {
        return last + 1 < bucket.length && range[last + 1] > 1;
    }

    /**
     * This operation sets to one the component following the last nonzero
     * component if it is not the last one.
     * @return the expanded probe.
     */
    public Probe expand() {
        Probe p = clone();
        p.last++;
        p.bucket[last] = 1;
        return p;
    }

    /**
     * Returns true if the probe is extendable.
     * @return true if the probe is extendable.
     */
    public boolean isExtendable() {
        return bucket[last] + 1 < range[last];
    }

    /**
     * This operation adds one to the last nonzero component.
     * @return the extended probe.
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
     * @param pz the probability of the probe.
     */
    public void setProb(PrZ[] pz) {
        prob = 1.0;
        for (int i = 0; i < bucket.length; i++) {
            prob *= pz[i].prh[bucket[i]].pr;
        }
    }

    /**
     * Returns the bucket number of the probe.
     * @param hash the hash function.
     * @param pz the probability list of all buckets for given query object.
     * @return the hash code.
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
