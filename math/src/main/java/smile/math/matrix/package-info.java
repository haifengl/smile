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

/**
 * Matrix interface, dense and sparse (band or irregular) matrix encapsulation
 * classes, LU, QR, Cholesky, SVD and eigen decompositions, etc. A matrix
 * is a rectangular array of numbers, symbols, or expressions. The individual
 * items in a matrix are called its elements or entries.
 * <p>
 * One of most important matrix operations is the matrix vector
 * multiplication, which is the only operation needed in many iterative matrix
 * algorithms, e.g. biconjugate gradient method for solving linear equations and
 * power iteration and Lanczos algorithm for eigen decomposition, which are
 * usually very efficient for very large and sparse matrices.
 * 
 * @author Haifeng Li
 */
package smile.math.matrix;
