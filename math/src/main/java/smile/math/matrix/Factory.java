/*******************************************************************************
 * Copyright (c) 2010 Haifeng Li
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/

package smile.math.matrix;

import java.lang.reflect.Constructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An abstract interface of dense matrix.
 *
 * @author Haifeng Li
 */
class Factory {
    private static final Logger logger = LoggerFactory.getLogger(Factory.class);

    private static Class<?> nlmatrix;
    private static Constructor<?> nlmatrixArray;
    private static Constructor<?> nlmatrixArray2D;
    private static Constructor<?> nlmatrixZeros;
    private static Constructor<?> nlmatrixOnes;

    static {
        try {
            nlmatrix = Class.forName("smile.netlib.NLMatrix");

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
            logger.info("smile-netlib module is not available in the classpath. Pure Java matrix library will be employed.");
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

        return new JMatrix(nrows, ncols, value);
    }
}
