/*
 * Copyright (c) 2010-2025 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.tensor;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.io.Serializable;
import java.lang.foreign.MemorySegment;

/**
 * A band matrix of single precision floating numbers.
 *
 * @author Haifeng Li
 */
class BandMatrix32 extends BandMatrix implements Serializable {
    /**
     * The on-heap band matrix storage.
     */
    final float[] AB;

    /**
     * Constructor.
     * @param m the number of rows.
     * @param n the number of columns.
     * @param kl the number of subdiagonals.
     * @param ku the number of superdiagonals.
     * @param ab the band matrix array.
     */
    public BandMatrix32(int m, int n, int kl, int ku, float[] ab) {
        super(MemorySegment.ofArray(ab), m, n, kl, ku);
        this.AB = ab;
    }

    @Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        memory = MemorySegment.ofArray(AB);
    }

    @Override
    public ScalarType scalarType() {
        return ScalarType.Float32;
    }

    @Override
    public long length() {
        return AB.length;
    }

    @Override
    public double get(int i, int j) {
        if (Math.max(0, j-ku) <= i && i <= Math.min(m-1, j+kl)) {
            return AB[j * ld + ku + i - j];
        } else {
            return 0.0;
        }
    }

    @Override
    public void set(int i, int j, double x) {
        if (Math.max(0, j-ku) <= i && i <= Math.min(m-1, j+kl)) {
            AB[j * ld + ku + i - j] = (float) x;
        } else {
            throw new UnsupportedOperationException(String.format("Set element at (%d, %d)", i, j));
        }
    }

    @Override
    public void add(int i, int j, double x) {
        if (Math.max(0, j-ku) <= i && i <= Math.min(m-1, j+kl)) {
            AB[j * ld + ku + i - j] += (float) x;
        } else {
            throw new UnsupportedOperationException(String.format("Add element at (%d, %d)", i, j));
        }
    }

    @Override
    public void sub(int i, int j, double x) {
        if (Math.max(0, j-ku) <= i && i <= Math.min(m-1, j+kl)) {
            AB[j * ld + ku + i - j] -= (float) x;
        } else {
            throw new UnsupportedOperationException(String.format("Sub element at (%d, %d)", i, j));
        }
    }

    @Override
    public void mul(int i, int j, double x) {
        if (Math.max(0, j-ku) <= i && i <= Math.min(m-1, j+kl)) {
            AB[j * ld + ku + i - j] *= (float) x;
        } else {
            throw new UnsupportedOperationException(String.format("Mul element at (%d, %d)", i, j));
        }
    }

    @Override
    public void div(int i, int j, double x) {
        if (Math.max(0, j-ku) <= i && i <= Math.min(m-1, j+kl)) {
            AB[j * ld + ku + i - j] /= (float) x;
        } else {
            throw new UnsupportedOperationException(String.format("Div element at (%d, %d)", i, j));
        }
    }

    @Override
    public BandMatrix copy() {
        float[] ab = AB.clone();
        BandMatrix matrix = new BandMatrix32(m, n, kl, ku, ab);
        if (m == n && kl == ku) {
            matrix.withUplo(uplo);
        }

        return matrix;
    }
}
