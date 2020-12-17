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
 * Gaussian model of hash values of nearest neighbor.
 *
 * @author Haifeng Li
 */
public class NeighborHashValueModel {

    /**
     * The hash values of query object.
     */
    public final double[] H;
    /**
     * The mean of hash values of neighbors.
     */
    public final double[] mean;
    /**
     * The variance of hash values of neighbors.
     */
    public double[] var;

    /**
     * Constructor.
     * @param H the hash values of query object.
     * @param mean the mean of hash values of neighbors.
     * @param var the variance of hash values of neighbors.
     */
    public NeighborHashValueModel(double[] H, double[] mean, double[] var) {
        this.H = H;
        this.mean = mean;
        this.var = var;
    }
}
