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
package smile.math;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;

import smile.math.matrix.EigenValueDecomposition;
import smile.math.matrix.IMatrix;
import smile.math.matrix.LUDecomposition;
import smile.math.matrix.Matrix;
import smile.math.matrix.QRDecomposition;
import smile.math.matrix.SingularValueDecomposition;
import smile.sort.QuickSelect;
import smile.sort.QuickSort;
import smile.sort.SortUtils;

/**
 * A collection of useful mathematical functions. The following functions are
 * included:
 * <ul>
 * <li> scalar functions: Besides all methods in java.lang.Math are included
 * for convenience, sqr, factorial, logFractorial, choose, logChoose, log2 are
 * provided.
 * <li> vector functions: min, max, mean, sum, var, sd, cov, L<sub>1</sub> norm,
 * L<sub>2</sub> norm, L<sub>&infin;</sub> norm, normalize, unitize, cor, Spearman
 * correlation, Kendall correlation, distance, dot product, histogram, vector
 * (element-wise) copy, equal, plus, minus, times, and divide.
 * <li> matrix functions: min, max, rowmean, sum, L<sub>1</sub> norm,
 * L<sub>2</sub> norm, L<sub>&infin;</sub> norm, rank, det, trace, transpose,
 * inverse, SVD and eigen decomposition, linear systems (dense or tridiagonal)
 * and least square, matrix copy, equal, plus, minus and times.
 * <li> random functions: random, randomInt, and permutate.
 * <li> Find the root of a univariate function with or without derivative.
 * </uL>
 *
 * @author Haifeng Li
 */
public class Math {

    /**
     * The base of the natural logarithms.
     */
    public static final double E = java.lang.Math.E;
    /**
     * The ratio of the circumference of a circle to its diameter.
     */
    public static final double PI = java.lang.Math.PI;
    /**
     * The machine precision for the double type, which is the difference between 1
     * and the smallest value greater than 1 that is representable for the double type.
     */
    public static double EPSILON = Math.pow(2.0, -52.0);
    /**
     * The base of the exponent of the double type.
     */
    public static int RADIX = 2;
    /**
     * The number of digits (in radix base) in the mantissa.
     */
    public static int DIGITS = 53;
    /**
     * Rounding style.
     * <ul>
     * <li> 0 if floating-point addition chops
     * <li> 1 if floating-point addition rounds, but not in the ieee style
     * <li> 2 if floating-point addition rounds in the ieee style
     * <li> 3 if floating-point addition chops, and there is partial underflow
     * <li> 4 if floating-point addition rounds, but not in the ieee style, and there is partial underflow
     * <li> 5 if floating-point addition rounds in the ieee style, and there is partial underflow
     * </ul>
     */
    public static int ROUND_STYLE = 2;
    /**
     * The largest negative integer such that 1.0 + RADIX<sup>MACHEP</sup> &ne; 1.0,
     * except that machep is bounded below by -(DIGITS+3)
     */
    public static int MACHEP = -52;
    /**
     * The largest negative integer such that 1.0 - RADIX<sup>NEGEP</sup> &ne; 1.0,
     * except that negeps is bounded below by -(DIGITS+3)
     */
    public static int NEGEP = -53;
    /**
     * High quality random number generator.
     */
    private static smile.math.Random random = new smile.math.Random();

    /**
     * Dynamically determines the machine parameters of the floating-point arithmetic.
     */
    static {
        double beta, betain, betah, a, b, ZERO, ONE, TWO, temp, tempa, temp1;
        int i, itemp;

        ONE = (double) 1;
        TWO = ONE + ONE;
        ZERO = ONE - ONE;

        a = ONE;
        temp1 = ONE;
        while (temp1 - ONE == ZERO) {
            a = a + a;
            temp = a + ONE;
            temp1 = temp - a;
        }
        b = ONE;
        itemp = 0;
        while (itemp == 0) {
            b = b + b;
            temp = a + b;
            itemp = (int) (temp - a);
        }
        RADIX = itemp;
        beta = (double) RADIX;

        DIGITS = 0;
        b = ONE;
        temp1 = ONE;
        while (temp1 - ONE == ZERO) {
            DIGITS = DIGITS + 1;
            b = b * beta;
            temp = b + ONE;
            temp1 = temp - b;
        }
        ROUND_STYLE = 0;
        betah = beta / TWO;
        temp = a + betah;
        if (temp - a != ZERO) {
            ROUND_STYLE = 1;
        }
        tempa = a + beta;
        temp = tempa + betah;
        if ((ROUND_STYLE == 0) && (temp - tempa != ZERO)) {
            ROUND_STYLE = 2;
        }

        NEGEP = DIGITS + 3;
        betain = ONE / beta;
        a = ONE;
        for (i = 0; i < NEGEP; i++) {
            a = a * betain;
        }
        b = a;
        temp = ONE - a;
        while (temp - ONE == ZERO) {
            a = a * beta;
            NEGEP = NEGEP - 1;
            temp = ONE - a;
        }
        NEGEP = -(NEGEP);

        MACHEP = -(DIGITS) - 3;
        a = b;
        temp = ONE + a;
        while (temp - ONE == ZERO) {
            a = a * beta;
            MACHEP = MACHEP + 1;
            temp = ONE + a;
        }
        EPSILON = a;
    }
    
    /**
     * Private constructor.
     */
    private Math() {
    }

    /**
     * Returns the absolute value of a double value.
     */
    public static double abs(double a) {
        return java.lang.Math.abs(a);
    }

    /**
     * Returns the absolute value of a float value.
     */
    public static float abs(float a) {
        return java.lang.Math.abs(a);
    }

    /**
     * Returns the absolute value of an int value.
     */
    public static int abs(int a) {
        return java.lang.Math.abs(a);
    }

    /**
     * Returns the absolute value of a long value.
     */
    public static long abs(long a) {
        return java.lang.Math.abs(a);
    }

    /**
     * Returns the arc cosine of an angle, in the range of 0.0 through pi.
     */
    public static double acos(double a) {
        return java.lang.Math.acos(a);
    }

    /**
     * Returns the arc sine of an angle, in the range of -pi/2 through pi/2.
     */
    public static double asin(double a) {
        return java.lang.Math.asin(a);
    }

    /**
     * Returns the arc tangent of an angle, in the range of -pi/2 through pi/2.
     */
    public static double atan(double a) {
        return java.lang.Math.atan(a);
    }

    /**
     * Converts rectangular coordinates (x, y) to polar (r, theta).
     */
    public static double atan2(double y, double x) {
        return java.lang.Math.atan2(y, x);
    }

    /**
     * Returns the cube root of a double value.
     */
    public static double cbrt(double a) {
        return java.lang.Math.cbrt(a);
    }

    /**
     * Returns the smallest (closest to negative infinity) double value that
     * is greater than or equal to the argument and is equal to a mathematical
     * integer.
     */
    public static double ceil(double a) {
        return java.lang.Math.ceil(a);
    }

    /**
     * Returns the first floating-point argument with the sign of the second
     * floating-point argument.
     */
    public static double copySign(double magnitude, double sign) {
        return java.lang.Math.copySign(magnitude, sign);
    }

    /**
     * Returns the first floating-point argument with the sign of the second
     * floating-point argument.
     */
    public static float copySign(float magnitude, float sign) {
        return java.lang.Math.copySign(magnitude, sign);
    }

    /**
     * Returns the trigonometric cosine of an angle.
     */
    public static double cos(double a) {
        return java.lang.Math.cos(a);
    }

    /**
     * Returns the hyperbolic cosine of a double value.
     */
    public static double cosh(double x) {
        return java.lang.Math.cosh(x);
    }

    /**
     * Returns Euler's number e raised to the power of a double value.
     */
    public static double exp(double a) {
        return java.lang.Math.exp(a);
    }

    /**
     * Returns e<sup>x</sup>-1.
     */
    public static double expm1(double x) {
        return java.lang.Math.expm1(x);
    }

    /**
     * Returns the largest (closest to positive infinity) double value that
     * is less than or equal to the argument and is equal to a mathematical
     * integer.
     */
    public static double floor(double a) {
        return java.lang.Math.floor(a);
    }

    /**
     * Returns the unbiased exponent used in the representation of a double. 
     */
    public static int getExponent(double d) {
        return java.lang.Math.getExponent(d);
    }

    /**
     * Returns the unbiased exponent used in the representation of a float.
     */
    public static int getExponent(float f) {
        return java.lang.Math.getExponent(f);
    }

    /**
     * Returns sqrt(x2 +y2) without intermediate overflow or underflow.
     */
    public static double hypot(double x, double y) {
        return java.lang.Math.hypot(x, y);
    }

    /**
     * Computes the remainder operation on two arguments as prescribed by the IEEE 754 standard.
     */
    public static double IEEEremainder(double f1, double f2) {
        return java.lang.Math.IEEEremainder(f1, f2);
    }

    /**
     * Returns the natural logarithm (base e) of a double value.
     */
    public static double log(double a) {
        return java.lang.Math.log(a);
    }

    /**
     * Returns the base 10 logarithm of a double value.
     */
    public static double log10(double a) {
        return java.lang.Math.log10(a);
    }

    /**
     * Returns the natural logarithm of the sum of the argument and 1.
     */
    public static double log1p(double x) {
        return java.lang.Math.log1p(x);
    }

    /**
     * Returns the greater of two double values.
     */
    public static double max(double a, double b) {
        return java.lang.Math.max(a, b);
    }

    /**
     * Returns the greater of two float values.
     */
    public static float max(float a, float b) {
        return java.lang.Math.max(a, b);
    }

    /**
     * Returns the greater of two int values.
     */
    public static int max(int a, int b) {
        return java.lang.Math.max(a, b);
    }

    /**
     * Returns the greater of two long values.
     */
    public static long max(long a, long b) {
        return java.lang.Math.max(a, b);
    }

    /**
     * Returns the smaller of two double values.
     */
    public static double min(double a, double b) {
        return java.lang.Math.min(a, b);
    }

    /**
     * Returns the smaller of two float values.
     */
    public static float min(float a, float b) {
        return java.lang.Math.min(a, b);
    }

    /**
     * Returns the smaller of two int values.
     */
    public static int min(int a, int b) {
        return java.lang.Math.min(a, b);
    }

    /**
     * Returns the smaller of two long values.
     */
    public static long min(long a, long b) {
        return java.lang.Math.min(a, b);
    }

    /**
     * Returns the floating-point number adjacent to the first argument in
     * the direction of the second argument.
     */
    public static double nextAfter(double start, double direction) {
        return java.lang.Math.nextAfter(start, direction);
    }

    /**
     * Returns the floating-point number adjacent to the first argument in
     * the direction of the second argument.
     */
    public static float nextAfter(float start, double direction) {
        return java.lang.Math.nextAfter(start, direction);
    }

    /**
     * Returns the floating-point value adjacent to d in the direction
     * of positive infinity.
     */
    public static double nextUp(double d) {
        return java.lang.Math.nextUp(d);
    }

    /**
     * Returns the floating-point value adjacent to f in the direction
     * of positive infinity.
     */
    public static float nextUp(float f) {
        return java.lang.Math.nextUp(f);
    }

    /**
     * Returns the value of the first argument raised to the power of the second argument.
     */
    public static double pow(double a, double b) {
        return java.lang.Math.pow(a, b);
    }

    /**
     * Returns the double value that is closest in value to the argument and is equal to a mathematical integer.
     */
    public static double rint(double a) {
        return java.lang.Math.rint(a);
    }

    /**
     * Returns the closest long to the argument.
     */
    public static long round(double a) {
        return java.lang.Math.round(a);
    }

    /**
     * Returns the closest int to the argument.
     */
    public static int round(float a) {
        return java.lang.Math.round(a);
    }

    /**
     * Returns d x 2<sup>scaleFactor</sup> rounded as if performed by a single
     * correctly rounded floating-point multiply to a member of the double value
     * set. 
     */
    public static double scalb(double d, int scaleFactor) {
        return java.lang.Math.scalb(d, scaleFactor);
    }

    /**
     * Returns f x 2<sup>scaleFactor</sup> rounded as if performed by a single
     * correctly rounded floating-point multiply to a member of the float value
     * set.
     */
    public static float scalb(float f, int scaleFactor) {
        return java.lang.Math.scalb(f, scaleFactor);
    }

    /**
     * Returns the signum of the argument; zero if the argument is
     * zero, 1.0 if the argument is greater than zero, -1.0 if the argument
     * is less than zero.
     */
    public static double signum(double d) {
        return java.lang.Math.signum(d);
    }

    /**
     * Returns the signum function of the argument; zero if the argument is
     * zero, 1.0f if the argument is greater than zero, -1.0f if the argument
     * is less than zero.
     */
    public static float signum(float f) {
        return java.lang.Math.signum(f);
    }

    /**
     * Returns the trigonometric sine of an angle.
     */
    public static double sin(double a) {
        return java.lang.Math.sin(a);
    }

    /**
     * Returns the hyperbolic sine of a double value.
     */
    public static double sinh(double x) {
        return java.lang.Math.sinh(x);
    }

    /**
     * Returns the correctly rounded positive square root of a double value.
     */
    public static double sqrt(double a) {
        return java.lang.Math.sqrt(a);
    }

    /**
     * Returns the trigonometric tangent of an angle.
     */
    public static double tan(double a) {
        return java.lang.Math.tan(a);
    }

    /**
     * Returns the hyperbolic tangent of a double value.
     */
    public static double tanh(double x) {
        return java.lang.Math.tanh(x);
    }

    /**
     * Converts an angle measured in radians to an approximately equivalent
     * angle measured in degrees.
     */
    public static double toDegrees(double angrad) {
        return java.lang.Math.toDegrees(angrad);
    }

    /**
     * Converts an angle measured in degrees to an approximately equivalent
     * angle measured in radians.
     */
    public static double toRadians(double angdeg) {
        return java.lang.Math.toRadians(angdeg);
    }

    /**
     * Returns the size of an ulp of the argument.
     */
    public static double ulp(double d) {
        return java.lang.Math.ulp(d);
    }

    /**
     * * Returns the size of an ulp of the argument.
     */
    public static float ulp(float f) {
        return java.lang.Math.ulp(f);
    }

    /**
     * log(2), used in log2().
     */
    private static final double LOG2 = java.lang.Math.log(2);

    /**
     * Log of base 2.
     */
    public static double log2(double x) {
        return java.lang.Math.log(x) / LOG2;
    }

    /**
     * Returns true if two double values equals to each other in the system precision.
     * @param a a double value.
     * @param b a double value.
     * @return true if two double values equals to each other in the system precision
     */
    public static boolean equals(double a, double b) {
        if (a == b) {
            return true;
        }
        
        double absa = Math.abs(a);
        double absb = Math.abs(b);
        return Math.abs(a - b) <= Math.min(absa, absb) * 2.2204460492503131e-16;
    }
        
    /**
     * Logistic sigmoid function.
     */
    public static double logistic(double x) {
        double y = 0.0;
        if (x < -40) {
            y = 2.353853e+17;
        } else if (x > 40) {
            y = 1.0 + 4.248354e-18;
        } else {
            y = 1.0 + Math.exp(-x);
        }

        return 1.0 / y;
    }

    /**
     * Returns x * x.
     */
    public static double sqr(double x) {
        return x * x;
    }

    /**
     * Returns true if x is a power of 2.
     */
    public static boolean isPower2(int x) {
        return x > 0 && (x & (x - 1)) == 0;
    }

    /**
     * Round a double vale to given digits such as 10^n, where n is a positive
     * or negative integer.
     */
    public static double round(double x, int decimal) {
        if (decimal < 0) {
            return round(x / pow(10, -decimal)) * pow(10, -decimal);
        } else {
            return round(x * pow(10, decimal)) / pow(10, decimal);
        }
    }

    /**
     * factorial of n
     *
     * @return factorial returned as double but is, numerically, an integer.
     * Numerical rounding may makes this an approximation after n = 21
     */
    public static double factorial(int n) {
        if (n < 0) {
            throw new IllegalArgumentException("n has to be nonnegative.");
        }

        double f = 1.0;
        for (int i = 2; i <= n; i++) {
            f *= i;
        }

        return f;
    }

    /**
     * log of factorial of n
     */
    public static double logFactorial(int n) {
        if (n < 0) {
            throw new IllegalArgumentException(String.format("n has to be nonnegative: %d", n));
        }

        double f = 0.0;
        for (int i = 2; i <= n; i++) {
            f += Math.log(i);
        }

        return f;
    }

    /**
     * n choose k. Returns 0 if n is less than k.
     */
    public static double choose(int n, int k) {
        if (n < 0 || k < 0) {
            throw new IllegalArgumentException(String.format("Invalid n = %d, k = %d", n, k));
        }

        if (n < k) {
            return 0.0;
        }

        return Math.floor(0.5 + Math.exp(logChoose(n, k)));
    }

    /**
     * log of n choose k
     */
    public static double logChoose(int n, int k) {
        if (n < 0 || k < 0 || k > n) {
            throw new IllegalArgumentException(String.format("Invalid n = %d, k = %d", n, k));
        }

        return Math.logFactorial(n) - Math.logFactorial(k) - Math.logFactorial(n - k);
    }

    /**
     * Given a set of n probabilities, generate a random number in [0, n).
     * @param prob probabilities of size n. The prob argument can be used to
     * give a vector of weights for obtaining the elements of the vector being
     * sampled. They need not sum to one, but they should be nonnegative and
     * not all zero.
     * @return a random integer in [0, n).
     */
    public static int random(double[] prob) {
        int[] ans = random(prob, 1);
        return ans[0];
    }

    /**
     * Given a set of m probabilities, draw with replacement a set of n random
     * number in [0, m).
     * @param prob probabilities of size n. The prob argument can be used to
     * give a vector of weights for obtaining the elements of the vector being
     * sampled. They need not sum to one, but they should be nonnegative and
     * not all zero.
     * @return an random array of length n in range of [0, m).
     */
    public static int[] random(double[] prob, int n) {
        // set up alias table
        double[] q = new double[prob.length];
        for (int i = 0; i < prob.length; i++) {
            q[i] = prob[i] * prob.length;
        }

        // initialize a with indices
        int[] a = new int[prob.length];
        for (int i = 0; i < prob.length; i++) {
            a[i] = i;
        }

        // set up H and L
        int[] HL = new int[prob.length];
        int head = 0;
        int tail = prob.length - 1;
        for (int i = 0; i < prob.length; i++) {
            if (q[i] >= 1.0) {
                HL[head++] = i;
            } else {
                HL[tail--] = i;
            }
        }

        while (head != 0 && tail != prob.length - 1) {
            int j = HL[tail + 1];
            int k = HL[head - 1];
            a[j] = k;
            q[k] += q[j] - 1;
            tail++;                                  // remove j from L
            if (q[k] < 1.0) {
                HL[tail--] = k;                      // add k to L
                head--;                              // remove k
            }
        }

        // generate sample
        int[] ans = new int[n];
        for (int i = 0; i < n; i++) {
            double rU = random() * prob.length;

            int k = (int) (rU);
            rU -= k;  /* rU becomes rU-[rU] */

            if (rU < q[k]) {
                ans[i] = k;
            } else {
                ans[i] = a[k];
            }
        }

        return ans;
    }

