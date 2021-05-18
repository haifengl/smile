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

package smile.math.distance;

import java.io.Serializable;
import java.util.function.ToDoubleBiFunction;
import java.util.stream.IntStream;
import smile.math.blas.UPLO;
import smile.math.matrix.Matrix;

/**
 * An interface to calculate a distance measure between two objects. A distance
 * function maps pairs of points into the non-negative reals and has to satisfy
 * <ul>
 * <li> non-negativity: {@code d(x, y) >= 0}
 * <li> isolation: {@code d(x, y) = 0} if and only if {@code x = y}
 * <li> symmetry: {@code d(x, y) = d(x, y)}
 * </ul>
 * Note that a distance function is not required to satisfy triangular inequality
 * {@code |x - y| + |y - z| >= |x - z|}, which is necessary for a metric.
 *
 * @author Haifeng Li
 */
public interface Distance<T> extends ToDoubleBiFunction<T,T>, Serializable {
    /**
     * Returns the distance measure between two objects.
     * @param x an object.
     * @param y an object.
     * @return the distance.
     */
    double d(T x, T y);

    /**
     * Returns the distance measure between two objects.
     * This is simply for Scala convenience.
     * @param x an object.
     * @param y an object.
     * @return the distance.
     */
    default double apply(T x, T y) {
        return d(x, y);
    }

    @Override
    default double applyAsDouble(T x, T y) {
        return d(x, y);
    }

    /**
     * Returns the pairwise distance matrix.
     *
     * @param x samples.
     * @return the pairwise distance matrix.
     */
    default Matrix D(T[] x) {
        int n = x.length;
        int N = n * (n - 1) / 2;
        Matrix D = new Matrix(n, n);
        IntStream.range(0, N).parallel().forEach(k -> {
            int j = n - 2 - (int) Math.floor(Math.sqrt(-8*k + 4*n*(n-1)-7)/2.0 - 0.5);
            int i = k + j + 1 - n*(n-1)/2 + (n-j)*((n-j)-1)/2;
            D.set(i, j, d(x[i], x[j]));
        });

        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                D.set(i, j, D.get(j, i));
            }
        }

        D.uplo(UPLO.LOWER);
        return D;
    }

    /**
     * Returns the pairwise distance matrix.
     *
     * @param x samples.
     * @param y samples.
     * @return the pairwise distance matrix.
     */
    default Matrix D(T[] x, T[] y) {
        int m = x.length;
        int n = y.length;
        Matrix D = new Matrix(m, n);
        IntStream.range(0, m).parallel().forEach(i -> {
            T xi = x[i];
            for (int j = 0; j < n; j++) {
                D.set(i, j, d(xi, y[j]));
            }
        });

        return D;
    }
}
