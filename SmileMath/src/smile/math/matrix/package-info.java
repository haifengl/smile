/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/

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