    /**
     * Generate a random number in [0, 1). This method is properly synchronized
     * to allow correct use by more than one thread. However, if many threads
     * need to generate pseudorandom numbers at a great rate, it may reduce
     * contention for each thread to have its own pseudorandom-number generator. 
     */
    public static synchronized double random() {
        return random.nextDouble();
    }

    /**
     * Generate n random numbers in [0, 1). This method is properly synchronized
     * to allow correct use by more than one thread. However, if many threads
     * need to generate pseudorandom numbers at a great rate, it may reduce
     * contention for each thread to have its own pseudorandom-number generator.
     */
    public static synchronized double[] random(int n) {
        double[] x = new double[n];
        random.nextDoubles(x);
        return x;
    }

    /**
     * Generate a uniform random number in the range [lo, hi). This method is
     * properly synchronized to allow correct use by more than one thread.
     * However, if many threads need to generate pseudorandom numbers at a
     * great rate, it may reduce contention for each thread to have its own
     * pseudorandom-number generator.
     * @param lo lower limit of range
     * @param hi upper limit of range
     * @return a uniform random real in the range [lo, hi)
     */
    public static synchronized double random(double lo, double hi) {
        return random.nextDouble(lo, hi);
    }

    /**
     * Generate n uniform random numbers in the range [lo, hi). This method is
     * properly synchronized to allow correct use by more than one thread.
     * However, if many threads need to generate pseudorandom numbers at a
     * great rate, it may reduce contention for each thread to have its own
     * pseudorandom-number generator.
     * @param n size of the array
     * @param lo lower limit of range
     * @param hi upper limit of range
     * @return a uniform random real in the range [lo, hi)
     */
    public static synchronized double[] random(double lo, double hi, int n) {
        double[] x = new double[n];
        random.nextDoubles(x, lo, hi);
        return x;
    }

    /**
     * Returns a random integer in [0, n). This method is properly synchronized
     * to allow correct use by more than one thread. However, if many threads
     * need to generate pseudorandom numbers at a great rate, it may reduce
     * contention for each thread to have its own pseudorandom-number generator.
     */
    public static synchronized int randomInt(int n) {
        return random.nextInt(n);
    }

    /**
     * Returns a random integer in [lo, hi). This method is properly synchronized
     * to allow correct use by more than one thread. However, if many threads
     * need to generate pseudorandom numbers at a great rate, it may reduce
     * contention for each thread to have its own pseudorandom-number generator.
     */
    public static synchronized int randomInt(int lo, int hi) {
        int w = hi - lo;
        return lo + random.nextInt(w);
    }

    /**
     * Generates a permutation of 0, 1, 2, ..., n-1, which is useful for
     * sampling without replacement.
     */
    public static int[] permutate(int n) {
        return random.permutate(n);
    }
    
    /**
     * Generates a permutation of given array. This method is properly synchronized
     * to allow correct use by more than one thread. However, if many threads
     * need to generate pseudorandom numbers at a great rate, it may reduce
     * contention for each thread to have its own pseudorandom-number generator.
     */
    public static void permutate(int[] x) {
        random.permutate(x);
    }

    /**
     * Generates a permutation of given array. This method is properly synchronized
     * to allow correct use by more than one thread. However, if many threads
     * need to generate pseudorandom numbers at a great rate, it may reduce
     * contention for each thread to have its own pseudorandom-number generator.
     */
    public static void permutate(float[] x) {
        random.permutate(x);
    }

    /**
     * Generates a permutation of given array. This method is properly synchronized
     * to allow correct use by more than one thread. However, if many threads
     * need to generate pseudorandom numbers at a great rate, it may reduce
     * contention for each thread to have its own pseudorandom-number generator.
     */
    public static void permutate(double[] x) {
        random.permutate(x);
    }

    /**
     * Generates a permutation of given array. This method is properly synchronized
     * to allow correct use by more than one thread. However, if many threads
     * need to generate pseudorandom numbers at a great rate, it may reduce
     * contention for each thread to have its own pseudorandom-number generator.
     */
    public static void permutate(Object[] x) {
        random.permutate(x);
    }

    /**
     * Returns a slice of data for given indices.
     */
    public static <E> E[] slice(E[] data, int[] index) {
        int n = index.length;

        @SuppressWarnings("unchecked")
        E[] x = (E[]) java.lang.reflect.Array.newInstance(data.getClass().getComponentType(), n);

        for (int i = 0; i < n; i++) {
            x[i] = data[index[i]];
        }

        return x;
    }

    /**
     * Returns a slice of data for given indices.
     */
    public static int[] slice(int[] data, int[] index) {
        int n = index.length;
        int[] x = new int[n];
        for (int i = 0; i < n; i++) {
            x[i] = data[index[i]];
        }

        return x;
    }

    /**
     * Returns a slice of data for given indices.
     */
    public static float[] slice(float[] data, int[] index) {
        int n = index.length;
        float[] x = new float[n];
        for (int i = 0; i < n; i++) {
            x[i] = data[index[i]];
        }

        return x;
    }

    /**
     * Returns a slice of data for given indices.
     */
    public static double[] slice(double[] data, int[] index) {
        int n = index.length;
        double[] x = new double[n];
        for (int i = 0; i < n; i++) {
            x[i] = data[index[i]];
        }

        return x;
    }

    /**
     * Determines if the polygon contains the specified coordinates.
     * 
     * @param point the coordinates of specified point to be tested.
     * @return true if the Polygon contains the specified coordinates; false otherwise.
     */
    public static boolean contains(double[][] polygon, double[] point) {
        return contains(polygon, point[0], point[1]);
    }
    
    /**
     * Determines if the polygon contains the specified coordinates.
     * 
     * @param x the specified x coordinate.
     * @param y the specified y coordinate.
     * @return true if the Polygon contains the specified coordinates; false otherwise.
     */
    public static boolean contains(double[][] polygon, double x, double y) {
        if (polygon.length <= 2) {
	    return false;
	}
        
	int hits = 0;

        int n = polygon.length;
	double lastx = polygon[n - 1][0];
	double lasty = polygon[n - 1][1];
	double curx, cury;

	// Walk the edges of the polygon
	for (int i = 0; i < n; lastx = curx, lasty = cury, i++) {
	    curx = polygon[i][0];
	    cury = polygon[i][1];

	    if (cury == lasty) {
		continue;
	    }

	    double leftx;
	    if (curx < lastx) {
		if (x >= lastx) {
		    continue;
		}
		leftx = curx;
	    } else {
		if (x >= curx) {
		    continue;
		}
		leftx = lastx;
	    }

	    double test1, test2;
	    if (cury < lasty) {
		if (y < cury || y >= lasty) {
		    continue;
		}
		if (x < leftx) {
		    hits++;
		    continue;
		}
		test1 = x - curx;
		test2 = y - cury;
	    } else {
		if (y < lasty || y >= cury) {
		    continue;
		}
		if (x < leftx) {
		    hits++;
		    continue;
		}
		test1 = x - lastx;
		test2 = y - lasty;
	    }

	    if (test1 < (test2 / (lasty - cury) * (lastx - curx))) {
		hits++;
	    }
	}

	return ((hits & 1) != 0);
    }

    /**
     * Reverses the order of the elements in the specified array.
     * @param a an array to reverse.
     */
    public static void reverse(int[] a) {
        int i = 0, j = a.length - 1;
        while (i < j) {
            SortUtils.swap(a, i++, j--);  // code for swap not shown, but easy enough
        }
    }

    /**
     * Reverses the order of the elements in the specified array.
     * @param a an array to reverse.
     */
    public static void reverse(float[] a) {
        int i = 0, j = a.length - 1;
        while (i < j) {
            SortUtils.swap(a, i++, j--);  // code for swap not shown, but easy enough
        }
    }

    /**
     * Reverses the order of the elements in the specified array.
     * @param a an array to reverse.
     */
    public static void reverse(double[] a) {
        int i = 0, j = a.length - 1;
        while (i < j) {
            SortUtils.swap(a, i++, j--);  // code for swap not shown, but easy enough
        }
    }

    /**
     * Reverses the order of the elements in the specified array.
     * @param a an array to reverse.
     */
    public static <T> void reverse(T[] a) {
        int i = 0, j = a.length - 1;
        while (i < j) {
            SortUtils.swap(a, i++, j--);
        }
    }

    /**
     * minimum of 3 integers
     */
    public static int min(int a, int b, int c) {
        return min(min(a, b), c);
    }

    /**
     * minimum of 3 floats
     */
    public static double min(float a, float b, float c) {
        return min(min(a, b), c);
    }

    /**
     * minimum of 3 doubles
     */
    public static double min(double a, double b, double c) {
        return min(min(a, b), c);
    }

    /**
     * maximum of 3 integers
     */
    public static int max(int a, int b, int c) {
        return max(max(a, b), c);
    }

    /**
     * maximum of 3 floats
     */
    public static float max(float a, float b, float c) {
        return max(max(a, b), c);
    }

    /**
     * maximum of 3 doubles
     */
    public static double max(double a, double b, double c) {
        return max(max(a, b), c);
    }

    /**
     * Returns the minimum value of an array.
     */
    public static int min(int... x) {
        int m = x[0];

        for (int n : x) {
            if (n < m) {
                m = n;
            }
        }

        return m;
    }

    /**
     * Returns the minimum value of an array.
     */
    public static float min(float... x) {
        float m = Float.POSITIVE_INFINITY;

        for (float n : x) {
            if (n < m) {
                m = n;
            }
        }

        return m;
    }

    /**
     * Returns the minimum value of an array.
     */
    public static double min(double... x) {
        double m = Double.POSITIVE_INFINITY;

        for (double n : x) {
            if (n < m) {
                m = n;
            }
        }

        return m;
    }

    /**
     * Returns the index of minimum value of an array.
     */
    public static int whichMin(int... x) {
        int m = x[0];
        int which = 0;

        for (int i = 1; i < x.length; i++) {
            if (x[i] < m) {
                m = x[i];
                which = i;
            }
        }

        return which;
    }

    /**
     * Returns the index of minimum value of an array.
     */
    public static int whichMin(float... x) {
        float m = Float.POSITIVE_INFINITY;
        int which = 0;

        for (int i = 0; i < x.length; i++) {
            if (x[i] < m) {
                m = x[i];
                which = i;
            }
        }

        return which;
    }

    /**
     * Returns the index of minimum value of an array.
     */
    public static int whichMin(double... x) {
        double m = Double.POSITIVE_INFINITY;
        int which = 0;

        for (int i = 0; i < x.length; i++) {
            if (x[i] < m) {
                m = x[i];
                which = i;
            }
        }

        return which;
    }

    /**
     * Returns the maximum value of an array.
     */
    public static int max(int... x) {
        int m = x[0];

        for (int n : x) {
            if (n > m) {
                m = n;
            }
        }

        return m;
    }

    /**
     * Returns the maximum value of an array.
     */
    public static float max(float... x) {
        float m = Float.NEGATIVE_INFINITY;

        for (float n : x) {
            if (n > m) {
                m = n;
            }
        }

        return m;
    }

    /**
     * Returns the maximum value of an array.
     */
    public static double max(double... x) {
        double m = Double.NEGATIVE_INFINITY;

        for (double n : x) {
            if (n > m) {
                m = n;
            }
        }

        return m;
    }

    /**
     * Returns the index of maximum value of an array.
     */
    public static int whichMax(int... x) {
        int m = x[0];
        int which = 0;

        for (int i = 1; i < x.length; i++) {
            if (x[i] > m) {
                m = x[i];
                which = i;
            }
        }

        return which;
    }

    /**
     * Returns the index of maximum value of an array.
     */
    public static int whichMax(float... x) {
        float m = Float.NEGATIVE_INFINITY;
        int which = 0;

        for (int i = 0; i < x.length; i++) {
            if (x[i] > m) {
                m = x[i];
                which = i;
            }
        }

        return which;
    }

    /**
     * Returns the index of maximum value of an array.
     */
    public static int whichMax(double... x) {
        double m = Double.NEGATIVE_INFINITY;
        int which = 0;

        for (int i = 0; i < x.length; i++) {
            if (x[i] > m) {
                m = x[i];
                which = i;
            }
        }

        return which;
    }

    /**
     * Returns the minimum of a matrix.
     */
    public static int min(int[][] matrix) {
        int m = matrix[0][0];

        for (int[] x : matrix) {
            for (int y : x) {
                if (m > y) {
                    m = y;
                }
            }
        }

        return m;
    }

    /**
     * Returns the minimum of a matrix.
     */
    public static double min(double[][] matrix) {
        double m = Double.POSITIVE_INFINITY;

        for (double[] x : matrix) {
            for (double y : x) {
                if (m > y) {
                    m = y;
                }
            }
        }

        return m;
    }

    /**
     * Returns the maximum of a matrix.
     */
    public static int max(int[][] matrix) {
        int m = matrix[0][0];

        for (int[] x : matrix) {
            for (int y : x) {
                if (m < y) {
                    m = y;
                }
            }
        }

        return m;
    }

    /**
     * Returns the maximum of a matrix.
     */
    public static double max(double[][] matrix) {
        double m = Double.NEGATIVE_INFINITY;

        for (double[] x : matrix) {
            for (double y : x) {
                if (m < y) {
                    m = y;
                }
            }
        }

        return m;
    }

    /**
     * Returns the row minimum for a matrix.
     */
    public static double[] rowMin(double[][] data) {
        double[] x = new double[data.length];

        for (int i = 0; i < x.length; i++) {
            x[i] = min(data[i]);
        }

        return x;
    }

    /**
     * Returns the row maximum for a matrix.
     */
    public static double[] rowMax(double[][] data) {
        double[] x = new double[data.length];

        for (int i = 0; i < x.length; i++) {
            x[i] = max(data[i]);
        }

        return x;
    }

    /**
     * Returns the row sums for a matrix.
     */
    public static double[] rowSum(double[][] data) {
        double[] x = new double[data.length];

        for (int i = 0; i < x.length; i++) {
            x[i] = sum(data[i]);
        }

        return x;
    }

    /**
     * Returns the row means for a matrix.
     */
    public static double[] rowMean(double[][] data) {
        double[] x = new double[data.length];

        for (int i = 0; i < x.length; i++) {
            x[i] = mean(data[i]);
        }

        return x;
    }

    /**
     * Returns the row standard deviations for a matrix.
     */
    public static double[] rowSd(double[][] data) {
        double[] x = new double[data.length];

        for (int i = 0; i < x.length; i++) {
            x[i] = sd(data[i]);
        }

        return x;
    }

    /**
     * Returns the column minimum for a matrix.
     */
    public static double[] colMin(double[][] data) {
        double[] x = new double[data[0].length];
        for (int i = 0; i < x.length; i++) {
            x[i] = Double.POSITIVE_INFINITY;
        }

        for (int i = 0; i < data.length; i++) {
            for (int j = 0; j < x.length; j++) {
                if (x[j] > data[i][j]) {
                    x[j] = data[i][j];
                }
            }
        }

        return x;
    }

    /**
     * Returns the column maximum for a matrix.
     */
    public static double[] colMax(double[][] data) {
        double[] x = new double[data[0].length];
        for (int i = 0; i < x.length; i++) {
            x[i] = Double.NEGATIVE_INFINITY;
        }

        for (int i = 0; i < data.length; i++) {
            for (int j = 0; j < x.length; j++) {
                if (x[j] < data[i][j]) {
                    x[j] = data[i][j];
                }
            }
        }

        return x;
    }

    /**
     * Returns the column sums for a matrix.
     */
    public static double[] colSum(double[][] data) {
        double[] x = data[0].clone();

        for (int i = 1; i < data.length; i++) {
            for (int j = 0; j < x.length; j++) {
                x[j] += data[i][j];
            }
        }

        return x;
    }

    /**
     * Returns the column sums for a matrix.
     */
    public static double[] colMean(double[][] data) {
        double[] x = data[0].clone();

        for (int i = 1; i < data.length; i++) {
            for (int j = 0; j < x.length; j++) {
                x[j] += data[i][j];
            }
        }

        scale(1.0 / data.length, x);

        return x;
    }

    /**
     * Returns the column deviations for a matrix.
     */
    public static double[] colSd(double[][] data) {
        if (data.length < 2) {
            throw new IllegalArgumentException("Array length is less than 2.");
        }

        int p = data[0].length;
        double[] sum = new double[p];
        double[] sumsq = new double[p];
        for (double[] x : data) {
            for (int i = 0; i < p; i++) {
                sum[i] += x[i];
                sumsq[i] += x[i] * x[i];
            }
        }

        int n = data.length - 1;
        for (int i = 0; i < p; i++) {
            sumsq[i] = java.lang.Math.sqrt(sumsq[i] / n - (sum[i] / data.length) * (sum[i] / n));
        }

        return sumsq;
    }

    /**
     * Returns the sum of an array.
     */
    public static int sum(int[] x) {
        double sum = 0.0;

        for (int n : x) {
            sum += n;
        }

        if (sum > Integer.MAX_VALUE || sum < -Integer.MAX_VALUE) {
            throw new ArithmeticException("Sum overflow: " + sum);
        }
        
        return (int) sum;
    }

    /**
     * Returns the sum of an array.
     */
    public static double sum(float[] x) {
        double sum = 0.0;

        for (float n : x) {
            sum += n;
        }

        return sum;
    }

    /**
     * Returns the sum of an array.
     */
    public static double sum(double[] x) {
        double sum = 0.0;

        for (double n : x) {
            sum += n;
        }

        return sum;
    }

    /**
     * Find the median of an array of type int. The input array will
     * be rearranged.
     */
    public static int median(int[] a) {
        return QuickSelect.median(a);
    }

    /**
     * Find the median of an array of type float. The input array will
     * be rearranged.
     */
    public static float median(float[] a) {
        return QuickSelect.median(a);
    }

    /**
     * Find the median of an array of type double. The input array will
     * be rearranged.
     */
    public static double median(double[] a) {
        return QuickSelect.median(a);
    }

    /**
     * Find the median of an array of type double. The input array will
     * be rearranged.
     */
    public static <T extends Comparable<? super T>> T median(T[] a) {
        return QuickSelect.median(a);
    }

