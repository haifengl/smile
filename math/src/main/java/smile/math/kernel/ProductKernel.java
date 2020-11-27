/*******************************************************************************
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
 ******************************************************************************/

package smile.math.kernel;

import java.util.Arrays;
import java.util.stream.DoubleStream;

/**
 * The product kernel takes two kernels and combines them via k1(x, y) * k2(x, y).
 *
 * @author Haifeng Li
 */
public class ProductKernel<T> implements MercerKernel<T> {
    private final MercerKernel<T> k1;
    private final MercerKernel<T> k2;

    /**
     * Constructor.
     * @param k1 the kernel to combine.
     * @param k2 the kernel to combine.
     */
    public ProductKernel(MercerKernel<T> k1, MercerKernel<T> k2) {
        this.k1 = k1;
        this.k2 = k2;
    }

    @Override
    public double k(T x, T y) {
        return k1.k(x, y) * k2.k(x, y);
    }

    @Override
    public MercerKernel<T> of(double[] params) {
        int n1 = k1.lo().length;
        return new ProductKernel<>(k1.of(params), k2.of(Arrays.copyOfRange(params, n1, params.length)));
    }

    @Override
    public double[] hyperparameters() {
        return DoubleStream.concat(
                Arrays.stream(k1.hyperparameters()),
                Arrays.stream(k2.hyperparameters())
        ).toArray();
    }

    @Override
    public double[] lo() {
        return DoubleStream.concat(
                Arrays.stream(k1.lo()),
                Arrays.stream(k2.lo())
        ).toArray();
    }

    @Override
    public double[] hi() {
        return DoubleStream.concat(
                Arrays.stream(k1.hi()),
                Arrays.stream(k2.hi())
        ).toArray();
    }
}