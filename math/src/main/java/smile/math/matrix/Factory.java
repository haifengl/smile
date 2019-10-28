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

package smile.math.matrix;

import java.lang.reflect.Constructor;

/**
 * An abstract interface of dense matrix.
 *
 * @author Haifeng Li
 */
class Factory {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(Factory.class);

    private static Class<?> nlmatrix;
    private static Constructor<?> nlmatrixArray;
    private static Constructor<?> nlmatrixArray2D;
    private static Constructor<?> nlmatrixZeros;
    private static Constructor<?> nlmatrixOnes;

    private static Class<?> ndmatrix;
    private static Constructor<?> ndmatrixArray;
    private static Constructor<?> ndmatrixArray2D;
    private static Constructor<?> ndmatrixZeros;
    private static Constructor<?> ndmatrixOnes;

    static {
        try {
            nlmatrix = Class.forName("smile.netlib.NLMatrix");
            logger.info("smile-netlib module is available.");

            try {
                nlmatrixArray2D = nlmatrix.getConstructor(double[][].class);
            } catch (NoSuchMethodException e) {
                logger.error("NLMatrix(double[][]) does not exist");
            }

            try {
                nlmatrixArray = nlmatrix.getConstructor(double[].class);
            } catch (NoSuchMethodException e) {
                logger.error("NLMatrix(double[]) does not exist");
            }

            try {
                nlmatrixZeros = nlmatrix.getConstructor(Integer.TYPE, Integer.TYPE);
            } catch (NoSuchMethodException e) {
                logger.error("NLMatrix(int, int) does not exist");
            }

            try {
                nlmatrixOnes = nlmatrix.getConstructor(Integer.TYPE, Integer.TYPE, Double.TYPE);
            } catch (NoSuchMethodException e) {
                logger.error("NLMatrix(int, int, double) does not exist");
            }
        } catch (ClassNotFoundException e) {

        }

        try {
            ndmatrix = Class.forName("smile.nd4j.NDMatrix");
            logger.info("smile-nd4j module is available.");

            try {
                ndmatrixArray2D = ndmatrix.getConstructor(double[][].class);
            } catch (NoSuchMethodException e) {
                logger.error("NDMatrix(double[][]) does not exist");
            }

            try {
                ndmatrixArray = ndmatrix.getConstructor(double[].class);
            } catch (NoSuchMethodException e) {
                logger.error("NDMatrix(double[]) does not exist");
            }

            try {
                ndmatrixZeros = ndmatrix.getConstructor(Integer.TYPE, Integer.TYPE);
            } catch (NoSuchMethodException e) {
                logger.error("NDMatrix(int, int) does not exist");
            }

            try {
                ndmatrixOnes = ndmatrix.getConstructor(Integer.TYPE, Integer.TYPE, Double.TYPE);
            } catch (NoSuchMethodException e) {
                logger.error("NDMatrix(int, int, double) does not exist");
            }
        } catch (ClassNotFoundException e) {

        }
    }

    /** Creates a matrix initialized by A. */
    public static DenseMatrix matrix(double[][] A) {
        if (nlmatrixZeros != null) {
            try {
                return (DenseMatrix) nlmatrixArray2D.newInstance((Object) A);
            } catch (Exception e) {
                logger.error("Failed to call NLMatrix(double[][]): {}", e);
            }
        }

        if (ndmatrixZeros != null) {
            try {
                return (DenseMatrix) ndmatrixArray2D.newInstance((Object) A);
            } catch (Exception e) {
                logger.error("Failed to call NDMatrix(double[][]): {}", e);
            }
        }

        return new JMatrix(A);
    }

    /** Creates a column vector/matrix initialized by A. */
    public static DenseMatrix matrix(double[] A) {
        if (nlmatrixZeros != null) {
            try {
                return (DenseMatrix) nlmatrixArray.newInstance(A);
            } catch (Exception e) {
                logger.error("Failed to call NLMatrix(double[]): {}", e);
            }
        }

        if (ndmatrixZeros != null) {
            try {
                return (DenseMatrix) ndmatrixArray.newInstance(A);
            } catch (Exception e) {
                logger.error("Failed to call NDMatrix(double[]): {}", e);
            }
        }

        return new JMatrix(A);
    }

    /** Creates a matrix of all zeros. */
    public static DenseMatrix matrix(int nrows, int ncols) {
        if (nlmatrixZeros != null) {
            try {
                return (DenseMatrix) nlmatrixZeros.newInstance(nrows, ncols);
            } catch (Exception e) {
                logger.error("Failed to call NLMatrix(int, int): {}", e);
            }
        }

        if (ndmatrixZeros != null) {
            try {
                return (DenseMatrix) ndmatrixZeros.newInstance(nrows, ncols);
            } catch (Exception e) {
                logger.error("Failed to call NDMatrix(int, int): {}", e);
            }
        }

        return new JMatrix(nrows, ncols);
    }

    /** Creates a matrix filled with given value. */
    public static DenseMatrix matrix(int nrows, int ncols, double value) {
        if (nlmatrixOnes != null) {
            try {
                return (DenseMatrix) nlmatrixOnes.newInstance(nrows, ncols, value);
            } catch (Exception e) {
                logger.error("Failed to call NLMatrix(int, int, double): {}", e);
            }
        }

        if (ndmatrixOnes != null) {
            try {
                return (DenseMatrix) ndmatrixOnes.newInstance(nrows, ncols, value);
            } catch (Exception e) {
                logger.error("Failed to call NDMatrix(int, int, double): {}", e);
            }
        }

        return new JMatrix(nrows, ncols, value);
    }
}