    /**
     * Find the first quantile (p = 1/4) of an array of type int. The input array will
     * be rearranged.
     */
    public static int q1(int[] a) {
        return QuickSelect.q1(a);
    }

    /**
     * Find the first quantile (p = 1/4) of an array of type float. The input array will
     * be rearranged.
     */
    public static float q1(float[] a) {
        return QuickSelect.q1(a);
    }

    /**
     * Find the first quantile (p = 1/4) of an array of type double. The input array will
     * be rearranged.
     */
    public static double q1(double[] a) {
        return QuickSelect.q1(a);
    }

    /**
     * Find the first quantile (p = 1/4) of an array of type double. The input array will
     * be rearranged.
     */
    public static <T extends Comparable<? super T>> T q1(T[] a) {
        return QuickSelect.q1(a);
    }

    /**
     * Find the third quantile (p = 3/4) of an array of type int. The input array will
     * be rearranged.
     */
    public static int q3(int[] a) {
        return QuickSelect.q3(a);
    }

    /**
     * Find the third quantile (p = 3/4) of an array of type float. The input array will
     * be rearranged.
     */
    public static float q3(float[] a) {
        return QuickSelect.q3(a);
    }

    /**
     * Find the third quantile (p = 3/4) of an array of type double. The input array will
     * be rearranged.
     */
    public static double q3(double[] a) {
        return QuickSelect.q3(a);
    }

    /**
     * Find the third quantile (p = 3/4) of an array of type double. The input array will
     * be rearranged.
     */
    public static <T extends Comparable<? super T>> T q3(T[] a) {
        return QuickSelect.q3(a);
    }

    /**
     * Returns the mean of an array.
     */
    public static double mean(int[] x) {
        return (double) sum(x) / x.length;
    }

    /**
     * Returns the mean of an array.
     */
    public static double mean(float[] x) {
        return sum(x) / x.length;
    }

    /**
     * Returns the mean of an array.
     */
    public static double mean(double[] x) {
        return sum(x) / x.length;
    }

    /**
     * Returns the variance of an array.
     */
    public static double var(int[] x) {
        if (x.length < 2) {
            throw new IllegalArgumentException("Array length is less than 2.");
        }

        double sum = 0.0;
        double sumsq = 0.0;
        for (int xi : x) {
            sum += xi;
            sumsq += xi * xi;
        }

        int n = x.length - 1;
        return sumsq / n - (sum / x.length) * (sum / n);
    }

    /**
     * Returns the variance of an array.
     */
    public static double var(float[] x) {
        if (x.length < 2) {
            throw new IllegalArgumentException("Array length is less than 2.");
        }

        double sum = 0.0;
        double sumsq = 0.0;
        for (float xi : x) {
            sum += xi;
            sumsq += xi * xi;
        }

        int n = x.length - 1;
        return sumsq / n - (sum / x.length) * (sum / n);
    }

    /**
     * Returns the variance of an array.
     */
    public static double var(double[] x) {
        if (x.length < 2) {
            throw new IllegalArgumentException("Array length is less than 2.");
        }

        double sum = 0.0;
        double sumsq = 0.0;
        for (double xi : x) {
            sum += xi;
            sumsq += xi * xi;
        }

        int n = x.length - 1;
        return sumsq / n - (sum / x.length) * (sum / n);
    }

    /**
     * Returns the standard deviation of an array.
     */
    public static double sd(int[] x) {
        return sqrt(var(x));
    }

    /**
     * Returns the standard deviation of an array.
     */
    public static double sd(float[] x) {
        return sqrt(var(x));
    }

    /**
     * Returns the standard deviation of an array.
     */
    public static double sd(double[] x) {
        return sqrt(var(x));
    }

    /**
     * Returns the median absolute deviation (MAD). Note that input array will
     * be altered after the computation. MAD is a robust measure of
     * the variability of a univariate sample of quantitative data. For a
     * univariate data set X<sub>1</sub>, X<sub>2</sub>, ..., X<sub>n</sub>,
     * the MAD is defined as the median of the absolute deviations from the data's median:
     * <p>
     * MAD(X) = median(|X<sub>i</sub> - median(X<sub>i</sub>)|)
     * <p>
     * that is, starting with the residuals (deviations) from the data's median,
     * the MAD is the median of their absolute values.
     * <p>
     * MAD is a more robust estimator of scale than the sample variance or
     * standard deviation. For instance, MAD is more resilient to outliers in
     * a data set than the standard deviation. It thus behaves better with
     * distributions without a mean or variance, such as the Cauchy distribution.
     * <p>
     * In order to use the MAD as a consistent estimator for the estimation of
     * the standard deviation &sigma;, one takes &sigma; = K * MAD, where K is
     * a constant scale factor, which depends on the distribution. For normally
     * distributed data K is taken to be 1.4826. Other distributions behave
     * differently: for example for large samples from a uniform continuous
     * distribution, this factor is about 1.1547.
     */
    public static double mad(int[] x) {
        int m = median(x);
        for (int i = 0; i < x.length; i++) {
            x[i] = Math.abs(x[i] - m);
        }

        return median(x);
    }

    /**
     * Returns the median absolute deviation (MAD). Note that input array will
     * be altered after the computation. MAD is a robust measure of
     * the variability of a univariate sample of quantitative data. For a
     * univariate data set X<sub>1</sub>, X<sub>2</sub>, ..., X<sub>n</sub>,
     * the MAD is defined as the median of the absolute deviations from the data's median:
     * <p>
     * MAD(X) = median(|X<sub>i</sub> - median(X<sub>i</sub>)|)
     * <p>
     * that is, starting with the residuals (deviations) from the data's median,
     * the MAD is the median of their absolute values.
     * <p>
     * MAD is a more robust estimator of scale than the sample variance or
     * standard deviation. For instance, MAD is more resilient to outliers in
     * a data set than the standard deviation. It thus behaves better with
     * distributions without a mean or variance, such as the Cauchy distribution.
     * <p>
     * In order to use the MAD as a consistent estimator for the estimation of
     * the standard deviation &sigma;, one takes &sigma; = K * MAD, where K is
     * a constant scale factor, which depends on the distribution. For normally
     * distributed data K is taken to be 1.4826. Other distributions behave
     * differently: for example for large samples from a uniform continuous
     * distribution, this factor is about 1.1547.
     */
    public static double mad(float[] x) {
        float m = median(x);
        for (int i = 0; i < x.length; i++) {
            x[i] = Math.abs(x[i] - m);
        }

        return median(x);
    }

    /**
     * Returns the median absolute deviation (MAD). Note that input array will
     * be altered after the computation. MAD is a robust measure of
     * the variability of a univariate sample of quantitative data. For a
     * univariate data set X<sub>1</sub>, X<sub>2</sub>, ..., X<sub>n</sub>,
     * the MAD is defined as the median of the absolute deviations from the data's median:
     * <p>
     * MAD(X) = median(|X<sub>i</sub> - median(X<sub>i</sub>)|)
     * <p>
     * that is, starting with the residuals (deviations) from the data's median,
     * the MAD is the median of their absolute values.
     * <p>
     * MAD is a more robust estimator of scale than the sample variance or
     * standard deviation. For instance, MAD is more resilient to outliers in
     * a data set than the standard deviation. It thus behaves better with
     * distributions without a mean or variance, such as the Cauchy distribution.
     * <p>
     * In order to use the MAD as a consistent estimator for the estimation of
     * the standard deviation &sigma;, one takes &sigma; = K * MAD, where K is
     * a constant scale factor, which depends on the distribution. For normally
     * distributed data K is taken to be 1.4826. Other distributions behave
     * differently: for example for large samples from a uniform continuous
     * distribution, this factor is about 1.1547.
     */
    public static double mad(double[] x) {
        double m = median(x);
        for (int i = 0; i < x.length; i++) {
            x[i] = Math.abs(x[i] - m);
        }

        return median(x);
    }

    /**
     * Given a set of boolean values, are all of the values true? 
     */
    public static boolean all(boolean[] x) {
        for (boolean b : x) {
            if (!b) {
                return false;
            }
        }

        return true;
    }

    /**
     * Given a set of boolean values, is at least one of the values true?
     */
    public static boolean any(boolean[] x) {
        for (boolean b : x) {
            if (b) {
                return true;
            }
        }

        return false;
    }

    /**
     * The Euclidean distance.
     */
    public static double distance(int[] x, int[] y) {
        return Math.sqrt(squaredDistance(x, y));
    }

    /**
     * The Euclidean distance.
     */
    public static double distance(float[] x, float[] y) {
        return Math.sqrt(squaredDistance(x, y));
    }

    /**
     * The Euclidean distance.
     */
    public static double distance(double[] x, double[] y) {
        return Math.sqrt(squaredDistance(x, y));
    }

    /**
     * The Euclidean distance.
     */
    public static double distance(SparseArray x, SparseArray y) {
        return Math.sqrt(squaredDistance(x, y));
    }

    /**
     * The squared Euclidean distance.
     */
    public static double squaredDistance(int[] x, int[] y) {
        if (x.length != y.length) {
            throw new IllegalArgumentException("Input vector sizes are different.");
        }

        double sum = 0.0;
        for (int i = 0; i < x.length; i++) {
            sum += sqr(x[i] - y[i]);
        }

        return sum;
    }

    /**
     * The squared Euclidean distance.
     */
    public static double squaredDistance(float[] x, float[] y) {
        if (x.length != y.length) {
            throw new IllegalArgumentException("Input vector sizes are different.");
        }

        double sum = 0.0;
        for (int i = 0; i < x.length; i++) {
            sum += sqr(x[i] - y[i]);
        }

        return sum;
    }

    /**
     * The squared Euclidean distance.
     */
    public static double squaredDistance(double[] x, double[] y) {
        if (x.length != y.length) {
            throw new IllegalArgumentException("Input vector sizes are different.");
        }

        double sum = 0.0;
        for (int i = 0; i < x.length; i++) {
            sum += sqr(x[i] - y[i]);
        }

        return sum;
    }

    /**
     * The Euclidean distance.
     */
    public static double squaredDistance(SparseArray x, SparseArray y) {
        Iterator<SparseArray.Entry> it1 = x.iterator();
        Iterator<SparseArray.Entry> it2 = y.iterator();
        SparseArray.Entry e1 = it1.hasNext() ? it1.next() : null;
        SparseArray.Entry e2 = it2.hasNext() ? it2.next() : null;

        double sum = 0.0;
        while (e1 != null && e2 != null) {
            if (e1.i == e2.i) {
                sum += Math.sqr(e1.x - e2.x);
                e1 = it1.hasNext() ? it1.next() : null;
                e2 = it2.hasNext() ? it2.next() : null;
            } else if (e1.i > e2.i) {
                sum += Math.sqr(e2.x);
                e2 = it2.hasNext() ? it2.next() : null;
            } else {
                sum += Math.sqr(e1.x);
                e1 = it1.hasNext() ? it1.next() : null;
            }
        }
        
        while (it1.hasNext()) {
            sum += Math.sqr(it1.next().x);
        }

        while (it2.hasNext()) {
            sum += Math.sqr(it2.next().x);
        }
        
        return sum;
    }

    /**
     * Kullback-Leibler divergence. The Kullback-Leibler divergence (also
     * information divergence, information gain, relative entropy, or KLIC)
     * is a non-symmetric measure of the difference between two probability
     * distributions P and Q. KL measures the expected number of extra bits
     * required to code samples from P when using a code based on Q, rather
     * than using a code based on P. Typically P represents the "true"
     * distribution of data, observations, or a precise calculated theoretical
     * distribution. The measure Q typically represents a theory, model,
     * description, or approximation of P.
     * <p>
     * Although it is often intuited as a distance metric, the KL divergence is
     * not a true metric - for example, the KL from P to Q is not necessarily
     * the same as the KL from Q to P.
     */
    public static double KullbackLeiblerDivergence(double[] x, double[] y) {
        boolean intersection = false;
        double kl = 0.0;

        for (int i = 0; i < x.length; i++) {
            if (x[i] != 0.0 && y[i] != 0.0) {
                intersection = true;
                kl += x[i] * Math.log(x[i] / y[i]);
            }
        }

        if (intersection) {
            return kl;
        } else {
            return Double.POSITIVE_INFINITY;
        }
    }

    /**
     * Kullback-Leibler divergence. The Kullback-Leibler divergence (also
     * information divergence, information gain, relative entropy, or KLIC)
     * is a non-symmetric measure of the difference between two probability
     * distributions P and Q. KL measures the expected number of extra bits
     * required to code samples from P when using a code based on Q, rather
     * than using a code based on P. Typically P represents the "true"
     * distribution of data, observations, or a precise calculated theoretical
     * distribution. The measure Q typically represents a theory, model,
     * description, or approximation of P.
     * <p>
     * Although it is often intuited as a distance metric, the KL divergence is
     * not a true metric - for example, the KL from P to Q is not necessarily
     * the same as the KL from Q to P.
     */
    public static double KullbackLeiblerDivergence(SparseArray x, SparseArray y) {
        if (x.isEmpty()) {
            throw new IllegalArgumentException("List x is empty.");
        }

        if (y.isEmpty()) {
            throw new IllegalArgumentException("List y is empty.");
        }

        Iterator<SparseArray.Entry> iterX = x.iterator();
        Iterator<SparseArray.Entry> iterY = y.iterator();

        SparseArray.Entry a = iterX.hasNext() ? iterX.next() : null;
        SparseArray.Entry b = iterY.hasNext() ? iterY.next() : null;

        boolean intersection = false;
        double kl = 0.0;

        while (a != null && b != null) {
            if (a.i < b.i) {
                a = iterX.hasNext() ? iterX.next() : null;
            } else if (a.i > b.i) {
                b = iterY.hasNext() ? iterY.next() : null;
            } else {
                intersection = true;
                kl += a.x * Math.log(a.x / b.x);

                a = iterX.hasNext() ? iterX.next() : null;
                b = iterY.hasNext() ? iterY.next() : null;
            }
        }

        if (intersection) {
            return kl;
        } else {
            return Double.POSITIVE_INFINITY;
        }
    }

    /**
     * Kullback-Leibler divergence. The Kullback-Leibler divergence (also
     * information divergence, information gain, relative entropy, or KLIC)
     * is a non-symmetric measure of the difference between two probability
     * distributions P and Q. KL measures the expected number of extra bits
     * required to code samples from P when using a code based on Q, rather
     * than using a code based on P. Typically P represents the "true"
     * distribution of data, observations, or a precise calculated theoretical
     * distribution. The measure Q typically represents a theory, model,
     * description, or approximation of P.
     * <p>
     * Although it is often intuited as a distance metric, the KL divergence is
     * not a true metric - for example, the KL from P to Q is not necessarily
     * the same as the KL from Q to P.
     */
    public static double KullbackLeiblerDivergence(double[] x, SparseArray y) {
        return KullbackLeiblerDivergence(y, x);
    }

    /**
     * Kullback-Leibler divergence. The Kullback-Leibler divergence (also
     * information divergence, information gain, relative entropy, or KLIC)
     * is a non-symmetric measure of the difference between two probability
     * distributions P and Q. KL measures the expected number of extra bits
     * required to code samples from P when using a code based on Q, rather
     * than using a code based on P. Typically P represents the "true"
     * distribution of data, observations, or a precise calculated theoretical
     * distribution. The measure Q typically represents a theory, model,
     * description, or approximation of P.
     * <p>
     * Although it is often intuited as a distance metric, the KL divergence is
     * not a true metric - for example, the KL from P to Q is not necessarily
     * the same as the KL from Q to P.
     */
    public static double KullbackLeiblerDivergence(SparseArray x, double[] y) {
        if (x.isEmpty()) {
            throw new IllegalArgumentException("List x is empty.");
        }

        Iterator<SparseArray.Entry> iter = x.iterator();

        boolean intersection = false;
        double kl = 0.0;
        while (iter.hasNext()) {
            SparseArray.Entry b = iter.next();
            int i = b.i;
            if (y[i] > 0) {
                intersection = true;
                kl += b.x * Math.log(b.x / y[i]);
            }
        }

        if (intersection) {
            return kl;
        } else {
            return Double.POSITIVE_INFINITY;
        }
    }

    /**
     * Jensen-Shannon divergence JS(P||Q) = (KL(P||M) + KL(Q||M)) / 2, where
     * M = (P+Q)/2. The Jensen-Shannon divergence is a popular
     * method of measuring the similarity between two probability distributions.
     * It is also known as information radius or total divergence to the average.
     * It is based on the Kullback-Leibler divergence, with the difference that
     * it is always a finite value. The square root of the Jensen-Shannon divergence
     * is a metric.
     */
    public static double JensenShannonDivergence(double[] x, double[] y) {
        double[] m = new double[x.length];
        for (int i = 0; i < m.length; i++) {
            m[i] = (x[i] + y[i]) / 2;
        }

        return (KullbackLeiblerDivergence(x, m) + KullbackLeiblerDivergence(y, m)) / 2;
    }

    /**
     * Jensen-Shannon divergence JS(P||Q) = (KL(P||M) + KL(Q||M)) / 2, where
     * M = (P+Q)/2. The Jensen-Shannon divergence is a popular
     * method of measuring the similarity between two probability distributions.
     * It is also known as information radius or total divergence to the average.
     * It is based on the Kullback-Leibler divergence, with the difference that
     * it is always a finite value. The square root of the Jensen-Shannon divergence
     * is a metric.
     */
    public static double JensenShannonDivergence(SparseArray x, SparseArray y) {
        if (x.isEmpty()) {
            throw new IllegalArgumentException("List x is empty.");
        }

        if (y.isEmpty()) {
            throw new IllegalArgumentException("List y is empty.");
        }

        Iterator<SparseArray.Entry> iterX = x.iterator();
        Iterator<SparseArray.Entry> iterY = y.iterator();

        SparseArray.Entry a = iterX.hasNext() ? iterX.next() : null;
        SparseArray.Entry b = iterY.hasNext() ? iterY.next() : null;

        double js = 0.0;

        while (a != null && b != null) {
            if (a.i < b.i) {
                double mi = a.x / 2;
                js += a.x * Math.log(a.x / mi);
                a = iterX.hasNext() ? iterX.next() : null;
            } else if (a.i > b.i) {
                double mi = b.x / 2;
                js += b.x * Math.log(b.x / mi);
                b = iterY.hasNext() ? iterY.next() : null;
            } else {
                double mi = (a.x + b.x) / 2;
                js += a.x * Math.log(a.x / mi) + b.x * Math.log(b.x / mi);

                a = iterX.hasNext() ? iterX.next() : null;
                b = iterY.hasNext() ? iterY.next() : null;
            }
        }

        return js / 2;
    }

