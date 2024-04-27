/*
 * Copyright (c) 2010-2021 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */

package smile.math.kernel;

import java.util.Arrays;
import java.util.stream.DoubleStream;

/**
 * The sum kernel takes two kernels and combines them via k1(x, y) + k2(x, y)
 *
 * @param <T> the input type of kernel function.
 *
 * @author Haifeng Li
 */
public class SumKernel<T> implements MercerKernel<T> {
    /** The kernel to combine. */
    private final MercerKernel<T> k1;
    /** The kernel to combine. */
    private final MercerKernel<T> k2;

    /**
     * Constructor.
     * @param k1 the kernel to combine.
     * @param k2 the kernel to combine.
     */
    public SumKernel(MercerKernel<T> k1, MercerKernel<T> k2) {
        this.k1 = k1;
        this.k2 = k2;
    }

    @Override
    public double k(T x, T y) {
        return k1.k(x, y) + k2.k(x, y);
    }

    @Override
    public double[] kg(T x, T y) {
        double[] kg1 = k1.kg(x, y);
        double[] kg2 = k2.kg(x, y);
        double[] kg = new double[kg1.length + kg2.length - 1];
        kg[0] = kg1[0] + kg2[0];
        System.arraycopy(kg1, 1, kg, 1, kg1.length-1);
        System.arraycopy(kg2, 1, kg, kg1.length, kg2.length-1);
        return kg;
    }

    @Override
    public MercerKernel<T> of(double[] params) {
        int n1 = k1.lo().length;
        return new SumKernel<>(k1.of(params), k2.of(Arrays.copyOfRange(params, n1, params.length)));
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