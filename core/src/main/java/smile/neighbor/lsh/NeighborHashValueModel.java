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
 * Probability list of all buckets for given query object.
 */
/**
 * Gaussian model of hash values of nearest neighbor.
 */
public class NeighborHashValueModel {

    /**
     * Hash values of query object.
     */
    public final double[] H;
    /**
     * Mean of hash values of neighbors.
     */
    public final double[] mean;
    /**
     * Variance of hash values of neighbors.
     */
    public double[] var;

    /**
     * Constructor.
     */
    public NeighborHashValueModel(double[] H, double[] mean, double[] var) {
        this.H = H;
        this.mean = mean;
        this.var = var;
    }
}