    /**
     * Jensen-Shannon divergence JS(P||Q) = (KL(P||M) + KL(Q||M)) / 2, where
     * M = (P+Q)/2. The Jensen-Shannon divergence is a popular
     * method of measuring the similarity between two probability distributions.
     * It is also known as information radius or total divergence to the average.
     * It is based on the Kullback-Leibler divergence, with the difference that
     * it is always a finite value. The square root of the Jensen-Shannon divergence
     * is a metric.
     */
    public static double JensenShannonDivergence(double[] x, SparseArray y) {
        return JensenShannonDivergence(y, x);
    }

    /**
     * Jensen-Shannon divergence JS(P||Q) = (KL(P||M) + KL(Q||M)) / 2, where
     * M = (P+Q)/2. The Jensen-Shannon divergence is a popular
     * method of measuring the similarity between two probability distributions.
     * It is also known as information radius or total divergence to the average.
     * It is based on the Kullback-Leibler divergence, with the difference that
     * it is always a finite value. The square root of the Jensen-Shannon divergence
     * is a metric.
     */
    public static double JensenShannonDivergence(SparseArray x, double[] y) {
        if (x.isEmpty()) {
            throw new IllegalArgumentException("List x is empty.");
        }

        Iterator<SparseArray.Entry> iter = x.iterator();

        double js = 0.0;
        while (iter.hasNext()) {
            SparseArray.Entry b = iter.next();
            int i = b.i;
            double mi = (b.x + y[i]) / 2;
            js += b.x * Math.log(b.x / mi);
            if (y[i] > 0) {
                js += y[i] * Math.log(y[i] / mi);
            }
        }

        return js / 2;
    }

    /**
     * Returns the dot product between two vectors.
     */
    public static double dot(int[] x, int[] y) {
        if (x.length != y.length) {
            throw new IllegalArgumentException("Arrays have different length.");
        }

        double p = 0.0;
        for (int i = 0; i < x.length; i++) {
            p += x[i] * y[i];
        }

        return p;
    }

    /**
     * Returns the dot product between two vectors.
     */
    public static double dot(float[] x, float[] y) {
        if (x.length != y.length) {
            throw new IllegalArgumentException("Arrays have different length.");
        }

        double p = 0.0;
        for (int i = 0; i < x.length; i++) {
            p += x[i] * y[i];
        }

        return p;
    }

    /**
     * Returns the dot product between two vectors.
     */
    public static double dot(double[] x, double[] y) {
        if (x.length != y.length) {
            throw new IllegalArgumentException("Arrays have different length.");
        }

        double p = 0.0;
        for (int i = 0; i < x.length; i++) {
            p += x[i] * y[i];
        }

        return p;
    }

    /**
     * Returns the dot product between two sparse arrays.
     */
    public static double dot(SparseArray x, SparseArray y) {
        Iterator<SparseArray.Entry> it1 = x.iterator();
        Iterator<SparseArray.Entry> it2 = y.iterator();
        SparseArray.Entry e1 = it1.hasNext() ? it1.next() : null;
        SparseArray.Entry e2 = it2.hasNext() ? it2.next() : null;

        double s = 0.0;
        while (e1 != null && e2 != null) {
            if (e1.i == e2.i) {
                s += e1.x * e2.x;
                e1 = it1.hasNext() ? it1.next() : null;
                e2 = it2.hasNext() ? it2.next() : null;
            } else if (e1.i > e2.i) {
                e2 = it2.hasNext() ? it2.next() : null;
            } else {
                e1 = it1.hasNext() ? it1.next() : null;
            }
        }
        
        return s;
    }

    /**
     * Returns the covariance between two vectors.
     */
    public static double cov(int[] x, int[] y) {
        if (x.length != y.length) {
            throw new IllegalArgumentException("Arrays have different length.");
        }

        if (x.length < 3) {
            throw new IllegalArgumentException("array length has to be at least 3.");
        }

        double mx = mean(x);
        double my = mean(y);

        double Sxy = 0.0;
        for (int i = 0; i < x.length; i++) {
            double dx = x[i] - mx;
            double dy = y[i] - my;
            Sxy += dx * dy;
        }

        return Sxy / (x.length - 1);
    }

    /**
     * Returns the covariance between two vectors.
     */
    public static double cov(float[] x, float[] y) {
        if (x.length != y.length) {
            throw new IllegalArgumentException("Arrays have different length.");
        }

        if (x.length < 3) {
            throw new IllegalArgumentException("array length has to be at least 3.");
        }

        double mx = mean(x);
        double my = mean(y);

        double Sxy = 0.0;
        for (int i = 0; i < x.length; i++) {
            double dx = x[i] - mx;
            double dy = y[i] - my;
            Sxy += dx * dy;
        }

        return Sxy / (x.length - 1);
    }

    /**
     * Returns the covariance between two vectors.
     */
    public static double cov(double[] x, double[] y) {
        if (x.length != y.length) {
            throw new IllegalArgumentException("Arrays have different length.");
        }

        if (x.length < 3) {
            throw new IllegalArgumentException("array length has to be at least 3.");
        }

        double mx = mean(x);
        double my = mean(y);

        double Sxy = 0.0;
        for (int i = 0; i < x.length; i++) {
            double dx = x[i] - mx;
            double dy = y[i] - my;
            Sxy += dx * dy;
        }

        return Sxy / (x.length - 1);
    }

    /**
     * Returns the sample covariance matrix.
     */
    public static double[][] cov(double[][] data) {
        return cov(data, Math.colMean(data));
    }

    /**
     * Returns the sample covariance matrix.
     * @param mu the known mean of data.
     */
    public static double[][] cov(double[][] data, double[] mu) {
        double[][] sigma = new double[data[0].length][data[0].length];
        for (int i = 0; i < data.length; i++) {
            for (int j = 0; j < mu.length; j++) {
                for (int k = 0; k <= j; k++) {
                    sigma[j][k] += (data[i][j] - mu[j]) * (data[i][k] - mu[k]);
                }
            }
        }

        int n = data.length - 1;
        for (int j = 0; j < mu.length; j++) {
            for (int k = 0; k <= j; k++) {
                sigma[j][k] /= n;
                sigma[k][j] = sigma[j][k];
            }
        }

        return sigma;
    }

    /**
     * Returns the correlation coefficient between two vectors.
     */
    public static double cor(int[] x, int[] y) {
        if (x.length != y.length) {
            throw new IllegalArgumentException("Arrays have different length.");
        }

        if (x.length < 3) {
            throw new IllegalArgumentException("array length has to be at least 3.");
        }

        double Sxy = cov(x, y);
        double Sxx = var(x);
        double Syy = var(y);

        if (Sxx == 0 || Syy == 0) {
            return Double.NaN;
        }

        return Sxy / java.lang.Math.sqrt(Sxx * Syy);
    }

    /**
     * Returns the correlation coefficient between two vectors.
     */
    public static double cor(float[] x, float[] y) {
        if (x.length != y.length) {
            throw new IllegalArgumentException("Arrays have different length.");
        }

        if (x.length < 3) {
            throw new IllegalArgumentException("array length has to be at least 3.");
        }

        double Sxy = cov(x, y);
        double Sxx = var(x);
        double Syy = var(y);

        if (Sxx == 0 || Syy == 0) {
            return Double.NaN;
        }

        return Sxy / java.lang.Math.sqrt(Sxx * Syy);
    }

    /**
     * Returns the correlation coefficient between two vectors.
     */
    public static double cor(double[] x, double[] y) {
        if (x.length != y.length) {
            throw new IllegalArgumentException("Arrays have different length.");
        }

        if (x.length < 3) {
            throw new IllegalArgumentException("array length has to be at least 3.");
        }

        double Sxy = cov(x, y);
        double Sxx = var(x);
        double Syy = var(y);

        if (Sxx == 0 || Syy == 0) {
            return Double.NaN;
        }

        return Sxy / java.lang.Math.sqrt(Sxx * Syy);
    }

    /**
     * Returns the sample correlation matrix.
     */
    public static double[][] cor(double[][] data) {
        return cor(data, Math.colMean(data));
    }

    /**
     * Returns the sample correlation matrix.
     * @param mu the known mean of data.
     */
    public static double[][] cor(double[][] data, double[] mu) {
        double[][] sigma = cov(data, mu);

        int n = data[0].length;
        double[] sd = new double[n];
        for (int i = 0; i < n; i++) {
            sd[i] = sqrt(sigma[i][i]);
        }

        for (int i = 0; i < n; i++) {
            for (int j = 0; j <= i; j++) {
                sigma[i][j] /= sd[i] * sd[j];
                sigma[j][i] = sigma[i][j];
            }
        }

        return sigma;
    }

    /**
     * Given a sorted array, replaces the elements by their rank, including
     * midranking of ties, and returns as s the sum of f<sup>3</sup> - f, where
     * f is the number of elements in each tie.
     */
    private static double crank(double[] w) {
        int n = w.length;
        double s = 0.0;
        int j = 1;
        while (j < n) {
            if (w[j] != w[j - 1]) {
                w[j - 1] = j;
                ++j;
            } else {
                int jt = j + 1;
                while (jt <= n && w[jt - 1] == w[j - 1]) {
                    jt++;
                }

                double rank = 0.5 * (j + jt - 1);
                for (int ji = j; ji <= (jt - 1); ji++) {
                    w[ji - 1] = rank;
                }

                double t = jt - j;
                s += (t * t * t - t);
                j = jt;
            }
        }

        if (j == n) {
            w[n - 1] = n;
        }

        return s;
    }

    /**
     * The Spearman Rank Correlation Coefficient is a form of the Pearson
     * coefficient with the data converted to rankings (ie. when variables
     * are ordinal). It can be used when there is non-parametric data and hence
     * Pearson cannot be used.
     */
    public static double spearman(int[] x, int[] y) {
        if (x.length != y.length) {
            throw new IllegalArgumentException("Input vector sizes are different.");
        }

        int n = x.length;
        double[] wksp1 = new double[n];
        double[] wksp2 = new double[n];
        for (int j = 0; j < n; j++) {
            wksp1[j] = x[j];
            wksp2[j] = y[j];
        }

        QuickSort.sort(wksp1, wksp2);
        double sf = crank(wksp1);
        QuickSort.sort(wksp2, wksp1);
        double sg = crank(wksp2);

        double d = 0.0;
        for (int j = 0; j < n; j++) {
            d += Math.sqr(wksp1[j] - wksp2[j]);
        }

        int en = n;
        double en3n = en * en * en - en;
        double fac = (1.0 - sf / en3n) * (1.0 - sg / en3n);
        double rs = (1.0 - (6.0 / en3n) * (d + (sf + sg) / 12.0)) / Math.sqrt(fac);
        return rs;
    }

    /**
     * The Spearman Rank Correlation Coefficient is a form of the Pearson
     * coefficient with the data converted to rankings (ie. when variables
     * are ordinal). It can be used when there is non-parametric data and hence
     * Pearson cannot be used.
     */
    public static double spearman(float[] x, float[] y) {
        if (x.length != y.length) {
            throw new IllegalArgumentException("Input vector sizes are different.");
        }

        int n = x.length;
        double[] wksp1 = new double[n];
        double[] wksp2 = new double[n];
        for (int j = 0; j < n; j++) {
            wksp1[j] = x[j];
            wksp2[j] = y[j];
        }

        QuickSort.sort(wksp1, wksp2);
        double sf = crank(wksp1);
        QuickSort.sort(wksp2, wksp1);
        double sg = crank(wksp2);

        double d = 0.0;
        for (int j = 0; j < n; j++) {
            d += Math.sqr(wksp1[j] - wksp2[j]);
        }

        int en = n;
        double en3n = en * en * en - en;
        double fac = (1.0 - sf / en3n) * (1.0 - sg / en3n);
        double rs = (1.0 - (6.0 / en3n) * (d + (sf + sg) / 12.0)) / Math.sqrt(fac);
        return rs;
    }

    /**
     * The Spearman Rank Correlation Coefficient is a form of the Pearson
     * coefficient with the data converted to rankings (ie. when variables
     * are ordinal). It can be used when there is non-parametric data and hence
     * Pearson cannot be used.
     */
    public static double spearman(double[] x, double[] y) {
        if (x.length != y.length) {
            throw new IllegalArgumentException("Input vector sizes are different.");
        }

        int n = x.length;
        double[] wksp1 = new double[n];
        double[] wksp2 = new double[n];
        for (int j = 0; j < n; j++) {
            wksp1[j] = x[j];
            wksp2[j] = y[j];
        }

        QuickSort.sort(wksp1, wksp2);
        double sf = crank(wksp1);
        QuickSort.sort(wksp2, wksp1);
        double sg = crank(wksp2);

        double d = 0.0;
        for (int j = 0; j < n; j++) {
            d += Math.sqr(wksp1[j] - wksp2[j]);
        }

        int en = n;
        double en3n = en * en * en - en;
        double fac = (1.0 - sf / en3n) * (1.0 - sg / en3n);
        double rs = (1.0 - (6.0 / en3n) * (d + (sf + sg) / 12.0)) / Math.sqrt(fac);
        return rs;
    }

    /**
     * The Kendall Tau Rank Correlation Coefficient is used to measure the
     * degree of correspondence between sets of rankings where the measures
     * are not equidistant. It is used with non-parametric data.
     */
    public static double kendall(int[] x, int[] y) {
        if (x.length != y.length) {
            throw new IllegalArgumentException("Input vector sizes are different.");
        }

        int is = 0, n2 = 0, n1 = 0, n = x.length;
        double aa, a2, a1;
        for (int j = 0; j < n - 1; j++) {
            for (int k = j + 1; k < n; k++) {
                a1 = x[j] - x[k];
                a2 = y[j] - y[k];
                aa = a1 * a2;
                if (aa != 0.0) {
                    ++n1;
                    ++n2;
                    if (aa > 0) {
                        ++is;
                    } else {
                        --is;
                    }

                } else {
                    if (a1 != 0.0) {
                        ++n1;
                    }
                    if (a2 != 0.0) {
                        ++n2;
                    }
                }
            }
        }

        double tau = is / (Math.sqrt(n1) * Math.sqrt(n2));
        return tau;
    }

    /**
     * The Kendall Tau Rank Correlation Coefficient is used to measure the
     * degree of correspondence between sets of rankings where the measures
     * are not equidistant. It is used with non-parametric data.
     */
    public static double kendall(float[] x, float[] y) {
        if (x.length != y.length) {
            throw new IllegalArgumentException("Input vector sizes are different.");
        }

        int is = 0, n2 = 0, n1 = 0, n = x.length;
        double aa, a2, a1;
        for (int j = 0; j < n - 1; j++) {
            for (int k = j + 1; k < n; k++) {
                a1 = x[j] - x[k];
                a2 = y[j] - y[k];
                aa = a1 * a2;
                if (aa != 0.0) {
                    ++n1;
                    ++n2;
                    if (aa > 0) {
                        ++is;
                    } else {
                        --is;
                    }

                } else {
                    if (a1 != 0.0) {
                        ++n1;
                    }
                    if (a2 != 0.0) {
                        ++n2;
                    }
                }
            }
        }

        double tau = is / (Math.sqrt(n1) * Math.sqrt(n2));
        return tau;
    }

    /**
     * The Kendall Tau Rank Correlation Coefficient is used to measure the
     * degree of correspondence between sets of rankings where the measures
     * are not equidistant. It is used with non-parametric data.
     */
    public static double kendall(double[] x, double[] y) {
        if (x.length != y.length) {
            throw new IllegalArgumentException("Input vector sizes are different.");
        }

        int is = 0, n2 = 0, n1 = 0, n = x.length;
        double aa, a2, a1;
        for (int j = 0; j < n - 1; j++) {
            for (int k = j + 1; k < n; k++) {
                a1 = x[j] - x[k];
                a2 = y[j] - y[k];
                aa = a1 * a2;
                if (aa != 0.0) {
                    ++n1;
                    ++n2;
                    if (aa > 0) {
                        ++is;
                    } else {
                        --is;
                    }

                } else {
                    if (a1 != 0.0) {
                        ++n1;
                    }
                    if (a2 != 0.0) {
                        ++n2;
                    }
                }
            }
        }

        double tau = is / (Math.sqrt(n1) * Math.sqrt(n2));
        return tau;
    }

    /**
     * L1 vector norm.
     */
    public static double norm1(double[] x) {
        double norm = 0.0;

        for (double n : x) {
            norm += Math.abs(n);
        }

        return norm;
    }

    /**
     * L2 vector norm.
     */
    public static double norm2(double[] x) {
        double norm = 0.0;

        for (double n : x) {
            norm += n * n;
        }

        norm = Math.sqrt(norm);

        return norm;
    }

    /**
     * L-infinity vector norm. Maximum absolute value.
     */
    public static double normInf(double[] x) {
        int n = x.length;

        double f = Math.abs(x[0]);
        for (int i = 1; i < n; i++) {
            f = Math.max(f, Math.abs(x[i]));
        }

        return f;
    }

    /**
     * L2 vector norm.
     */
    public static double norm(double[] x) {
        return norm2(x);
    }

    /**
     * L1 matrix norm. Maximum column sum.
     */
    public static double norm1(double[][] x) {
        int m = x.length;
        int n = x[0].length;

        double f = 0.0;
        for (int j = 0; j < n; j++) {
            double s = 0.0;
            for (int i = 0; i < m; i++) {
                s += Math.abs(x[i][j]);
            }
            f = Math.max(f, s);
        }

        return f;
    }

    /**
     * L2 matrix norm. Maximum singular value.
     */
    public static double norm2(double[][] x) {
        return SingularValueDecomposition.decompose(x).norm();
    }

    /**
     * L2 matrix norm. Maximum singular value.
     */
    public static double norm(double[][] x) {
        return norm2(x);
    }

    /**
     * Infinity matrix norm. Maximum row sum.
     */
    public static double normInf(double[][] x) {
        int m = x.length;

        double f = 0.0;
        for (int i = 0; i < m; i++) {
            double s = norm1(x[i]);
            f = Math.max(f, s);
        }

        return f;
    }

