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
import smile.linalg.UPLO;
import static smile.tensor.ScalarType.*;

/**
 * A symmetric matrix of single precision floating numbers.
 *
 * @author Haifeng Li
 */
class SymmMatrix32 extends SymmMatrix implements Serializable {
    /**
     * The on-heap packed matrix storage.
     */
    final float[] ap;

    /**
     * Default constructor for readObject.
     */
    private SymmMatrix32() {
        this.ap = null;
    }

    /**
     * Constructor.
     * @param uplo the symmetric matrix stores the upper or lower triangle.
     * @param n the number of rows/columns.
     * @param ap the packed matrix array.
     */
    public SymmMatrix32(UPLO uplo, int n, float[] ap) {
        super(MemorySegment.ofArray(ap), uplo, n);
        this.ap = ap;
    }

    @Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        memory = MemorySegment.ofArray(ap);
    }

    @Override
    public ScalarType scalarType() {
        return Float32;
    }

    @Override
    public long length() {
        return ap.length;
    }

    @Override
    public double get(int i, int j) {
        return switch (uplo) {
            case LOWER -> {
                if (j > i) {
                    int tmp = i;
                    i = j;
                    j = tmp;
                }
                yield ap[i + ((2 * n - j - 1) * j / 2)];
            }
            case UPPER -> {
                if (i > j) {
                    int tmp = i;
                    i = j;
                    j = tmp;
                }
                yield ap[i + (j * (j + 1) / 2)];
            }
        };
    }

    @Override
    public void set(int i, int j, double x) {
        switch (uplo) {
            case LOWER -> {
                if (j > i) {
                    int tmp = i;
                    i = j;
                    j = tmp;
                }
                ap[i + ((2 * n - j - 1) * j / 2)] = (float) x;
            }
            case UPPER -> {
                if (i > j) {
                    int tmp = i;
                    i = j;
                    j = tmp;
                }
                ap[i + (j * (j + 1) / 2)] = (float) x;
            }
        }
    }

    @Override
    public void add(int i, int j, double x) {
        switch (uplo) {
            case LOWER -> {
                if (j > i) {
                    int tmp = i;
                    i = j;
                    j = tmp;
                }
                ap[i + ((2 * n - j - 1) * j / 2)] += (float) x;
            }
            case UPPER -> {
                if (i > j) {
                    int tmp = i;
                    i = j;
                    j = tmp;
                }
                ap[i + (j * (j + 1) / 2)] += (float) x;
            }
        }
    }

    @Override
    public void sub(int i, int j, double x) {
        switch (uplo) {
            case LOWER -> {
                if (j > i) {
                    int tmp = i;
                    i = j;
                    j = tmp;
                }
                ap[i + ((2 * n - j - 1) * j / 2)] -= (float) x;
            }
            case UPPER -> {
                if (i > j) {
                    int tmp = i;
                    i = j;
                    j = tmp;
                }
                ap[i + (j * (j + 1) / 2)] -= (float) x;
            }
        }
    }

    @Override
    public void mul(int i, int j, double x) {
        switch (uplo) {
            case LOWER -> {
                if (j > i) {
                    int tmp = i;
                    i = j;
                    j = tmp;
                }
                ap[i + ((2 * n - j - 1) * j / 2)] *= (float) x;
            }
            case UPPER -> {
                if (i > j) {
                    int tmp = i;
                    i = j;
                    j = tmp;
                }
                ap[i + (j * (j + 1) / 2)] *= (float) x;
            }
        }
    }

    @Override
    public void div(int i, int j, double x) {
        switch (uplo) {
            case LOWER -> {
                if (j > i) {
                    int tmp = i;
                    i = j;
                    j = tmp;
                }
                ap[i + ((2 * n - j - 1) * j / 2)] /= (float) x;
            }
            case UPPER -> {
                if (i > j) {
                    int tmp = i;
                    i = j;
                    j = tmp;
                }
                ap[i + (j * (j + 1) / 2)] /= (float) x;
            }
        }
    }

    @Override
    public SymmMatrix copy() {
        return new SymmMatrix32(uplo, n, ap.clone());
    }
}
