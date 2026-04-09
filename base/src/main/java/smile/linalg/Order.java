/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.linalg;

/**
 * Matrix memory layout (storage order).
 *
 * <p>BLAS and LAPACK support two storage orders:
 * <ul>
 *   <li>{@link #ROW_MAJOR} — elements of the same row are contiguous in memory
 *       (C / C++ convention, CBLAS value {@code 101}).</li>
 *   <li>{@link #COL_MAJOR} — elements of the same column are contiguous in memory
 *       (Fortran convention, CBLAS value {@code 102}).</li>
 * </ul>
 *
 * <p>SMILE's {@code DenseMatrix64} and {@code DenseMatrix32} use column-major order
 * by default (matching Fortran LAPACK conventions).
 */
public enum Order {
    /** Row-major layout (C order): rows are contiguous. */
    ROW_MAJOR(101),
    /** Column-major layout (Fortran order): columns are contiguous. */
    COL_MAJOR(102);

    /** The integer value passed to CBLAS / LAPACK. */
    private final int value;

    /** Constructor. */
    Order(int value) {
        this.value = value;
    }

    /**
     * Returns the integer value for CBLAS.
     * @return the CBLAS integer value.
     */
    public int blas() { return value; }

    /**
     * Returns the integer value for LAPACK (same encoding as CBLAS).
     * @return the LAPACK integer value.
     */
    public int lapack() { return value; }

    /**
     * Returns {@code true} if this layout is row-major.
     * @return {@code true} if row-major.
     */
    public boolean isRowMajor() { return this == ROW_MAJOR; }

    /**
     * Returns {@code true} if this layout is column-major.
     * @return {@code true} if column-major.
     */
    public boolean isColMajor() { return this == COL_MAJOR; }

    /**
     * Returns a human-readable description of this storage order.
     * @return a human-readable description.
     */
    public String description() {
        return switch (this) {
            case ROW_MAJOR -> "Row-major (C/C++ order): rows are contiguous";
            case COL_MAJOR -> "Column-major (Fortran order): columns are contiguous";
        };
    }

    /**
     * Returns the {@code Order} constant corresponding to the given CBLAS/LAPACK integer value.
     * @param value the integer value ({@code 101} for row-major, {@code 102} for column-major).
     * @return the matching {@code Order} constant.
     * @throws IllegalArgumentException if the value does not match any constant.
     */
    public static Order fromValue(int value) {
        for (Order o : values()) {
            if (o.value == value) return o;
        }
        throw new IllegalArgumentException("Unknown CBLAS Order value: " + value);
    }
}