    /**
     * Frobenius matrix norm. Sqrt of sum of squares of all elements.
     */
    public static double normFro(double[][] x) {
        int m = x.length;
        int n = x[0].length;

        double f = 0.0;
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                f = Math.hypot(f, x[i][j]);
            }
        }

        return f;
    }

    /**
     * Normalizes an array to mean 0 and variance 1.
     */
    public static void normalize(double[] x) {
        double mu = mean(x);
        double sigma = sd(x);

        if (sigma <= 0) {
            throw new IllegalArgumentException("array has variance of 0.");
        }

        for (int i = 0; i < x.length; i++) {
            x[i] = (x[i] - mu) / sigma;
        }
    }

    /**
     * Normalizes each column of a matrix to mean 0 and variance 1.
     */
    public static void normalize(double[][] x) {
        int n = x.length;
        int p = x[0].length;

        for (int j = 0; j < p; j++) {
            double mu = 0.0;
            double sd = 0.0;
            for (int i = 0; i < n; i++) {
                mu += x[i][j];
                sd += x[i][j] * x[i][j];
            }

            sd = Math.sqrt(sd / (n-1) - (mu / n) * (mu / (n-1)));
            mu /= n;

            if (sd <= 0) {
                throw new IllegalArgumentException(String.format("Column %d has variance of 0.", j));
            }

            for (int i = 0; i < n; i++) {
                x[i][j] = (x[i][j] - mu) / sd;
            }
        }
    }

    /**
     * Unitize an array so that L2 norm of x = 1.
     *
     * @param x the array of double
     */
    public static void unitize(double[] x) {
        unitize2(x);
    }

    /**
     * Unitize an array so that L1 norm of x is 1.
     *
     * @param x an array of nonnegative double
     */
    public static void unitize1(double[] x) {
        double n = norm1(x);

        for (int i = 0; i < x.length; i++) {
            x[i] /= n;
        }
    }

    /**
     * Unitize an array so that L2 norm of x = 1.
     *
     * @param x the array of double
     */
    public static void unitize2(double[] x) {
        double n = norm(x);

        for (int i = 0; i < x.length; i++) {
            x[i] /= n;
        }
    }

    /**
     * Returns the fitted linear function value y = intercept + slope * log(x).
     */
    private static double smoothed(int x, double slope, double intercept) {
        return Math.exp(intercept + slope * Math.log(x));
    }

    /**
     * Returns the index of given frequency.
     * @param r the frequency list.
     * @param f the given frequency.
     * @return the index of given frequency or -1 if it doesn't exist in the list.
     */
    private static int row(int[] r, int f) {
        int i = 0;

        while (i < r.length && r[i] < f) {
            ++i;
        }

        return ((i < r.length && r[i] == f) ? i : -1);
    }

    /**
     * Takes a set of (frequency, frequency-of-frequency) pairs, and applies
     * the "Simple Good-Turing" technique for estimating the probabilities
     * corresponding to the observed frequencies, and P<sub>0</sub>, the joint
     * probability of all unobserved species.
     * @param r the frequency in ascending order.
     * @param Nr the frequency of frequencies.
     * @param p on output, it is the estimated probabilities.
     * @return P<sub>0</sub> for all unobserved species.
     */
    public static double GoodTuring(int[] r, int[] Nr, double[] p) {
        final double CONFID_FACTOR = 1.96;

        if (r.length != Nr.length) {
            throw new IllegalArgumentException("The sizes of r and Nr are not same.");
        }

        int len = r.length;
        double[] logR = new double[len];
        double[] logZ = new double[len];
        double[] Z = new double[len];

        int N = 0;
        for (int j = 0; j < len; ++j) {
            N += r[j] * Nr[j];
        }

        int n1 = (r[0] != 1) ? 0 : Nr[0];
        double p0 = n1 / (double) N;

        for (int j = 0; j < len; ++j) {
            int q = j == 0     ?  0           : r[j - 1];
            int t = j == len - 1 ? 2 * r[j] - q : r[j + 1];
            Z[j] = 2.0 * Nr[j] / (t - q);
            logR[j] = Math.log(r[j]);
            logZ[j] = Math.log(Z[j]);
        }

        // Simple linear regression.
        double XYs = 0.0, Xsquares = 0.0, meanX = 0.0, meanY = 0.0;
        for (int i = 0; i < len; ++i) {
            meanX += logR[i];
            meanY += logZ[i];
        }
        meanX /= len;
        meanY /= len;
        for (int i = 0; i < len; ++i) {
            XYs += (logR[i] - meanX) * (logZ[i] - meanY);
            Xsquares += Math.sqr(logR[i] - meanX);
        }

        double slope = XYs / Xsquares;
        double intercept = meanY - slope * meanX;

        boolean indiffValsSeen = false;
        for (int j = 0; j < len; ++j) {
            double y = (r[j] + 1) * smoothed(r[j] + 1, slope, intercept) / smoothed(r[j], slope, intercept);

            if (row(r, r[j] + 1) < 0) {
                indiffValsSeen = true;
            }

            if (!indiffValsSeen) {
                int n = Nr[row(r, r[j] + 1)];
                double x = (r[j] + 1) * n / (double) Nr[j];
                if (Math.abs(x - y) <= CONFID_FACTOR * Math.sqrt(Math.sqr(r[j] + 1.0) * n / Math.sqr(Nr[j]) * (1 + n / (double) Nr[j]))) {
                    indiffValsSeen = true;
                } else {
                    p[j] = x;
                }
            }

            if (indiffValsSeen) {
                p[j] = y;
            }
        }

        double Nprime = 0.0;
        for (int j = 0; j < len; ++j) {
            Nprime += Nr[j] * p[j];
        }

        for (int j = 0; j < len; ++j) {
            p[j] = (1 - p0) * p[j] / Nprime;
        }

        return p0;
    }

    /**
     * Check if x element-wisely equals y.
     */
    public static boolean equals(int[] x, int[] y) {
        if (x.length != y.length) {
            throw new IllegalArgumentException(String.format("Arrays have different length: x[%d], y[%d]", x.length, y.length));
        }

        for (int i = 0; i < x.length; i++) {
            if (x[i] != y[i]) {
                return false;
            }
        }

        return true;
    }

    /**
     * Check if x element-wisely equals y with default epsilon 1E-7.
     */
    public static boolean equals(float[] x, float[] y) {
        return equals(x, y, 1.0E-7f);
    }

    /**
     * Check if x element-wisely equals y.
     */
    public static boolean equals(float[] x, float[] y, float epsilon) {
        if (x.length != y.length) {
            throw new IllegalArgumentException(String.format("Arrays have different length: x[%d], y[%d]", x.length, y.length));
        }

        for (int i = 0; i < x.length; i++) {
            if (Math.abs(x[i] - y[i]) > epsilon) {
                return false;
            }
        }

        return true;
    }

    /**
     * Check if x element-wisely equals y with default epsilon 1E-10.
     */
    public static boolean equals(double[] x, double[] y) {
        return equals(x, y, 1.0E-10);
    }

    /**
     * Check if x element-wisely equals y.
     */
    public static boolean equals(double[] x, double[] y, double eps) {
        if (x.length != y.length) {
            throw new IllegalArgumentException(String.format("Arrays have different length: x[%d], y[%d]", x.length, y.length));
        }

        if (eps <= 0.0) {
            throw new IllegalArgumentException("Invalid epsilon: " + eps);            
        }
        
        for (int i = 0; i < x.length; i++) {
            if (Math.abs(x[i] - y[i]) > eps) {
                return false;
            }
        }

        return true;
    }

    /**
     * Check if x element-wisely equals y.
     */
    public static <T extends Comparable<? super T>>  boolean equals(T[] x, T[] y) {
        if (x.length != y.length) {
            throw new IllegalArgumentException(String.format("Arrays have different length: x[%d], y[%d]", x.length, y.length));
        }

        for (int i = 0; i < x.length; i++) {
            if (x[i].compareTo(y[i]) != 0) {
                return false;
            }
        }

        return true;
    }

    /**
     * Check if x element-wisely equals y.
     */
    public static boolean equals(int[][] x, int[][] y) {
        if (x.length != y.length || x[0].length != y[0].length) {
            throw new IllegalArgumentException(String.format("Matrices have different rows: %d x %d vs %d x %d", x.length, x[0].length, y.length, y[0].length));
        }

        for (int i = 0; i < x.length; i++) {
            for (int j = 0; j < x[i].length; j++) {
                if (x[i][j] != y[i][j]) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Check if x element-wisely equals y with default epsilon 1E-7.
     */
    public static boolean equals(float[][] x, float[][] y) {
        return equals(x, y, 1.0E-7f);
    }

    /**
     * Check if x element-wisely equals y.
     */
    public static boolean equals(float[][] x, float[][] y, float epsilon) {
        if (x.length != y.length || x[0].length != y[0].length) {
            throw new IllegalArgumentException(String.format("Matrices have different rows: %d x %d vs %d x %d", x.length, x[0].length, y.length, y[0].length));
        }

        for (int i = 0; i < x.length; i++) {
            for (int j = 0; j < x[i].length; j++) {
                if (Math.abs(x[i][j] - y[i][j]) > epsilon) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Check if x element-wisely equals y with default epsilon 1E-10.
     */
    public static boolean equals(double[][] x, double[][] y) {
        return equals(x, y, 1.0E-10);
    }

    /**
     * Check if x element-wisely equals y.
     */
    public static boolean equals(double[][] x, double[][] y, double eps) {
        if (x.length != y.length || x[0].length != y[0].length) {
            throw new IllegalArgumentException(String.format("Matrices have different rows: %d x %d vs %d x %d", x.length, x[0].length, y.length, y[0].length));
        }

        if (eps <= 0.0) {
            throw new IllegalArgumentException("Invalid epsilon: " + eps);            
        }
        
        for (int i = 0; i < x.length; i++) {
            for (int j = 0; j < x[i].length; j++) {
                if (Math.abs(x[i][j] - y[i][j]) > eps) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Check if x element-wisely equals y.
     */
    public static <T extends Comparable<? super T>>  boolean equals(T[][] x, T[][] y) {
        if (x.length != y.length || x[0].length != y[0].length) {
            throw new IllegalArgumentException(String.format("Matrices have different rows: %d x %d vs %d x %d", x.length, x[0].length, y.length, y[0].length));
        }

        for (int i = 0; i < x.length; i++) {
            for (int j = 0; j < x[i].length; j++) {
                if (x[i][j].compareTo(y[i][j]) != 0) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Deep clone a two-dimensional array.
     */
    public static int[][] clone(int[][] x) {
        int[][] matrix = new int[x.length][];
        for (int i = 0; i < x.length; i++) {
            matrix[i] = x[i].clone();
        }

        return matrix;
    }

    /**
     * Deep clone a two-dimensional array.
     */
    public static float[][] clone(float[][] x) {
        float[][] matrix = new float[x.length][];
        for (int i = 0; i < x.length; i++) {
            matrix[i] = x[i].clone();
        }

        return matrix;
    }

    /**
     * Deep clone a two-dimensional array.
     */
    public static double[][] clone(double[][] x) {
        double[][] matrix = new double[x.length][];
        for (int i = 0; i < x.length; i++) {
            matrix[i] = x[i].clone();
        }

        return matrix;
    }

    /**
     * Swap two elements of an array.
     */
    public static void swap(int[] x, int i, int j) {
        int s = x[i];
        x[i] = x[j];
        x[j] = s;
    }

    /**
     * Swap two elements of an array.
     */
    public static void swap(float[] x, int i, int j) {
        float s = x[i];
        x[i] = x[j];
        x[j] = s;
    }

    /**
     * Swap two elements of an array.
     */
    public static void swap(double[] x, int i, int j) {
        double s = x[i];
        x[i] = x[j];
        x[j] = s;
    }

    /**
     * Swap two elements of an array.
     */
    public static void swap(Object[] x, int i, int j) {
        Object s = x[i];
        x[i] = x[j];
        x[j] = s;
    }

    /**
     * Swap two arrays.
     */
    public static void swap(int[] x, int[] y) {
        if (x.length != y.length) {
            throw new IllegalArgumentException(String.format("Arrays have different length: x[%d], y[%d]", x.length, y.length));
        }

        for (int i = 0; i < x.length; i++) {
            int s = x[i];
            x[i] = y[i];
            y[i] = s;
        }
    }

    /**
     * Swap two arrays.
     */
    public static void swap(float[] x, float[] y) {
        if (x.length != y.length) {
            throw new IllegalArgumentException(String.format("Arrays have different length: x[%d], y[%d]", x.length, y.length));
        }

        for (int i = 0; i < x.length; i++) {
            float s = x[i];
            x[i] = y[i];
            y[i] = s;
        }
    }

    /**
     * Swap two arrays.
     */
    public static void swap(double[] x, double[] y) {
        if (x.length != y.length) {
            throw new IllegalArgumentException(String.format("Arrays have different length: x[%d], y[%d]", x.length, y.length));
        }

        for (int i = 0; i < x.length; i++) {
            double s = x[i];
            x[i] = y[i];
            y[i] = s;
        }
    }

    /**
     * Swap two arrays.
     */
    public static <E> void swap(E[] x, E[] y) {
        if (x.length != y.length) {
            throw new IllegalArgumentException(String.format("Arrays have different length: x[%d], y[%d]", x.length, y.length));
        }

        for (int i = 0; i < x.length; i++) {
            E s = x[i];
            x[i] = y[i];
            y[i] = s;
        }
    }

    /**
     * Copy x into y.
     */
    public static void copy(int[] x, int[] y) {
        if (x.length != y.length) {
            throw new IllegalArgumentException(String.format("Arrays have different length: x[%d], y[%d]", x.length, y.length));
        }

        System.arraycopy(x, 0, y, 0, x.length);
    }

    /**
     * Copy x into y.
     */
    public static void copy(float[] x, float[] y) {
        if (x.length != y.length) {
            throw new IllegalArgumentException(String.format("Arrays have different length: x[%d], y[%d]", x.length, y.length));
        }

        System.arraycopy(x, 0, y, 0, x.length);
    }

    /**
     * Copy x into y.
     */
    public static void copy(double[] x, double[] y) {
        if (x.length != y.length) {
            throw new IllegalArgumentException(String.format("Arrays have different length: x[%d], y[%d]", x.length, y.length));
        }

        System.arraycopy(x, 0, y, 0, x.length);
    }

    /**
     * Copy x into y.
     */
    public static void copy(int[][] x, int[][] y) {
        if (x.length != y.length || x[0].length != y[0].length) {
            throw new IllegalArgumentException(String.format("Matrices have different rows: %d x %d vs %d x %d", x.length, x[0].length, y.length, y[0].length));
        }

        for (int i = 0; i < x.length; i++) {
            System.arraycopy(x[i], 0, y[i], 0, x[i].length);
        }
    }

    /**
     * Copy x into y.
     */
    public static void copy(float[][] x, float[][] y) {
        if (x.length != y.length || x[0].length != y[0].length) {
            throw new IllegalArgumentException(String.format("Matrices have different rows: %d x %d vs %d x %d", x.length, x[0].length, y.length, y[0].length));
        }

        for (int i = 0; i < x.length; i++) {
            System.arraycopy(x[i], 0, y[i], 0, x[i].length);
        }
    }

    /**
     * Copy x into y.
     */
    public static void copy(double[][] x, double[][] y) {
        if (x.length != y.length || x[0].length != y[0].length) {
            throw new IllegalArgumentException(String.format("Matrices have different rows: %d x %d vs %d x %d", x.length, x[0].length, y.length, y[0].length));
        }

        for (int i = 0; i < x.length; i++) {
            System.arraycopy(x[i], 0, y[i], 0, x[i].length);
        }
    }

    /**
     * Element-wise sum of two arrays y = x + y.
     */
    public static void plus(double[] y, double[] x) {
        if (x.length != y.length) {
            throw new IllegalArgumentException(String.format("Arrays have different length: x[%d], y[%d]", x.length, y.length));
        }

        for (int i = 0; i < x.length; i++) {
            y[i] += x[i];
        }
    }

    /**
     * Element-wise sum of two matrices y = x + y.
     */
    public static void plus(double[][] y, double[][] x) {
        if (x.length != y.length || x[0].length != y[0].length) {
            throw new IllegalArgumentException(String.format("Matrices have different rows: %d x %d vs %d x %d", x.length, x[0].length, y.length, y[0].length));
        }

        for (int i = 0; i < x.length; i++) {
            plus(y[i], x[i]);
        }
    }

    /**
     * Element-wise subtraction of two arrays y = y - x.
     * @param y minuend matrix
     * @param x subtrahend matrix
     */
    public static void minus(double[] y, double[] x) {
        if (x.length != y.length) {
            throw new IllegalArgumentException(String.format("Arrays have different length: x[%d], y[%d]", x.length, y.length));
        }

        for (int i = 0; i < x.length; i++) {
            y[i] -= x[i];
        }
    }

    /**
     * Element-wise subtraction of two matrices y = y - x.
     * @param y minuend matrix
     * @param x subtrahend matrix
     */
    public static void minus(double[][] y, double[][] x) {
        if (x.length != y.length || x[0].length != y[0].length) {
            throw new IllegalArgumentException(String.format("Matrices have different rows: %d x %d vs %d x %d", x.length, x[0].length, y.length, y[0].length));
        }

        for (int i = 0; i < x.length; i++) {
            minus(y[i], x[i]);
        }
    }

    /**
     * Scale each element of an array by a constant x = a * x.
     */
    public static void scale(double a, double[] x) {
        for (int i = 0; i < x.length; i++) {
            x[i] *= a;
        }
    }

    /**
     * Scale each element of an array by a constant y = a * x.
     */
    public static void scale(double a, double[] x, double[] y) {
        for (int i = 0; i < x.length; i++) {
            y[i] = a * x[i];
        }
    }

    /**
     * Update an array by adding a multiple of another array y = a * x + y.
     */
    public static void axpy(double a, double[] x, double[] y) {
        if (x.length != y.length) {
            throw new IllegalArgumentException(String.format("Arrays have different length: x[%d], y[%d]", x.length, y.length));
        }

        for (int i = 0; i < x.length; i++) {
            y[i] += a * x[i];
        }
    }

    /**
     * Product of a matrix and a vector y = A * x according to the rules of linear algebra.
     * Number of columns in A must equal number of elements in x.
     */
    public static void ax(double[][] A, double[] x, double[] y) {
        if (A[0].length != x.length) {
            throw new IllegalArgumentException(String.format("Array dimensions do not match for matrix multiplication: %dx%d vs %dx1", A.length, A[0].length, x.length));
        }

        if (A.length != y.length) {
            throw new IllegalArgumentException(String.format("Array dimensions do not match"));
        }

        Arrays.fill(y, 0.0);
        for (int i = 0; i < y.length; i++) {
            for (int k = 0; k < A[i].length; k++) {
                y[i] += A[i][k] * x[k];
            }
        }
    }

    /**
     * Product of a matrix and a vector y = A * x + y according to the rules of linear algebra.
     * Number of columns in A must equal number of elements in x.
     */
    public static void axpy(double[][] A, double[] x, double[] y) {
        if (A[0].length != x.length) {
            throw new IllegalArgumentException(String.format("Array dimensions do not match for matrix multiplication: %dx%d vs %dx1", A.length, A[0].length, x.length));
        }

        if (A.length != y.length) {
            throw new IllegalArgumentException(String.format("Array dimensions do not match"));
        }

        for (int i = 0; i < y.length; i++) {
            for (int k = 0; k < A[i].length; k++) {
                y[i] += A[i][k] * x[k];
            }
        }
    }

    /**
     * Product of a matrix and a vector y = A * x + b * y according to the rules of linear algebra.
     * Number of columns in A must equal number of elements in x.
     */
    public static void axpy(double[][] A, double[] x, double[] y, double b) {
        if (A[0].length != x.length) {
            throw new IllegalArgumentException(String.format("Array dimensions do not match for matrix multiplication: %dx%d vs %dx1", A.length, A[0].length, x.length));
        }

        if (A.length != y.length) {
            throw new IllegalArgumentException(String.format("Array dimensions do not match"));
        }

        for (int i = 0; i < y.length; i++) {
            y[i] *= b;
            for (int k = 0; k < A[i].length; k++) {
                y[i] += A[i][k] * x[k];
            }
        }
    }

    /**
     * Product of a matrix and a vector y = A<sup>T</sup> * x according to the rules of linear algebra.
     * Number of elements in x must equal number of rows in A.
     */
    public static void atx(double[][] A, double[] x, double[] y) {
        if (A.length != x.length) {
            throw new IllegalArgumentException(String.format("Array dimensions do not match for matrix multiplication: %d x %d vs 1 x %d", A.length, A[0].length, x.length));
        }

        if (A[0].length != y.length) {
            throw new IllegalArgumentException(String.format("Array dimensions do not match"));
        }

        Arrays.fill(y, 0.0);
        for (int i = 0; i < y.length; i++) {
            for (int k = 0; k < x.length; k++) {
                y[i] += x[k] * A[k][i];
            }
        }
    }

    /**
     * Product of a matrix and a vector y = A<sup>T</sup> * x + y according to the rules of linear algebra.
     * Number of elements in x must equal number of rows in A.
     */
    public static void atxpy(double[][] A, double[] x, double[] y) {
        if (A.length != x.length) {
            throw new IllegalArgumentException(String.format("Array dimensions do not match for matrix multiplication: 1 x %d vs %d x %d", x.length, A.length, A[0].length));
        }

        if (A[0].length != y.length) {
            throw new IllegalArgumentException(String.format("Array dimensions do not match"));
        }

        for (int i = 0; i < y.length; i++) {
            for (int k = 0; k < x.length; k++) {
                y[i] += x[k] * A[k][i];
            }
        }
    }

    /**
     * Product of a matrix and a vector y = A<sup>T</sup> * x + b * y according to the rules of linear algebra.
     * Number of elements in x must equal number of rows in A.
     */
    public static void atxpy(double[][] A, double[] x, double[] y, double b) {
        if (A.length != x.length) {
            throw new IllegalArgumentException(String.format("Array dimensions do not match for matrix multiplication: 1 x %d vs %d x %d", x.length, A.length, A[0].length));
        }

        if (A[0].length != y.length) {
            throw new IllegalArgumentException(String.format("Array dimensions do not match"));
        }

        for (int i = 0; i < y.length; i++) {
            y[i] *= b;
            for (int k = 0; k < x.length; k++) {
                y[i] += x[k] * A[k][i];
            }
        }
    }

    /**
     * Returns x' * A * x.
     */
    public static double xax(double[][] A, double[] x) {
        if (A.length != A[0].length) {
            throw new IllegalArgumentException("The matrix is not square");
        }

        if (A.length != x.length) {
            throw new IllegalArgumentException(String.format("x' * A * x: 1 x %d vs %d x %d", x.length, A.length, A[0].length));
        }

        int n = A.length;
        double s = 0.0;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                s += A[i][j] * x[i] * x[j];
            }
        }

        return s;
    }

    /**
     * Matrix multiplication A * A' according to the rules of linear algebra.
     */
    public static double[][] aatmm(double[][] A) {
        int m = A.length;
        int n = A[0].length;
        double[][] C = new double[m][m];
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < m; j++) {
                for (int k = 0; k < n; k++) {
                    C[i][j] += A[i][k] * A[j][k];
                }
            }
        }

        return C;
    }

    /**
     * Matrix multiplication C = A * A' according to the rules of linear algebra.
     */
    public static void aatmm(double[][] A, double[][] C) {
        int m = A.length;
        int n = A[0].length;

        for (int i = 0; i < m; i++) {
            for (int j = 0; j < m; j++) {
                for (int k = 0; k < n; k++) {
                    C[i][j] += A[i][k] * A[j][k];
                }
            }
        }
    }

    /**
     * Matrix multiplication A' * A according to the rules of linear algebra.
     */
    public static double[][] atamm(double[][] A) {
        int m = A.length;
        int n = A[0].length;

        double[][] C = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                for (int k = 0; k < m; k++) {
                    C[i][j] += A[k][i] * A[k][j];
                }
            }
        }

        return C;
    }

    /**
     * Matrix multiplication C = A' * A according to the rules of linear algebra.
     */
    public static void atamm(double[][] A, double[][] C) {
        int m = A.length;
        int n = A[0].length;

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                for (int k = 0; k < m; k++) {
                    C[i][j] += A[k][i] * A[k][j];
                }
            }
        }
    }

    /**
     * Matrix multiplication A * B according to the rules of linear algebra.
     */
    public static double[][] abmm(double[][] A, double[][] B) {
        if (A[0].length != B.length) {
            throw new IllegalArgumentException(String.format("Matrix multiplication A * B: %d x %d vs %d x %d", A.length, A[0].length, B.length, B[0].length));
        }

        int m = A.length;
        int n = B[0].length;
        int l = B.length;
        double[][] C = new double[m][n];
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                for (int k = 0; k < l; k++) {
                    C[i][j] += A[i][k] * B[k][j];
                }
            }
        }

        return C;
    }

    /**
     * Matrix multiplication C = A * B according to the rules of linear algebra.
     */
    public static void abmm(double[][] A, double[][] B, double[][] C) {
        if (A[0].length != B.length) {
            throw new IllegalArgumentException(String.format("Matrix multiplication A * B: %d x %d vs %d x %d", A.length, A[0].length, B.length, B[0].length));
        }

        int m = A.length;
        int n = B[0].length;
        int l = B.length;

        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                for (int k = 0; k < l; k++) {
                    C[i][j] += A[i][k] * B[k][j];
                }
            }
        }
    }

    /**
     * Matrix multiplication A' * B according to the rules of linear algebra.
     */
    public static double[][] atbmm(double[][] A, double[][] B) {
        if (A.length != B.length) {
            throw new IllegalArgumentException(String.format("Matrix multiplication A' * B: %d x %d vs %d x %d", A.length, A[0].length, B.length, B[0].length));
        }

        int m = A[0].length;
        int n = B[0].length;
        int l = B.length;
        double[][] C = new double[m][n];
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                for (int k = 0; k < l; k++) {
                    C[i][j] += A[k][i] * B[k][j];
                }
            }
        }

        return C;
    }

    /**
     * Matrix multiplication C = A' * B according to the rules of linear algebra.
     */
    public static void atbmm(double[][] A, double[][] B, double[][] C) {
        if (A.length != B.length) {
            throw new IllegalArgumentException(String.format("Matrix multiplication A' * B: %d x %d vs %d x %d", A.length, A[0].length, B.length, B[0].length));
        }

        int m = A[0].length;
        int n = B[0].length;
        int l = B.length;

        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                for (int k = 0; k < l; k++) {
                    C[i][j] += A[k][i] * B[k][j];
                }
            }
        }
    }

    /**
     * Matrix multiplication A * B' according to the rules of linear algebra.
     */
    public static double[][] abtmm(double[][] A, double[][] B) {
        if (A[0].length != B[0].length) {
            throw new IllegalArgumentException(String.format("Matrix multiplication A * B': %d x %d vs %d x %d", A.length, A[0].length, B.length, B[0].length));
        }

        int m = A.length;
        int n = B.length;
        int l = B[0].length;
        double[][] C = new double[m][n];
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                for (int k = 0; k < l; k++) {
                    C[i][j] += A[i][k] * B[j][k];
                }
            }
        }

        return C;
    }

    /**
     * Matrix multiplication C = A * B' according to the rules of linear algebra.
     */
    public static void abtmm(double[][] A, double[][] B, double[][] C) {
        if (A[0].length != B[0].length) {
            throw new IllegalArgumentException(String.format("Matrix multiplication A * B': %d x %d vs %d x %d", A.length, A[0].length, B.length, B[0].length));
        }

        int m = A.length;
        int n = B.length;
        int l = B[0].length;

        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                for (int k = 0; k < l; k++) {
                    C[i][j] += A[i][k] * B[j][k];
                }
            }
        }
    }

    /**
     * Matrix multiplication A' * B' according to the rules of linear algebra.
     */
    public static double[][] atbtmm(double[][] A, double[][] B) {
        if (A.length != B[0].length) {
            throw new IllegalArgumentException(String.format("Matrix multiplication A' * B': %d x %d vs %d x %d", A.length, A[0].length, B.length, B[0].length));
        }

        int m = A[0].length;
        int n = B.length;
        int l = A.length;
        double[][] C = new double[m][n];
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                for (int k = 0; k < l; k++) {
                    C[i][j] += A[k][i] * B[j][k];
                }
            }
        }

        return C;
    }

    /**
     * Matrix multiplication C = A' * B' according to the rules of linear algebra.
     */
    public static void atbtmm(double[][] A, double[][] B, double[][] C) {
        if (A.length != B[0].length) {
            throw new IllegalArgumentException(String.format("Matrix multiplication A' * B': %d x %d vs %d x %d", A.length, A[0].length, B.length, B[0].length));
        }

        int m = A[0].length;
        int n = B.length;
        int l = A.length;

        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                for (int k = 0; k < l; k++) {
                    C[i][j] += A[k][i] * B[j][k];
                }
            }
        }
    }

    /**
     * Raise each element of an array to a scalar power.
     * @param x array
     * @param n scalar exponent
     * @return x<sup>n</sup>
     */
    public static double[] pow(double[] x, double n) {
        double[] array = new double[x.length];
        for (int i = 0; i < x.length; i++) {
            array[i] = Math.pow(x[i], n);
        }
        return array;
    }

    /**
     * Raise each element of a matrix to a scalar power.
     * @param x matrix
     * @param n exponent
     * @return x<sup>n</sup>
     */
    public static double[][] pow(double[][] x, double n) {
        double[][] array = new double[x.length][x[0].length];
        for (int i = 0; i < x.length; i++) {
            for (int j = 0; j < x[i].length; j++) {
                array[i][j] = Math.pow(x[i][j], n);
            }
        }
        return array;
    }

    /**
     * Find unique elements of vector.
     * @param x an integer array.
     * @return the same values as in x but with no repetitions.
     */
    public static int[] unique(int[] x) {
        HashSet<Integer> hash = new HashSet<Integer>();
        for (int i = 0; i < x.length; i++) {
            hash.add(x[i]);
        }

        int[] y = new int[hash.size()];

        Iterator<Integer> keys = hash.iterator();
        for (int i = 0; i < y.length; i++) {
            y[i] = keys.next();
        }

        return y;
    }

    /**
     * Find unique elements of vector.
     * @param x an array of strings.
     * @return the same values as in x but with no repetitions.
     */
    public static String[] unique(String[] x) {
        HashSet<String> hash = new HashSet<String>(Arrays.asList(x));

        String[] y = new String[hash.size()];

        Iterator<String> keys = hash.iterator();
        for (int i = 0; i < y.length; i++) {
            y[i] = keys.next();
        }

        return y;
    }

    
    /**
     * Sorts each variable and returns the index of values in ascending order.
     * Note that the order of original array is NOT altered.
     * 
     * @param x a set of variables to be sorted. Each row is an instance. Each
     * column is a variable.
     * @return the index of values in ascending order
     */
    public static int[][] sort(double[][] x) {
        int n = x.length;
        int p = x[0].length;
        
        double[] a = new double[n];
        int[][] index = new int[p][];
        
        for (int j = 0; j < p; j++) {
            for (int i = 0; i < n; i++) {
                a[i] = x[i][j];
            }
            index[j] = QuickSort.sort(a);
        }
        
        return index;
    }
    
    /**
     * Returns a square identity matrix of size n.
     * @return     An n-by-n matrix with ones on the diagonal and zeros elsewhere.
     */
    public static double[][] eye(int n) {
        double[][] x = new double[n][n];
        for (int i = 0; i < n; i++) {
            x[i][i] = 1.0;
        }
        return x;
    }

    /**
     * Returns an identity matrix of size m by n.
     * @param m    the number of rows.
     * @param n    the number of columns.
     * @return     an m-by-n matrix with ones on the diagonal and zeros elsewhere.
     */
    public static double[][] eye(int m, int n) {
        double[][] x = new double[m][n];
        int k = Math.min(m, n);
        for (int i = 0; i < k; i++) {
            x[i][i] = 1.0;
        }
        return x;
    }

    /**
     * Returns the matrix trace. The sum of the diagonal elements.
     */
    public static double trace(double[][] A) {
        int n = Math.min(A.length, A[0].length);

        double t = 0.0;
        for (int i = 0; i < n; i++) {
            t += A[i][i];
        }

        return t;
    }

    /**
     * Returns the matrix transpose.
     */
    public static double[][] transpose(double[][] A) {
        int m = A.length;
        int n = A[0].length;

        double[][] matrix = new double[n][m];
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                matrix[j][i] = A[i][j];
            }
        }

        return matrix;
    }

    /**
     * Returns the matrix inverse or pseudo inverse.
     * @return  matrix inverse if A is square, pseudo inverse otherwise.
     */
    public static double[][] inverse(double[][] A) {
        double[][] inv = eye(A[0].length, A.length);

        if (A.length == A[0].length) {
            LUDecomposition lu = new LUDecomposition(A, false);
            lu.solve(inv);
        } else {
            QRDecomposition qr = new QRDecomposition(A, false);
            qr.solve(inv);
        }

        return inv;
    }

    /**
     * Returns the matrix determinant
     */
    public static double det(double[][] A) {
        if (A.length != A[0].length) {
            throw new IllegalArgumentException(String.format("Matrix is not square: %d x %d", A.length, A[0].length));
        }

        LUDecomposition lu = new LUDecomposition(A, false);
        return lu.det();
    }

    /**
     * Returns the matrix rank. Note that the input matrix will be altered.
     * @return  Effective numerical rank.
     */
    public static int rank(double[][] A) {
        return SingularValueDecomposition.decompose(A).rank();
    }

    /**
     * Returns the largest eigen pair of matrix with the power iteration
     * under the assumptions A has an eigenvalue that is strictly greater
     * in magnitude than its other its other eigenvalues and the starting
     * vector has a nonzero component in the direction of an eigenvector
     * associated with the dominant eigenvalue.
     * @param v on input, it is the non-zero initial guess of the eigen vector.
     * On output, it is the eigen vector corresponding largest eigen value.
     * @return the largest eigen value.
     */
    public static double eigen(double[][] A, double[] v) {
        return EigenValueDecomposition.eigen(new Matrix(A), v);
    }

    /**
     * Returns the largest eigen pair of matrix with the power iteration
     * under the assumptions A has an eigenvalue that is strictly greater
     * in magnitude than its other its other eigenvalues and the starting
     * vector has a nonzero component in the direction of an eigenvector
     * associated with the dominant eigenvalue.
     * @param v on input, it is the non-zero initial guess of the eigen vector.
     * On output, it is the eigen vector corresponding largest eigen value.
     * @param tol the desired convergence tolerance.
     * @return the largest eigen value.
     */
    public static double eigen(double[][] A, double[] v, double tol) {
        return EigenValueDecomposition.eigen(new Matrix(A), v, tol);
    }

    /**
     * Returns the largest eigen pair of matrix with the power iteration
     * under the assumptions A has an eigenvalue that is strictly greater
     * in magnitude than its other its other eigenvalues and the starting
     * vector has a nonzero component in the direction of an eigenvector
     * associated with the dominant eigenvalue.
     * @param v on input, it is the non-zero initial guess of the eigen vector.
     * On output, it is the eigen vector corresponding largest eigen value.
     * @return the largest eigen value.
     */
    public static double eigen(IMatrix A, double[] v) {
        return EigenValueDecomposition.eigen(A, v);
    }

    /**
     * Returns the largest eigen pair of matrix with the power iteration
     * under the assumptions A has an eigenvalue that is strictly greater
     * in magnitude than its other its other eigenvalues and the starting
     * vector has a nonzero component in the direction of an eigenvector
     * associated with the dominant eigenvalue.
     * @param v on input, it is the non-zero initial guess of the eigen vector.
     * On output, it is the eigen vector corresponding largest eigen value.
     * @param tol the desired convergence tolerance.
     * @return the largest eigen value.
     */
    public static double eigen(IMatrix A, double[] v, double tol) {
        return EigenValueDecomposition.eigen(A, v, tol);
    }

    /**
     * Find k largest approximate eigen pairs of a symmetric matrix by an
     * iterative Lanczos algorithm.
     */
    public static EigenValueDecomposition eigen(double[][] A, int k) {
        return EigenValueDecomposition.decompose(new Matrix(A), k);
    }

    /**
     * Returns the eigen value decomposition of a square matrix. Note that the input
     * matrix will be altered during decomposition.
     * @param A    square matrix which will be altered after decomposition.
     */
    public static EigenValueDecomposition eigen(double[][] A) {
        return EigenValueDecomposition.decompose(A);
    }

    /**
     * Returns the eigen value decomposition of a square matrix. Note that the input
     * matrix will be altered during decomposition.
     * @param A    square matrix which will be altered after decomposition.
     * @param symmetric true if the matrix is assumed to be symmetric.
     */
    public static EigenValueDecomposition eigen(double[][] A, boolean symmetric) {
        return EigenValueDecomposition.decompose(A, symmetric);
    }

    /**
     * Returns the eigen value decomposition of a square matrix. Note that the input
     * matrix will be altered during decomposition.
     * @param A    square matrix which will be altered after decomposition.
     * @param symmetric true if the matrix is assumed to be symmetric.
     * @param onlyValues true if only compute eigenvalues; the default is to compute eigenvectors also.
     */
    public static EigenValueDecomposition eigen(double[][] A, boolean symmetric, boolean onlyValues) {
        return EigenValueDecomposition.decompose(A, symmetric, onlyValues);
    }

    /**
     * Returns the singular value decomposition. Note that the input matrix
     * will be altered after decomposition.
     */
    public static SingularValueDecomposition svd(double[][] A) {
        return SingularValueDecomposition.decompose(A);
    }

    /**
     * Solve A*x = b (exact solution if A is square, least squares
     * solution otherwise), which means the LU or QR decomposition will take
     * place in A and the results will be stored in b.
     * @return the solution, which is actually the vector b in case of exact solution.
     */
    public static double[] solve(double[][] A, double[] b) {
        if (A.length == A[0].length) {
            LUDecomposition lu = new LUDecomposition(A, true);
            lu.solve(b);
            return b;
        } else {
            double[] x = new double[A[0].length];
            QRDecomposition qr = new QRDecomposition(A, false);
            qr.solve(b, x);
            return x;
        }
    }

    /**
     * Solve A*X = B (exact solution if A is square, least squares
     * solution otherwise), which means the LU or QR decomposition will take
     * place in A and the results will be stored in B.
     * @return the solution, which is actually the matrix B in case of exact solution.
     */
    public static double[][] solve(double[][] A, double[][] B) {
        if (A.length == A[0].length) {
            LUDecomposition lu = new LUDecomposition(A, true);
            lu.solve(B);
            return B;
        } else {
            double[][] X = new double[A[0].length][B[0].length];
            QRDecomposition qr = new QRDecomposition(A, false);
            qr.solve(B, X);
            return X;
        }
    }

    /**
     * Solve the tridiagonal linear set which is of diagonal dominance
     * |b<sub>i</sub>| &gt; |a<sub>i</sub>| + |c<sub>i</sub>|.
     * <pre>
     * | b0 c0  0  0  0 ...                        |
     * | a1 b1 c1  0  0 ...                        |
     * |  0 a2 b2 c2  0 ...                        |
     * |                ...                        |
     * |                ... a(n-2)  b(n-2)  c(n-2) |
     * |                ... 0       a(n-1)  b(n-1) |
     * </pre>
     * @param a the lower part of tridiagonal matrix. a[0] is undefined and not
     * referenced by the method.
     * @param b the diagonal of tridiagonal matrix.
     * @param c the upper of tridiagonal matrix. c[n-1] is undefined and not
     * referenced by the method.
     * @param r the right-hand side of linear equations.
     * @return the solution.
     */
    public static double[] solve(double[] a, double[] b, double[] c, double[] r) {
        if (b[0] == 0.0) {
            throw new IllegalArgumentException("Invalid value of b[0] == 0. The equations should be rewritten as a set of order n - 1.");
        }

        int n = a.length;
        double[] u = new double[n];
        double[] gam = new double[n];

        double bet = b[0];
        u[0] = r[0] / bet;

        for (int j = 1; j < n; j++) {
            gam[j] = c[j - 1] / bet;
            bet = b[j] - a[j] * gam[j];
            if (bet == 0.0) {
                throw new IllegalArgumentException("The tridagonal matrix is not of diagonal dominance.");
            }
            u[j] = (r[j] - a[j] * u[j - 1]) / bet;
        }

        for (int j = (n - 2); j >= 0; j--) {
            u[j] -= gam[j + 1] * u[j + 1];
        }

        return u;
    }

    /**
     * Solves A * x = b by iterative biconjugate gradient method.
     * @param A the matrix supporting matrix vector multiplication operation.
     * @param b the right hand side of linear equations.
     * @param x on input, x should be set to an initial guess of the solution
     * (or all zeros). On output, x is reset to the improved solution.
     * @return the estimated error.
     */
    public static double solve(IMatrix A, double[] b, double[] x) {
        return solve(A, A, b, x);
    }

    /**
     * Solves A * x = b by iterative biconjugate gradient method.
     * @param A the matrix supporting matrix vector multiplication operation.
     * @param Ap the preconditioned matrix of A.
     * @param b the right hand side of linear equations.
     * @param x on input, x should be set to an initial guess of the solution
     * (or all zeros). On output, x is reset to the improved solution.
     * @return the estimated error.
     */
    public static double solve(IMatrix A, IMatrix Ap, double[] b, double[] x) {
        return solve(A, Ap, b, x, 1E-10);
    }

    /**
     * Solves A * x = b by iterative biconjugate gradient method.
     * @param A the matrix supporting matrix vector multiplication operation.
     * @param b the right hand side of linear equations.
     * @param x on input, x should be set to an initial guess of the solution
     * (or all zeros). On output, x is reset to the improved solution.
     * @param tol the desired convergence tolerance.
     * @return the estimated error.
     */
    public static double solve(IMatrix A, double[] b, double[] x, double tol) {
        return solve(A, A, b, x, tol);
    }

    /**
     * Solves A * x = b by iterative biconjugate gradient method.
     * @param A the matrix supporting matrix vector multiplication operation.
     * @param Ap the preconditioned matrix of A.
     * @param b the right hand side of linear equations.
     * @param x on input, x should be set to an initial guess of the solution
     * (or all zeros). On output, x is reset to the improved solution.
     * @param tol the desired convergence tolerance.
     * @return the estimated error.
     */
    public static double solve(IMatrix A, IMatrix Ap, double[] b, double[] x, double tol) {
        return solve(A, Ap, b, x, tol, 1);
    }

    /**
     * Solves A * x = b by iterative biconjugate gradient method.
     * @param A the matrix supporting matrix vector multiplication operation.
     * @param b the right hand side of linear equations.
     * @param x on input, x should be set to an initial guess of the solution
     * (or all zeros). On output, x is reset to the improved solution.
     * @param itol specify which convergence test is applied. If itol = 1,
     * iteration stops when |Ax - b| / |b| is less than the parameter tolerance.
     * If itol = 2, the stop criterion is
     * |A<sup>-1</sup> (Ax - b)| / |A<sup>-1</sup>b| is less than tolerance.
     * If tol = 3, |x<sub>k+1</sub> - x<sub>k</sub>|<sub>2</sub> is less than
     * tolerance. The setting of tol = 4 is same as tol = 3 except that the
     * L<sub>&infin;</sub> norm instead of L<sub>2</sub>.
     * @param tol the desired convergence tolerance.
     * @return the estimated error.
     */
    public static double solve(IMatrix A, double[] b, double[] x, double tol, int itol) {
        return solve(A, A, b, x, tol, itol);
    }

    /**
     * Solves A * x = b by iterative biconjugate gradient method.
     * @param A the matrix supporting matrix vector multiplication operation.
     * @param Ap the preconditioned matrix of A.
     * @param b the right hand side of linear equations.
     * @param x on input, x should be set to an initial guess of the solution
     * (or all zeros). On output, x is reset to the improved solution.
     * @param itol specify which convergence test is applied. If itol = 1,
     * iteration stops when |Ax - b| / |b| is less than the parameter tolerance.
     * If itol = 2, the stop criterion is
     * |A<sup>-1</sup> (Ax - b)| / |A<sup>-1</sup>b| is less than tolerance.
     * If tol = 3, |x<sub>k+1</sub> - x<sub>k</sub>|<sub>2</sub> is less than
     * tolerance. The setting of tol = 4 is same as tol = 3 except that the
     * L<sub>&infin;</sub> norm instead of L<sub>2</sub>.
     * @param tol the desired convergence tolerance.
     * @return the estimated error.
     */
    public static double solve(IMatrix A, IMatrix Ap, double[] b, double[] x, double tol, int itol) {
        return solve(A, Ap, b, x, tol, itol, 2 * Math.max(A.nrows(), A.ncols()));
    }

    /**
     * Solves A * x = b by iterative biconjugate gradient method.
     * This method can be called repeatedly, with maxIter &lt; n, to monitor how
     * error decreases.
     * @param A the matrix supporting matrix vector multiplication operation.
     * @param b the right hand side of linear equations.
     * @param x on input, x should be set to an initial guess of the solution
     * (or all zeros). On output, x is reset to the improved solution.
     * @param itol specify which convergence test is applied. If itol = 1,
     * iteration stops when |Ax - b| / |b| is less than the parameter tolerance.
     * If itol = 2, the stop criterion is
     * |A<sup>-1</sup> (Ax - b)| / |A<sup>-1</sup>b| is less than tolerance.
     * If tol = 3, |x<sub>k+1</sub> - x<sub>k</sub>|<sub>2</sub> is less than
     * tolerance. The setting of tol = 4 is same as tol = 3 except that the
     * L<sub>&infin;</sub> norm instead of L<sub>2</sub>.
     * @param tol the desired convergence tolerance.
     * @param maxIter the maximum number of allowed iterations.
     * @return the estimated error.
     */
    public static double solve(IMatrix A, double[] b, double[] x, double tol, int itol, int maxIter) {
        return solve(A, A, b, x, tol, itol, maxIter);
    }
    
    /**
     * Solves A * x = b by iterative biconjugate gradient method.
     * This method can be called repeatedly, with maxIter &lt; n, to monitor how
     * error decreases.
     * @param A the matrix supporting matrix vector multiplication operation.
     * @param Ap the preconditioned matrix of A.
     * @param b the right hand side of linear equations.
     * @param x on input, x should be set to an initial guess of the solution
     * (or all zeros). On output, x is reset to the improved solution.
     * @param itol specify which convergence test is applied. If itol = 1,
     * iteration stops when |Ax - b| / |b| is less than the parameter tolerance.
     * If itol = 2, the stop criterion is
     * |A<sup>-1</sup> (Ax - b)| / |A<sup>-1</sup>b| is less than tolerance.
     * If tol = 3, |x<sub>k+1</sub> - x<sub>k</sub>|<sub>2</sub> is less than
     * tolerance. The setting of tol = 4 is same as tol = 3 except that the
     * L<sub>&infin;</sub> norm instead of L<sub>2</sub>.
     * @param tol the desired convergence tolerance.
     * @param maxIter the maximum number of allowed iterations.
     * @return the estimated error.
     */
    public static double solve(IMatrix A, IMatrix Ap, double[] b, double[] x, double tol, int itol, int maxIter) {
        if (tol <= 0.0) {
            throw new IllegalArgumentException("Invalid tolerance: " + tol);            
        }
        
        if (maxIter <= 0) {
            throw new IllegalArgumentException("Invalid maximum number of iterations: " + maxIter);            
        }
        
        if (itol < 1 || itol > 4) {
            throw new IllegalArgumentException(String.format("Illegal itol: %d", itol));
        }

        double err = 0.0;
        double ak, akden, bk, bkden = 1.0, bknum, bnrm, dxnrm, xnrm, zm1nrm, znrm = 0.0;
        int j, n = b.length;

        double[] p = new double[n];
        double[] pp = new double[n];
        double[] r = new double[n];
        double[] rr = new double[n];
        double[] z = new double[n];
        double[] zz = new double[n];

        A.ax(x, r);
        for (j = 0; j < n; j++) {
            r[j] = b[j] - r[j];
            rr[j] = r[j];
        }

        if (itol == 1) {
            bnrm = snorm(b, itol);
            Ap.asolve(r, z);
        } else if (itol == 2) {
            Ap.asolve(b, z);
            bnrm = snorm(z, itol);
            Ap.asolve(r, z);
        } else if (itol == 3 || itol == 4) {
            Ap.asolve(b, z);
            bnrm = snorm(z, itol);
            Ap.asolve(r, z);
            znrm = snorm(z, itol);
        } else {
            throw new IllegalArgumentException(String.format("Illegal itol: %d", itol));
        }

        for (int iter = 1; iter <= maxIter; iter++) {
            Ap.asolve(rr, zz);
            for (bknum = 0.0, j = 0; j < n; j++) {
                bknum += z[j] * rr[j];
            }
            if (iter == 1) {
                for (j = 0; j < n; j++) {
                    p[j] = z[j];
                    pp[j] = zz[j];
                }
            } else {
                bk = bknum / bkden;
                for (j = 0; j < n; j++) {
                    p[j] = bk * p[j] + z[j];
                    pp[j] = bk * pp[j] + zz[j];
                }
            }
            bkden = bknum;
            A.ax(p, z);
            for (akden = 0.0, j = 0; j < n; j++) {
                akden += z[j] * pp[j];
            }
            ak = bknum / akden;
            A.atx(pp, zz);
            for (j = 0; j < n; j++) {
                x[j] += ak * p[j];
                r[j] -= ak * z[j];
                rr[j] -= ak * zz[j];
            }
            Ap.asolve(r, z);
            if (itol == 1) {
                err = snorm(r, itol) / bnrm;
            } else if (itol == 2) {
                err = snorm(z, itol) / bnrm;
            } else if (itol == 3 || itol == 4) {
                zm1nrm = znrm;
                znrm = snorm(z, itol);
                if (Math.abs(zm1nrm - znrm) > EPSILON * znrm) {
                    dxnrm = Math.abs(ak) * snorm(p, itol);
                    err = znrm / Math.abs(zm1nrm - znrm) * dxnrm;
                } else {
                    err = znrm / bnrm;
                    continue;
                }
                xnrm = snorm(x, itol);
                if (err <= 0.5 * xnrm) {
                    err /= xnrm;
                } else {
                    err = znrm / bnrm;
                    continue;
                }
            }

            if (iter % 10 == 0) {
                System.out.format("BCG: the error after %3d iterations: %.5g\n", iter, err);
            }

            if (err <= tol) {
                System.out.format("BCG: the error after %3d iterations: %.5g\n", iter, err);
                break;
            }
        }

        return err;
    }

    /**
     * Compute L2 or L-infinity norms for a vector x, as signaled by itol.
     */
    private static double snorm(double[] x, int itol) {
        int n = x.length;

        if (itol <= 3) {
            double ans = 0.0;
            for (int i = 0; i < n; i++) {
                ans += x[i] * x[i];
            }
            return Math.sqrt(ans);
        } else {
            int isamax = 0;
            for (int i = 0; i < n; i++) {
                if (Math.abs(x[i]) > Math.abs(x[isamax])) {
                    isamax = i;
                }
            }

            return Math.abs(x[isamax]);
        }
    }

    /**
     * Returns the root of a function known to lie between x1 and x2 by
     * Brent's method. The root will be refined until its accuracy is tol.
     * The method is guaranteed to converge as long as the function can be
     * evaluated within the initial interval known to contain a root.
     * @param func the function to be evaluated.
     * @param x1 the left end of search interval.
     * @param x2 the right end of search interval.
     * @param tol the accuracy tolerance.
     * @return the root.
     */
    public static double root(Function func, double x1, double x2, double tol) {
        return root(func, x1, x2, tol, 100);
    }

    /**
     * Returns the root of a function known to lie between x1 and x2 by
     * Brent's method. The root will be refined until its accuracy is tol.
     * The method is guaranteed to converge as long as the function can be
     * evaluated within the initial interval known to contain a root.
     * @param func the function to be evaluated.
     * @param x1 the left end of search interval.
     * @param x2 the right end of search interval.
     * @param tol the accuracy tolerance.
     * @param maxIter the maximum number of allowed iterations.
     * @return the root.
     */
    public static double root(Function func, double x1, double x2, double tol, int maxIter) {
        if (tol <= 0.0) {
            throw new IllegalArgumentException("Invalid tolerance: " + tol);            
        }
        
        if (maxIter <= 0) {
            throw new IllegalArgumentException("Invalid maximum number of iterations: " + maxIter);            
        }
        
        double a = x1, b = x2, c = x2, d = 0, e = 0, fa = func.f(a), fb = func.f(b), fc, p, q, r, s, xm;
        if ((fa > 0.0 && fb > 0.0) || (fa < 0.0 && fb < 0.0)) {
            throw new IllegalArgumentException("Root must be bracketed.");
        }

        fc = fb;
        for (int iter = 1; iter <= maxIter; iter++) {
            if ((fb > 0.0 && fc > 0.0) || (fb < 0.0 && fc < 0.0)) {
                c = a;
                fc = fa;
                e = d = b - a;
            }

            if (abs(fc) < abs(fb)) {
                a = b;
                b = c;
                c = a;
                fa = fb;
                fb = fc;
                fc = fa;
            }

            tol = 2.0 * EPSILON * abs(b) + 0.5 * tol;
            xm = 0.5 * (c - b);

            if (iter % 10 == 0) {
                System.out.format("Brent: the root after %3d iterations: %.5g, error = %.5g\n", iter, b, xm);
            }

            if (abs(xm) <= tol || fb == 0.0) {
                System.out.format("Brent: the root after %3d iterations: %.5g, error = %.5g\n", iter, b, xm);
                return b;
            }

            if (abs(e) >= tol && abs(fa) > abs(fb)) {
                s = fb / fa;
                if (a == c) {
                    p = 2.0 * xm * s;
                    q = 1.0 - s;
                } else {
                    q = fa / fc;
                    r = fb / fc;
                    p = s * (2.0 * xm * q * (q - r) - (b - a) * (r - 1.0));
                    q = (q - 1.0) * (r - 1.0) * (s - 1.0);
                }

                if (p > 0.0) {
                    q = -q;
                }

                p = abs(p);
                double min1 = 3.0 * xm * q - abs(tol * q);
                double min2 = abs(e * q);
                if (2.0 * p < (min1 < min2 ? min1 : min2)) {
                    e = d;
                    d = p / q;
                } else {
                    d = xm;
                    e = d;
                }
            } else {
                d = xm;
                e = d;
            }

            a = b;
            fa = fb;
            if (abs(d) > tol) {
                b += d;
            } else {
                b += copySign(tol, xm);
            }
            fb = func.f(b);
        }

        System.err.println("Brent's method exceeded the maximum number of iterations.");
        return b;
    }

    /**
     * Returns the root of a function whose derivative is available known
     * to lie between x1 and x2 by Newton-Raphson method. The root will be
     * refined until its accuracy is within xacc.
     * @param func the function to be evaluated.
     * @param x1 the left end of search interval.
     * @param x2 the right end of search interval.
     * @param tol the accuracy tolerance.
     * @return the root.
     */
    public static double root(DifferentiableFunction func, double x1, double x2, double tol) {
        return root(func, x1, x2, tol, 100);
    }

    /**
     * Returns the root of a function whose derivative is available known
     * to lie between x1 and x2 by Newton-Raphson method. The root will be
     * refined until its accuracy is within xacc.
     * @param func the function to be evaluated.
     * @param x1 the left end of search interval.
     * @param x2 the right end of search interval.
     * @param tol the accuracy tolerance.
     * @param maxIter the maximum number of allowed iterations.
     * @return the root.
     */
    public static double root(DifferentiableFunction func, double x1, double x2, double tol, int maxIter) {
        if (tol <= 0.0) {
            throw new IllegalArgumentException("Invalid tolerance: " + tol);            
        }
        
        if (maxIter <= 0) {
            throw new IllegalArgumentException("Invalid maximum number of iterations: " + maxIter);            
        }
        
        double fl = func.f(x1);
        double fh = func.f(x2);
        if ((fl > 0.0 && fh > 0.0) || (fl < 0.0 && fh < 0.0)) {
            throw new IllegalArgumentException("Root must be bracketed in rtsafe");
        }

        if (fl == 0.0) {
            return x1;
        }
        if (fh == 0.0) {
            return x2;
        }

        double xh, xl;
        if (fl < 0.0) {
            xl = x1;
            xh = x2;
        } else {
            xh = x1;
            xl = x2;
        }
        double rts = 0.5 * (x1 + x2);
        double dxold = abs(x2 - x1);
        double dx = dxold;
        double f = func.f(rts);
        double df = func.df(rts);
        for (int iter = 1; iter <= maxIter; iter++) {
            if ((((rts - xh) * df - f) * ((rts - xl) * df - f) > 0.0) || (abs(2.0 * f) > abs(dxold * df))) {
                dxold = dx;
                dx = 0.5 * (xh - xl);
                rts = xl + dx;
                if (xl == rts) {
                    System.out.format("Newton-Raphson: the root after %3d iterations: %.5g, error = %.5g\n", iter, rts, dx);
                    return rts;
                }
            } else {
                dxold = dx;
                dx = f / df;
                double temp = rts;
                rts -= dx;
                if (temp == rts) {
                    System.out.format("Newton-Raphson: the root after %3d iterations: %.5g, error = %.5g\n", iter, rts, dx);
                    return rts;
                }
            }

            if (iter % 10 == 0) {
                System.out.format("Newton-Raphson: the root after %3d iterations: %.5g, error = %.5g\n", iter, rts, dx);
            }

            if (abs(dx) < tol) {
                System.out.format("Newton-Raphson: the root after %3d iterations: %.5g, error = %.5g\n", iter, rts, dx);
                return rts;
            }

            f = func.f(rts);
            df = func.df(rts);
            if (f < 0.0) {
                xl = rts;
            } else {
                xh = rts;
            }
        }

        System.err.println("Newton-Raphson method exceeded the maximum number of iterations.");
        return rts;
    }

    /**
     * Minimize a function along a search direction by find a step which satisfies
     * a sufficient decrease condition and a curvature condition.
     * <p>
     * At each stage this function updates an interval of uncertainty with
     * endpoints <code>stx</code> and <code>sty</code>. The interval of
     * uncertainty is initially chosen so that it contains a
     * minimizer of the modified function
     * <pre>
     *      f(x+stp*s) - f(x) - ftol*stp*(gradf(x)'s).
     * </pre>
     * If a step is obtained for which the modified function
     * has a nonpositive function value and nonnegative derivative,
     * then the interval of uncertainty is chosen so that it
     * contains a minimizer of <code>f(x+stp*s)</code>.
     * <p>
     * The algorithm is designed to find a step which satisfies
     * the sufficient decrease condition
     * <pre>
     *       f(x+stp*s) &lt;= f(X) + ftol*stp*(gradf(x)'s),
     * </pre>
     * and the curvature condition
     * <pre>
     *       abs(gradf(x+stp*s)'s)) &lt;= gtol*abs(gradf(x)'s).
     * </pre>
     * If <code>ftol</code> is less than <code>gtol</code> and if, for example,
     * the function is bounded below, then there is always a step which
     * satisfies both conditions. If no step can be found which satisfies both
     * conditions, then the algorithm usually stops when rounding
     * errors prevent further progress. In this case <code>stp</code> only
     * satisfies the sufficient decrease condition.
     * <p>
     *
     * @param xold on input this contains the base point for the line search.
     *
     * @param fold on input this contains the value of the objective function
     *		at <code>x</code>.
     *
     * @param g on input this contains the gradient of the objective function
     *		at <code>x</code>.
     *
     * @param p the search direction.
     *
     * @param x on output, it contains <code>xold + alam*p</code>.
     *
     * @param stpmax specify upper bound for the step in the line search so that
     *          we do not try to evaluate the function in regions where it is
     *          undefined or subject to overflow.
     *
     * @return the new function value.
     */
    static double linesearch(MultivariateFunction func, double[] xold, double fold, double[] g, double[] p, double[] x, double stpmax) {
        if (stpmax <= 0) {
            throw new IllegalArgumentException("Invalid upper bound of linear search step: " + stpmax);
        }

        // Termination occurs when the relative width of the interval
        // of uncertainty is at most xtol.
        final double xtol = EPSILON;
        // Tolerance for the sufficient decrease condition.
        final double ftol = 1.0E-4;

        int n = xold.length;

        // Scale if attempted step is too big
        double pnorm = norm(p);
        if (pnorm > stpmax) {
            double r = stpmax / pnorm;
            for (int i = 0; i < n; i++) {
                p[i] *= r;
            }
        }

        // Check if s is a descent direction.
        double slope = 0.0;
        for (int i = 0; i < n; i++) {
            slope += g[i] * p[i];
        }

        if (slope >= 0) {
            throw new IllegalArgumentException("Line Search: the search direction is not a descent direction, which may be caused by roundoff problem.");
        }

        // Calculate minimum step.
        double test = 0.0;
        for (int i = 0; i < n; i++) {
            double temp = abs(p[i]) / max(xold[i], 1.0);
            if (temp > test) {
                test = temp;
            }
        }

        double alammin = xtol / test;
        double alam = 1.0;

        double alam2 = 0.0, f2 = 0.0;
        double a, b, disc, rhs1, rhs2, tmpalam;
        while (true) {
            // Evaluate the function and gradient at stp
            // and compute the directional derivative.
            for (int i = 0; i < n; i++) {
                x[i] = xold[i] + alam * p[i];
            }

            double f = func.f(x);

            // Convergence on &Delta; x.
            if (alam < alammin) {
                System.arraycopy(xold, 0, x, 0, n);
                return f;
            } else if (f <= fold + ftol * alam * slope) {
                // Sufficient function decrease.
                return f;
            } else {
                // Backtrack
                if (alam == 1.0) {
                    // First time
                    tmpalam = -slope / (2.0 * (f - fold - slope));
                } else {
                    // Subsequent backtracks.
                    rhs1 = f - fold - alam * slope;
                    rhs2 = f2 - fold - alam2 * slope;
                    a = (rhs1 / (alam * alam) - rhs2 / (alam2 * alam2)) / (alam - alam2);
                    b = (-alam2 * rhs1 / (alam * alam) + alam * rhs2 / (alam2 * alam2)) / (alam - alam2);
                    if (a == 0.0) {
                        tmpalam = -slope / (2.0 * b);
                    } else {
                        disc = b * b - 3.0 * a * slope;
                        if (disc < 0.0) {
                            tmpalam = 0.5 * alam;
                        } else if (b <= 0.0) {
                            tmpalam = (-b + sqrt(disc)) / (3.0 * a);
                        } else {
                            tmpalam = -slope / (b + sqrt(disc));
                        }
                    }
                    if (tmpalam > 0.5 * alam) {
                        tmpalam = 0.5 * alam;
                    }
                }
            }
            alam2 = alam;
            f2 = f;
            alam = max(tmpalam, 0.1 * alam);
        }
    }

    /**
     * This method solves the unconstrained minimization problem
     * <pre>
     *     min f(x),    x = (x1,x2,...,x_n),
     * </pre>
     * using the limited-memory BFGS method. The method is especially
     * effective on problems involving a large number of variables. In
     * a typical iteration of this method an approximation <code>Hk</code> to the
     * inverse of the Hessian is obtained by applying <code>m</code> BFGS updates to
     * a diagonal matrix <code>Hk0</code>, using information from the previous M steps.
     * The user specifies the number <code>m</code>, which determines the amount of
     * storage required by the routine. The algorithm is described in "On the
     * limited memory BFGS method for large scale optimization", by D. Liu and J. Nocedal,
     * Mathematical Programming B 45 (1989) 503-528.
     *
     * @param func the function to be minimized.
     *
     * @param m the number of corrections used in the L-BFGS update.
     *		Values of <code>m</code> less than 3 are not recommended;
     *		large values of <code>m</code> will result in excessive
     *		computing time. <code>3 &lt;= m &lt;= 7</code> is recommended.
     *
     * @param x on initial entry this must be set by the user to the values
     *		of the initial estimate of the solution vector. On exit with
     *		<code>iflag = 0</code>, it contains the values of the variables
     *		at the best point found (usually a solution).
     *
     * @param gtol the convergence requirement on zeroing the gradient.
     *
     * @return the minimum value of the function.
     */
    public static double min(DifferentiableMultivariateFunction func, int m, double[] x, double gtol) {
        return min(func, m, x, gtol, 200);
    }

    /**
     * This method solves the unconstrained minimization problem
     * <pre>
     *     min f(x),    x = (x1,x2,...,x_n),
     * </pre>
     * using the limited-memory BFGS method. The method is especially
     * effective on problems involving a large number of variables. In
     * a typical iteration of this method an approximation <code>Hk</code> to the
     * inverse of the Hessian is obtained by applying <code>m</code> BFGS updates to
     * a diagonal matrix <code>Hk0</code>, using information from the previous M steps.
     * The user specifies the number <code>m</code>, which determines the amount of
     * storage required by the routine. The algorithm is described in "On the
     * limited memory BFGS method for large scale optimization", by D. Liu and J. Nocedal,
     * Mathematical Programming B 45 (1989) 503-528.
     *
     * @param func the function to be minimized.
     *
     * @param m the number of corrections used in the L-BFGS update.
     *		Values of <code>m</code> less than 3 are not recommended;
     *		large values of <code>m</code> will result in excessive
     *		computing time. <code>3 &lt;= m &lt;= 7</code> is recommended.
     *          A common choice for m is m = 5.
     *
     * @param x on initial entry this must be set by the user to the values
     *		of the initial estimate of the solution vector. On exit with
     *		<code>iflag = 0</code>, it contains the values of the variables
     *		at the best point found (usually a solution).
     *
     * @param gtol the convergence requirement on zeroing the gradient.
     *
     * @param maxIter the maximum allowed number of iterations.
     *
     * @return the minimum value of the function.
     */
    public static double min(DifferentiableMultivariateFunction func, int m, double[] x, double gtol, int maxIter) {
        // Initialize.
        if (m <= 0) {
            throw new IllegalArgumentException("Invalid m: " + m);
        }

        // The convergence criterion on x values.
        final double TOLX = 4 * EPSILON;
        // The scaled maximum step length allowed in line searches.
        final double STPMX = 100.0;

        int n = x.length;

        // The solution vector of line search.
        double[] xnew = new double[n];
        // The gradient vector of line search.
        double[] gnew = new double[n];
        // Line search direction.
        double[] xi = new double[n];

        // difference of x from previous step.
        double[][] s = new double[m][n];
        // difference of g from previous step.
        double[][] y = new double[m][n];

        // buffer for 1 / (y' * s)
        double[] rho = new double[m];
        double[] a = new double[m];

        // Diagonal of initial H0.
        double diag = 1.0;

        // Current gradient.
        double[] g = new double[n];
        // Current function value.
        double f = func.f(x, g);

        System.out.format("L-BFGS: initial function value: %.5g\n", f);

        double sum = 0.0;
        // Initial line search direction.
        for (int i = 0; i < n; i++) {
            xi[i] = -g[i];
            sum += x[i] * x[i];
        }

        // Upper limit for line search step.
        double stpmax = STPMX * max(sqrt(sum), n);

        for (int iter = 1, k = 0; iter <= maxIter; iter++) {
            linesearch(func, x, f, g, xi, xnew, stpmax);

            f = func.f(xnew, gnew);
            for (int i = 0; i < n; i++) {
                s[k][i] = xnew[i] - x[i];
                y[k][i] = gnew[i] - g[i];
                x[i] = xnew[i];
                g[i] = gnew[i];
            }

            // Test for convergence on x.
            double test = 0.0;
            for (int i = 0; i < n; i++) {
                double temp = abs(s[k][i]) / max(abs(x[i]), 1.0);
                if (temp > test) {
                    test = temp;
                }
            }

            if (test < TOLX) {
                System.out.format("L-BFGS: the function value after %3d iterations: %.5g\n", iter, f);
                return f;
            }

            // Test for convergence on zero gradient.
            test = 0.0;
            double den = max(f, 1.0);

            for (int i = 0; i < n; i++) {
                double temp = abs(g[i]) * max(abs(x[i]), 1.0) / den;
                if (temp > test) {
                    test = temp;
                }
            }

            if (test < gtol) {
                System.out.format("L-BFGS: the function value after %3d iterations: %.5g\n", iter, f);
                return f;
            }

            if (iter % 10 == 0) {
                System.out.format("L-BFGS: the function value after %3d iterations: %.5g\n", iter, f);
            }

            double ys = dot(y[k], s[k]);
            double yy = dot(y[k], y[k]);

            diag = ys / yy;

            rho[k] = 1.0 / ys;

            for (int i = 0; i < n; i++) {
                xi[i] = -g[i];
            }

            int cp = k;
            int bound = iter > m ? m : iter;
            for (int i = 0; i < bound; i++) {
                a[cp] = rho[cp] * dot(s[cp], xi);
                axpy(-a[cp], y[cp], xi);
                if (--cp == - 1) {
                    cp = m - 1;
                }
            }

            for (int i = 0; i < n; i++) {
                xi[i] *= diag;
            }

            for (int i = 0; i < bound; i++) {
                if (++cp == m) {
                    cp = 0;
                }
                double b = rho[cp] * dot(y[cp], xi);
                axpy(a[cp]-b, s[cp], xi);
            }

            if (++k == m) {
                k = 0;
            }
        }

        throw new IllegalStateException("L-BFGS: Too many iterations.");
    }

    /**
     * This method solves the unconstrained minimization problem
     * <pre>
     *     min f(x),    x = (x1,x2,...,x_n),
     * </pre>
     * using the BFGS method.
     *
     * @param func the function to be minimized.
     *
     * @param x on initial entry this must be set by the user to the values
     *		of the initial estimate of the solution vector. On exit, it
     *          contains the values of the variables at the best point found
     *          (usually a solution).
     *
     * @param gtol the convergence requirement on zeroing the gradient.
     *
     * @return the minimum value of the function.
     */
    public static double min(DifferentiableMultivariateFunction func, double[] x, double gtol) {
        return min(func, x, gtol, 200);
    }

    /**
     * This method solves the unconstrained minimization problem
     * <pre>
     *     min f(x),    x = (x1,x2,...,x_n),
     * </pre>
     * using the BFGS method.
     *
     * @param func the function to be minimized.
     *
     * @param x on initial entry this must be set by the user to the values
     *		of the initial estimate of the solution vector. On exit, it
     *          contains the values of the variables at the best point found
     *          (usually a solution).
     *
     * @param gtol the convergence requirement on zeroing the gradient.
     *
     * @param maxIter the maximum allowed number of iterations.
     *
     * @return the minimum value of the function.
     */
    public static double min(DifferentiableMultivariateFunction func, double[] x, double gtol, int maxIter) {
        // The convergence criterion on x values.
        final double TOLX = 4 * EPSILON;
        // The scaled maximum step length allowed in line searches.
        final double STPMX = 100.0;

        double den, fac, fad, fae, sumdg, sumxi, temp, test;

        int n = x.length;
        double[] dg = new double[n];
        double[] g = new double[n];
        double[] hdg = new double[n];
        double[] xnew = new double[n];
        double[] xi = new double[n];
        double[][] hessin = new double[n][n];

        // Calculate starting function value and gradient and initialize the
        // inverse Hessian to the unit matrix.
        double f = func.f(x, g);
        System.out.format("BFGS: initial function value: %.5g\n", f);

        double sum = 0.0;
        for (int i = 0; i < n; i++) {
            hessin[i][i] = 1.0;
            // Initialize line direction.
            xi[i] = -g[i];
            sum += x[i] * x[i];
        }

        double stpmax = STPMX * max(sqrt(sum), n);

        for (int iter = 1; iter <= maxIter; iter++) {
            // The new function evaluation occurs in line search.
            f = linesearch(func, x, f, g, xi, xnew, stpmax);

            if (iter % 10 == 0) {
                System.out.format("BFGS: the function value after %3d iterations: %.5g\n", iter, f);
            }

            // update the line direction and current point.
            for (int i = 0; i < n; i++) {
                xi[i] = xnew[i] - x[i];
                x[i] = xnew[i];
            }

            // Test for convergence on x.
            test = 0.0;
            for (int i = 0; i < n; i++) {
                temp = abs(xi[i]) / max(abs(x[i]), 1.0);
                if (temp > test) {
                    test = temp;
                }
            }

            if (test < TOLX) {
                System.out.format("BFGS: the function value after %3d iterations: %.5g\n", iter, f);
                return f;
            }
            
            System.arraycopy(g, 0, dg, 0, n);

            func.f(x, g);

            // Test for convergence on zero gradient.
            den = max(f, 1.0);
            test = 0.0;
            for (int i = 0; i < n; i++) {
                temp = abs(g[i]) * max(abs(x[i]), 1.0) / den;
                if (temp > test) {
                    test = temp;
                }
            }

            if (test < gtol) {
                System.out.format("BFGS: the function value after %3d iterations: %.5g\n", iter, f);
                return f;
            }

            for (int i = 0; i < n; i++) {
                dg[i] = g[i] - dg[i];
            }

            for (int i = 0; i < n; i++) {
                hdg[i] = 0.0;
                for (int j = 0; j < n; j++) {
                    hdg[i] += hessin[i][j] * dg[j];
                }
            }

            fac = fae = sumdg = sumxi = 0.0;
            for (int i = 0; i < n; i++) {
                fac += dg[i] * xi[i];
                fae += dg[i] * hdg[i];
                sumdg += dg[i] * dg[i];
                sumxi += xi[i] * xi[i];
            }

            // Skip upudate if fac is not sufficiently positive.
            if (fac > sqrt(EPSILON * sumdg * sumxi)) {
                fac = 1.0 / fac;
                fad = 1.0 / fae;

                // The vector that makes BFGS different from DFP.
                for (int i = 0; i < n; i++) {
                    dg[i] = fac * xi[i] - fad * hdg[i];
                }

                // BFGS updating formula.
                for (int i = 0; i < n; i++) {
                    for (int j = i; j < n; j++) {
                        hessin[i][j] += fac * xi[i] * xi[j] - fad * hdg[i] * hdg[j] + fae * dg[i] * dg[j];
                        hessin[j][i] = hessin[i][j];
                    }
                }
            }

            // Calculate the next direction to go.
            Arrays.fill(xi, 0.0);
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    xi[i] -= hessin[i][j] * g[j];
                }
            }
        }

        throw new IllegalStateException("BFGS: Too many iterations.");
    }
}
