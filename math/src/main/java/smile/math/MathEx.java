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

package smile.math;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

import smile.sort.QuickSelect;
import smile.sort.QuickSort;
import smile.sort.Sort;
import smile.util.IntPair;
import smile.util.SparseArray;

import static java.lang.Math.abs;
import static java.lang.Math.exp;
import static java.lang.Math.floor;
import static java.lang.Math.sqrt;

/**
 * Extra basic numeric functions. The following functions are
 * included:
 * <ul>
 * <li> scalar functions: sqr, factorial, lfactorial, choose, lchoose, log2 are
 * provided.
 * <li> vector functions: min, max, mean, sum, var, sd, cov, L<sub>1</sub> norm,
 * L<sub>2</sub> norm, L<sub>&infin;</sub> norm, normalize, unitize, cor, Spearman
 * correlation, Kendall correlation, distance, dot product, histogram, vector
 * (element-wise) copy, equal, plus, minus, times, and divide.
 * <li> matrix functions: min, max, rowSums, colSums, rowMeans, colMeans, transpose,
 * cov, cor, matrix copy, equals.
 * <li> random functions: random, randomInt, and permutate.
 * <li> Find the root of a univariate function with or without derivative.
 * </uL>
 *
 * @author Haifeng Li
 */
public class MathEx {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(MathEx.class);

    /**
     * Dynamically determines the machine parameters of the floating-point arithmetic.
     */
    private static final FPU fpu = new FPU();
    /**
     * The machine precision for the double type, which is the difference between 1
     * and the smallest value greater than 1 that is representable for the double type.
     */
    public static final double EPSILON = fpu.EPSILON;
    /**
     * The base of the exponent of the double type.
     */
    public static final int RADIX = fpu.RADIX;
    /**
     * The number of digits (in radix base) in the mantissa.
     */
    public static final int DIGITS = fpu.RADIX;
    /**
     * Rounding style.
     * <ul>
     * <li> 0 if floating-point addition chops
     * <li> 1 if floating-point addition rounds, but not in the ieee style
     * <li> 2 if floating-point addition rounds in the ieee style
     * <li> 3 if floating-point addition chops, and there is partial underflow
     * <li> 4 if floating-point addition rounds, but not in the ieee style, and there is partial underflow
     * <li> 5 if floating-point addition rounds in the IEEEE style, and there is partial underflow
     * </ul>
     */
    public static final int ROUND_STYLE = fpu.ROUND_STYLE;
    /**
     * The largest negative integer such that 1.0 + RADIX<sup>MACHEP</sup> &ne; 1.0,
     * except that machep is bounded below by -(DIGITS+3)
     */
    public static final int MACHEP = fpu.MACHEP;
    /**
     * The largest negative integer such that 1.0 - RADIX<sup>NEGEP</sup> &ne; 1.0,
     * except that negeps is bounded below by -(DIGITS+3)
     */
    public static final int NEGEP = fpu.NEGEP;
    /**
     * Root finding algorithms.
     */
    public static final Root root = new Root();
    /**
     * The Broyden–Fletcher–Goldfarb–Shanno (BFGS) algorithm
     * for unconstrained nonlinear optimization problems.
     */
    public static final BFGS BFGS = new BFGS();

    /**
     * This RNG is to generate the seeds for multi-threads.
     * Each thread will use different seed and unlikely generates
     * the correlated sequences with other threads.
     */
    private static Random seedRNG = new Random();

    /**
     * Used seeds.
     */
    private static HashSet<Long> seeds = new HashSet<>();

    /**
     * High quality random number generator.
     */
    private static ThreadLocal<Random> random = new ThreadLocal<smile.math.Random>() {
        protected synchronized Random initialValue() {
            // For the first RNG, we use the default seed so that we can
            // get repeatable results for random algorithms.
            // Note that this may or may not be the main thread.
            long seed = 19650218L;

            // Make sure other threads not to use the same seed.
            // This is very important for some algorithms such as random forest.
            // Otherwise, all trees of random forest are same except the main thread one.
            if (!seeds.isEmpty()) {
                do {
                    seed = probablePrime(19650218L, 256, seedRNG);
                } while (seeds.contains(seed));
            }

            logger.info(String.format("Set RNG seed %d for thread %s", seed, Thread.currentThread().getName()));
            seeds.add(seed);
            return new Random(seed);
        }
    };

    /**
     * Dynamically determines the machine parameters of the floating-point arithmetic.
     */
    private static class FPU {
        double EPSILON = Math.pow(2.0, -52.0);
        int RADIX = 2;
        int DIGITS = 53;
        int ROUND_STYLE = 2;
        int MACHEP = -52;
        int NEGEP = -53;

        FPU() {
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
    }
    
    /**
     * Private constructor.
     */
    private MathEx() {

    }

    /**
     * log(2), used in log2().
     */
    private static final double LOG2 = Math.log(2);

    /**
     * Log of base 2.
     */
    public static double log2(double x) {
        return Math.log(x) / LOG2;
    }

    /**
     * Returns natural log without underflow.
     */
    public static double log(double x) {
        double y = -690.7755;
        if (x > 1E-300) {
            y = Math.log(x);
        }
        return y;
    }

    /**
     * Returns natural log(1+exp(x)) without overflow.
     */
    public static double log1pe(double x) {
        double y = x;
        if (x <= 15) {
            y = Math.log1p(Math.exp(x));
        }

        return y;
    }

    /** Returns true if x is an integer. */
    public static boolean isInt(double x) {
        return (x == Math.floor(x)) && !Double.isInfinite(x);
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
        
        double absa = abs(a);
        double absb = abs(b);
        return abs(a - b) <= Math.min(absa, absb) * 2.2204460492503131e-16;
    }
        
    /**
     * Logistic sigmoid function.
     */
    public static double logistic(double x) {
        double y;
        if (x < -40) {
            y = 2.353853e+17;
        } else if (x > 40) {
            y = 1.0 + 4.248354e-18;
        } else {
            y = 1.0 + exp(-x);
        }

        return 1.0 / y;
    }

    /**
     * Hyperbolic tangent function. The tanh function is a rescaling of the
     * logistic sigmoid, such that its outputs range from -1 to 1.
     */
    public static double tanh(double x) {
        return 2.0 * logistic(2.0 * x) - 1.0;
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
     * Returns true if n is probably prime, false if it's definitely composite.
     * This implements Miller-Rabin primality test.
     * @param n an odd integer to be tested for primality
     * @param k a parameter that determines the accuracy of the test
     */
    public static boolean isProbablePrime(long n, int k) {
        return isProbablePrime(n, k, random.get());
    }

    /**
     * Returns true if n is probably prime, false if it's definitely composite.
     * This implements Miller-Rabin primality test.
     * @param n an odd integer to be tested for primality
     * @param k a parameter that determines the accuracy of the test
     * @param rng random number generator
     */
    private static boolean isProbablePrime(long n, int k, Random rng) {
        if (n <= 1 || n == 4)
            return false;
        if (n <= 3)
            return true;

        // Find r such that n = 2^d * r + 1 for some r >= 1
        int s = 0;
        long d = n - 1;
        while (d % 2 == 0) {
            s++;
            d = d / 2;
        }

        for (int i = 0; i < k; i++) {
            long a = 2 + rng.nextLong() % (n-4);
            long x = power(a, d, n);
            if (x == 1 || x == n -1)
                continue;

            int r = 0;
            for (; r < s; r++) {
                x = (x * x) % n;
                if (x == 1) return false;
                if (x == n - 1) break;
            }

            // None of the steps made x equal n-1.
            if (r == s) return false;
        }
        return true;
    }

    /**
     * Modular exponentiation (x^y) % p
     */
    private static long power(long x, long y, long p)
    {
        long res = 1;      // Initialize result
        x = x % p;  // Update x if it is more than or
        // equal to p
        while (y > 0) {
            // If y is odd, multiply x with result
            if ((y & 1) == 1) res = (res*x) % p;

            // y must be even now
            y = y>>1; // y = y/2
            x = (x*x) % p;
        }
        return res;
    }

    /**
     * Round a double vale to given digits such as 10^n, where n is a positive
     * or negative integer.
     */
    public static double round(double x, int decimal) {
        if (decimal < 0) {
            return Math.round(x / Math.pow(10, -decimal)) * Math.pow(10, -decimal);
        } else {
            return Math.round(x * Math.pow(10, decimal)) / Math.pow(10, decimal);
        }
    }

    /**
     * The factorial of n.
     *
     * @return factorial returned as double but is, numerically, an integer.
     * Numerical rounding may make this an approximation after n = 21.
     */
    public static double factorial(int n) {
        if (n < 0) {
            throw new IllegalArgumentException("n has to be non-negative.");
        }

        double f = 1.0;
        for (int i = 2; i <= n; i++) {
            f *= i;
        }

        return f;
    }

    /**
     * The log of factorial of n.
     */
    public static double lfactorial(int n) {
        if (n < 0) {
            throw new IllegalArgumentException(String.format("n has to be non-negative: %d", n));
        }

        double f = 0.0;
        for (int i = 2; i <= n; i++) {
            f += Math.log(i);
        }

        return f;
    }

    /**
     * The n choose k. Returns 0 if n is less than k.
     */
    public static double choose(int n, int k) {
        if (n < 0 || k < 0) {
            throw new IllegalArgumentException(String.format("Invalid n = %d, k = %d", n, k));
        }

        if (n < k) {
            return 0.0;
        }

        return floor(0.5 + exp(lchoose(n, k)));
    }

    /**
     * The log of n choose k.
     */
    public static double lchoose(int n, int k) {
        if (n < 0 || k < 0 || k > n) {
            throw new IllegalArgumentException(String.format("Invalid n = %d, k = %d", n, k));
        }

        return lfactorial(n) - lfactorial(k) - lfactorial(n - k);
    }

    /**
     * Initialize the random generator with a seed.
     */
    public static void setSeed(long seed) {
        if (seeds.isEmpty()) {
            seedRNG.setSeed(seed);
            random.get().setSeed(seed);
            seeds.clear();
            seeds.add(seed);
        } else {
            random.get().setSeed(seed);
            seeds.add(seed);
        }
    }

    /**
     * Initialize the random generator with a random seed from a
     * cryptographically strong random number generator.
     */
    public static void setSeed() {
        java.security.SecureRandom sr = new java.security.SecureRandom();
        byte[] bytes = sr.generateSeed(Long.BYTES);
        long seed = 0;
        for (int i = 0; i < Long.BYTES; i++) {
            seed <<= 8;
            seed |= (bytes[i] & 0xFF);
        }

        setSeed(seed);
    }

    /**
     * Returns a probably prime number greater than n.
     * @param n the returned value should be greater than n.
     * @param k a parameter that determines the accuracy of the primality test
     */
    public static long probablePrime(long n, int k) {
        return probablePrime(n, k, random.get());
    }

    /** Returns a probably prime number. */
    private static long probablePrime(long n, int k, Random rng) {
        long seed = n + rng.nextInt(899999963); // The largest prime less than 9*10^8
        for (int i = 0; i < 4096; i++) {
            if (isProbablePrime(seed, k, rng)) break;
            seed = n + rng.nextInt(899999963);
        }

        return seed;
    }

    /**
     * Returns a stream of prime numbers to be used as RNG seeds.
     * @param n the returned value should be greater than n.
     * @param k a parameter that determines the accuracy of the primality test
     */
    public static LongStream seeds(long n, int k) {
        return LongStream.generate(() -> probablePrime(n, k, seedRNG));
    }

    /**
     * Given a set of n probabilities, generate a random number in [0, n).
     * @param prob probabilities of size n. The prob argument can be used to
     * give a vector of weights for obtaining the elements of the vector being
     * sampled. They need not sum to one, but they should be non-negative and
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
     * sampled. They need not sum to one, but they should be non-negative and
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
     * Generate a random number in [0, 1).
     */
    public static double random() {
        return random.get().nextDouble();
    }

    /**
     * Generate n random numbers in [0, 1).
     */
    public static double[] random(int n) {
        double[] x = new double[n];
        random.get().nextDoubles(x);
        return x;
    }

    /**
     * Generate a uniform random number in the range [lo, hi).
     * @param lo lower limit of range
     * @param hi upper limit of range
     * @return a uniform random real in the range [lo, hi)
     */
    public static double random(double lo, double hi) {
        return random.get().nextDouble(lo, hi);
    }

    /**
     * Generate n uniform random numbers in the range [lo, hi).
     * @param n size of the array
     * @param lo lower limit of range
     * @param hi upper limit of range
     * @return a uniform random real in the range [lo, hi)
     */
    public static double[] random(double lo, double hi, int n) {
        double[] x = new double[n];
        random.get().nextDoubles(x, lo, hi);
        return x;
    }

    /**
     * Returns a random long integer.
     */
    public static long randomLong() {
        return random.get().nextLong();
    }

    /**
     * Returns a random integer in [0, n).
     */
    public static int randomInt(int n) {
        return random.get().nextInt(n);
    }

    /**
     * Returns a random integer in [lo, hi).
     */
    public static int randomInt(int lo, int hi) {
        int w = hi - lo;
        return lo + random.get().nextInt(w);
    }

    /**
     * Generates a permutation of 0, 1, 2, ..., n-1, which is useful for
     * sampling without replacement.
     */
    public static int[] permutate(int n) {
        return random.get().permutate(n);
    }
    
    /**
     * Generates a permutation of given array.
     */
    public static void permutate(int[] x) {
        random.get().permutate(x);
    }

    /**
     * Generates a permutation of given array.
     */
    public static void permutate(float[] x) {
        random.get().permutate(x);
    }

    /**
     * Generates a permutation of given array.
     */
    public static void permutate(double[] x) {
        random.get().permutate(x);
    }

    /**
     * Generates a permutation of given array.
     */
    public static void permutate(Object[] x) {
        random.get().permutate(x);
    }

    /**
     * The softmax function without overflow. The function takes as
     * an input vector of K real numbers, and normalizes it into a
     * probability distribution consisting of K probabilities
     * proportional to the exponentials of the input numbers.
     * That is, prior to applying softmax, some vector components
     * could be negative, or greater than one; and might not sum
     * to 1; but after applying softmax, each component will be
     * in the interval (0,1), and the components will add up to 1,
     * so that they can be interpreted as probabilities. Furthermore,
     * the larger input components will correspond to larger probabilities.
     *
     * @param posteriori the input/output vector.
     * @return the index of largest posteriori probability.
     */
    public static int softmax(double[] posteriori) {
        return softmax(posteriori, posteriori.length);
    }

    /**
     * The softmax function without overflow. The function takes as
     * an input vector of K real numbers, and normalizes it into a
     * probability distribution consisting of K probabilities
     * proportional to the exponentials of the input numbers.
     * That is, prior to applying softmax, some vector components
     * could be negative, or greater than one; and might not sum
     * to 1; but after applying softmax, each component will be
     * in the interval (0,1), and the components will add up to 1,
     * so that they can be interpreted as probabilities. Furthermore,
     * the larger input components will correspond to larger probabilities.
     *
     * @param x the input/output vector.
     * @param k uses only first k components of input vector.
     * @return the index of largest posteriori probability.
     */
    public static int softmax(double[] x, int k) {
        int y = -1;
        double max = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < k; i++) {
            if (x[i] > max) {
                max = x[i];
                y = i;
            }
        }

        double Z = 0.0;
        for (int i = 0; i < k; i++) {
            double out = exp(x[i] - max);
            x[i] = out;
            Z += out;
        }

        for (int i = 0; i < k; i++) {
            x[i] /= Z;
        }

        return y;
    }

    /** Combines the arguments to form a vector. */
    public static int[] c(int... x) {
        return x;
    }

    /** Combines the arguments to form a vector. */
    public static float[] c(float... x) {
        return x;
    }

    /** Combines the arguments to form a vector. */
    public static double[] c(double... x) {
        return x;
    }

    /** Combines the arguments to form a vector. */
    public static String[] c(String... x) {
        return x;
    }

    /** Merges multiple vectors into one. */
    public static int[] c(int[]... list) {
        int n = 0;
        for (int[] x : list) n += x.length;
        int[] y = new int[n];
        int pos = 0;
        for (int[] x: list) {
            System.arraycopy(x, 0, y, pos, x.length);
            pos += x.length;
        }
        return y;
    }

    /** Merges multiple vectors into one. */
    public static float[] c(float[]... list) {
        int n = 0;
        for (float[] x : list) n += x.length;
        float[] y = new float[n];
        int pos = 0;
        for (float[] x: list) {
            System.arraycopy(x, 0, y, pos, x.length);
            pos += x.length;
        }
        return y;
    }

    /** Merges multiple vectors into one. */
    public static double[] c(double[]... list) {
        int n = 0;
        for (double[] x : list) n += x.length;
        double[] y = new double[n];
        int pos = 0;
        for (double[] x: list) {
            System.arraycopy(x, 0, y, pos, x.length);
            pos += x.length;
        }
        return y;
    }

    /** Concatenates multiple vectors into one array of strings. */
    public static String[] c(String[]... list) {
        int n = 0;
        for (String[] x : list) n += x.length;
        String[] y = new String[n];
        int pos = 0;
        for (String[] x: list) {
            System.arraycopy(x, 0, y, pos, x.length);
            pos += x.length;
        }
        return y;
    }

    /** Take a sequence of vector arguments and combine by columns. */
    public static int[] cbind(int[]... x) {
        return c(x);
    }

    /** Take a sequence of vector arguments and combine by columns. */
    public static float[] cbind(float[]... x) {
        return c(x);
    }

    /** Take a sequence of vector arguments and combine by columns. */
    public static double[] cbind(double[]... x) {
        return c(x);
    }

    /** Take a sequence of vector arguments and combine by columns. */
    public static String[] cbind(String[]... x) {
        return c(x);
    }

    /** Take a sequence of vector arguments and combine by rows. */
    public static int[][] rbind(int[]... x) {
        return x;
    }

    /** Take a sequence of vector arguments and combine by rows. */
    public static float[][] rbind(float[]... x) {
        return x;
    }

    /** Take a sequence of vector arguments and combine by rows. */
    public static double[][] rbind(double[]... x) {
        return x;
    }

    /** Take a sequence of vector arguments and combine by rows. */
    public static String[][] rbind(String[]... x) {
        return x;
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
            Sort.swap(a, i++, j--);  // code for swap not shown, but easy enough
        }
    }

    /**
     * Reverses the order of the elements in the specified array.
     * @param a an array to reverse.
     */
    public static void reverse(float[] a) {
        int i = 0, j = a.length - 1;
        while (i < j) {
            Sort.swap(a, i++, j--);  // code for swap not shown, but easy enough
        }
    }

    /**
     * Reverses the order of the elements in the specified array.
     * @param a an array to reverse.
     */
    public static void reverse(double[] a) {
        int i = 0, j = a.length - 1;
        while (i < j) {
            Sort.swap(a, i++, j--);  // code for swap not shown, but easy enough
        }
    }

    /**
     * Reverses the order of the elements in the specified array.
     * @param a an array to reverse.
     */
    public static <T> void reverse(T[] a) {
        int i = 0, j = a.length - 1;
        while (i < j) {
            Sort.swap(a, i++, j--);
        }
    }

    /**
     * minimum of 3 integers
     */
    public static int min(int a, int b, int c) {
        return Math.min(Math.min(a, b), c);
    }

    /**
     * minimum of 3 floats
     */
    public static double min(float a, float b, float c) {
        return Math.min(Math.min(a, b), c);
    }

    /**
     * minimum of 3 doubles
     */
    public static double min(double a, double b, double c) {
        return Math.min(Math.min(a, b), c);
    }

    /**
     * minimum of 4 integers
     */
    public static int min(int a, int b, int c, int d) {
        return Math.min(Math.min(Math.min(a, b), c), d);
    }

    /**
     * minimum of 4 floats
     */
    public static double min(float a, float b, float c, float d) {
        return Math.min(Math.min(Math.min(a, b), c), d);
    }

    /**
     * minimum of 4 doubles
     */
    public static double min(double a, double b, double c, double d) {
        return Math.min(Math.min(Math.min(a, b), c), d);
    }

    /**
     * maximum of 3 integers
     */
    public static int max(int a, int b, int c) {
        return Math.max(Math.max(a, b), c);
    }

    /**
     * maximum of 3 floats
     */
    public static float max(float a, float b, float c) {
        return Math.max(Math.max(a, b), c);
    }

    /**
     * maximum of 3 doubles
     */
    public static double max(double a, double b, double c) {
        return Math.max(Math.max(a, b), c);
    }

    /**
     * maximum of 4 integers
     */
    public static int max(int a, int b, int c, int d) {
        return Math.max(Math.max(Math.max(a, b), c), d);
    }

    /**
     * maximum of 4 floats
     */
    public static float max(float a, float b, float c, float d) {
        return Math.max(Math.max(Math.max(a, b), c), d);
    }

    /**
     * maximum of 4 doubles
     */
    public static double max(double a, double b, double c, double d) {
        return Math.max(Math.max(Math.max(a, b), c), d);
    }

    /**
     * Returns the minimum value of an array.
     */
    public static int min(int[] x) {
        int min = x[0];

        for (int n : x) {
            if (n < min) {
                min = n;
            }
        }

        return min;
    }

    /**
     * Returns the minimum value of an array.
     */
    public static float min(float[] x) {
        float min = Float.POSITIVE_INFINITY;

        for (float n : x) {
            if (n < min) {
                min = n;
            }
        }

        return min;
    }

    /**
     * Returns the minimum value of an array.
     */
    public static double min(double[] x) {
        double min = Double.POSITIVE_INFINITY;

        for (double n : x) {
            if (n < min) {
                min = n;
            }
        }

        return min;
    }

    /**
     * Returns the index of minimum value of an array.
     */
    public static int whichMin(int[] x) {
        int min = x[0];
        int which = 0;

        for (int i = 1; i < x.length; i++) {
            if (x[i] < min) {
                min = x[i];
                which = i;
            }
        }

        return which;
    }

    /**
     * Returns the index of minimum value of an array.
     */
    public static int whichMin(float[] x) {
        float min = Float.POSITIVE_INFINITY;
        int which = 0;

        for (int i = 0; i < x.length; i++) {
            if (x[i] < min) {
                min = x[i];
                which = i;
            }
        }

        return which;
    }

    /**
     * Returns the index of minimum value of an array.
     */
    public static int whichMin(double[] x) {
        double min = Double.POSITIVE_INFINITY;
        int which = 0;

        for (int i = 0; i < x.length; i++) {
            if (x[i] < min) {
                min = x[i];
                which = i;
            }
        }

        return which;
    }

    /**
     * Returns the index of minimum value of an array.
     */
    public static IntPair whichMin(double[][] x) {
        double min = Double.POSITIVE_INFINITY;
        int whichRow = 0;
        int whichCol = 0;

        for (int i = 0; i < x.length; i++) {
            for (int j = 0; j < x[i].length; j++) {
                if (x[i][j] < min) {
                    min = x[i][j];
                    whichRow = i;
                    whichCol = j;
                }
            }
        }

        return new IntPair(whichRow, whichCol);
    }

    /**
     * Returns the maximum value of an array.
     */
    public static int max(int[] x) {
        int max = x[0];

        for (int n : x) {
            if (n > max) {
                max = n;
            }
        }

        return max;
    }

    /**
     * Returns the maximum value of an array.
     */
    public static float max(float[] x) {
        float max = Float.NEGATIVE_INFINITY;

        for (float n : x) {
            if (n > max) {
                max = n;
            }
        }

        return max;
    }

    /**
     * Returns the maximum value of an array.
     */
    public static double max(double[] x) {
        double max = Double.NEGATIVE_INFINITY;

        for (double n : x) {
            if (n > max) {
                max = n;
            }
        }

        return max;
    }

    /**
     * Returns the index of maximum value of an array.
     */
    public static int whichMax(int[] x) {
        int max = x[0];
        int which = 0;

        for (int i = 1; i < x.length; i++) {
            if (x[i] > max) {
                max = x[i];
                which = i;
            }
        }

        return which;
    }

    /**
     * Returns the index of maximum value of an array.
     */
    public static int whichMax(float[] x) {
        float max = Float.NEGATIVE_INFINITY;
        int which = 0;

        for (int i = 0; i < x.length; i++) {
            if (x[i] > max) {
                max = x[i];
                which = i;
            }
        }

        return which;
    }

    /**
     * Returns the index of maximum value of an array.
     */
    public static int whichMax(double[] x) {
        double max = Double.NEGATIVE_INFINITY;
        int which = 0;

        for (int i = 0; i < x.length; i++) {
            if (x[i] > max) {
                max = x[i];
                which = i;
            }
        }

        return which;
    }

    /**
     * Returns the index of maximum value of an array.
     */
    public static IntPair whichMax(double[][] x) {
        double max = Double.NEGATIVE_INFINITY;
        int whichRow = 0;
        int whichCol = 0;

        for (int i = 0; i < x.length; i++) {
            for (int j = 0; j < x[i].length; j++) {
                if (x[i][j] > max) {
                    max = x[i][j];
                    whichRow = i;
                    whichCol = j;
                }
            }
        }

        return new IntPair(whichRow, whichCol);
    }

    /**
     * Returns the minimum of a matrix.
     */
    public static int min(int[][] matrix) {
        int min = matrix[0][0];

        for (int[] x : matrix) {
            for (int y : x) {
                if (min > y) {
                    min = y;
                }
            }
        }

        return min;
    }

    /**
     * Returns the minimum of a matrix.
     */
    public static double min(double[][] matrix) {
        double min = Double.POSITIVE_INFINITY;

        for (double[] x : matrix) {
            for (double y : x) {
                if (min > y) {
                    min = y;
                }
            }
        }

        return min;
    }

    /**
     * Returns the maximum of a matrix.
     */
    public static int max(int[][] matrix) {
        int max = matrix[0][0];

        for (int[] x : matrix) {
            for (int y : x) {
                if (max < y) {
                    max = y;
                }
            }
        }

        return max;
    }

    /**
     * Returns the maximum of a matrix.
     */
    public static double max(double[][] matrix) {
        double max = Double.NEGATIVE_INFINITY;

        for (double[] x : matrix) {
            for (double y : x) {
                if (max < y) {
                    max = y;
                }
            }
        }

        return max;
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
     * Returns the row minimum for a matrix.
     */
    public static int[] rowMin(int[][] data) {
        int[] x = new int[data.length];

        for (int i = 0; i < x.length; i++) {
            x[i] = min(data[i]);
        }

        return x;
    }

    /**
     * Returns the row maximum for a matrix.
     */
    public static int[] rowMax(int[][] data) {
        int[] x = new int[data.length];

        for (int i = 0; i < x.length; i++) {
            x[i] = max(data[i]);
        }

        return x;
    }

    /**
     * Returns the row sums for a matrix.
     */
    public static long[] rowSums(int[][] data) {
        long[] x = new long[data.length];

        for (int i = 0; i < x.length; i++) {
            x[i] = sum(data[i]);
        }

        return x;
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
    public static double[] rowSums(double[][] data) {
        double[] x = new double[data.length];

        for (int i = 0; i < x.length; i++) {
            x[i] = sum(data[i]);
        }

        return x;
    }

    /**
     * Returns the row means for a matrix.
     */
    public static double[] rowMeans(double[][] data) {
        double[] x = new double[data.length];

        for (int i = 0; i < x.length; i++) {
            x[i] = mean(data[i]);
        }

        return x;
    }

    /**
     * Returns the row standard deviations for a matrix.
     */
    public static double[] rowSds(double[][] data) {
        double[] x = new double[data.length];

        for (int i = 0; i < x.length; i++) {
            x[i] = sd(data[i]);
        }

        return x;
    }

    /**
     * Returns the column minimum for a matrix.
     */
    public static int[] colMin(int[][] data) {
        int[] x = new int[data[0].length];
        Arrays.fill(x, Integer.MAX_VALUE);

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
    public static int[] colMax(int[][] data) {
        int[] x = new int[data[0].length];
        Arrays.fill(x, Integer.MIN_VALUE);

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
    public static long[] colSums(int[][] data) {
        long[] x = new long[data[0].length];

        for (int i = 0; i < data.length; i++) {
            for (int j = 0; j < x.length; j++) {
                x[j] += data[i][j];
            }
        }

        return x;
    }

    /**
     * Returns the column minimum for a matrix.
     */
    public static double[] colMin(double[][] data) {
        double[] x = new double[data[0].length];
        Arrays.fill(x, Double.POSITIVE_INFINITY);

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
        Arrays.fill(x, Double.NEGATIVE_INFINITY);

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
    public static double[] colSums(double[][] data) {
        double[] x = data[0].clone();

        for (int i = 1; i < data.length; i++) {
            for (int j = 0; j < x.length; j++) {
                x[j] += data[i][j];
            }
        }

        return x;
    }

    /**
     * Returns the column means for a matrix.
     */
    public static double[] colMeans(double[][] data) {
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
    public static double[] colSds(double[][] data) {
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
            sumsq[i] = sqrt(sumsq[i] / n - (sum[i] / data.length) * (sum[i] / n));
        }

        return sumsq;
    }

    /**
     * Returns the sum of an array.
     */
    public static int sum(byte[] x) {
        int sum = 0;

        for (int n : x) {
            sum += n;
        }

        return sum;
    }

    /**
     * Returns the sum of an array.
     */
    public static long sum(int[] x) {
        long sum = 0;

        for (int n : x) {
            sum += n;
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
            x[i] = abs(x[i] - m);
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
            x[i] = abs(x[i] - m);
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
            x[i] = abs(x[i] - m);
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
        return sqrt(squaredDistance(x, y));
    }

    /**
     * The Euclidean distance.
     */
    public static double distance(float[] x, float[] y) {
        return sqrt(squaredDistance(x, y));
    }

    /**
     * The Euclidean distance.
     */
    public static double distance(double[] x, double[] y) {
        return sqrt(squaredDistance(x, y));
    }

    /**
     * The Euclidean distance.
     */
    public static double distance(SparseArray x, SparseArray y) {
        return sqrt(squaredDistance(x, y));
    }

    /**
     * Pairwise distance between pairs of objects.
     * @param x Rows of x correspond to observations, and columns correspond to variables.
     * @return a full pairwise distance matrix.
     */
    public static double[][] pdist(double[][] x) {
        int n = x.length;

        double[][] dist = new double[n][n];
        pdist(x, dist, false, false);

        return dist;
    }

    /**
     * Pairwise distance between pairs of objects.
     * @param x Rows of x correspond to observations, and columns correspond to variables.
     * @param squared If true, compute the squared Euclidean distance.
     * @param half If true, only the lower half of dist will be referenced.
     * @param dist The distance matrix.
     */
    public static void pdist(double[][] x, double[][] dist, boolean squared, boolean half) {
        int n = x.length;
        int N = n * (n - 1) / 2;

        if (squared) {
            IntStream.range(0, N).parallel().forEach(k -> {
                int j = n - 2 - (int) Math.floor(Math.sqrt(-8*k + 4*n*(n-1)-7)/2.0 - 0.5);
                int i = k + j + 1 - n*(n-1)/2 + (n-j)*((n-j)-1)/2;
                dist[i][j] = squaredDistance(x[i], x[j]);
            });
        } else {
            IntStream.range(0, N).parallel().forEach(k -> {
                int j = n - 2 - (int) Math.floor(Math.sqrt(-8*k + 4*n*(n-1)-7)/2.0 - 0.5);
                int i = k + j + 1 - n*(n-1)/2 + (n-j)*((n-j)-1)/2;
                dist[i][j] = distance(x[i], x[j]);
            });
        }

        if (!half) {
            for (int i = 0; i < n; i++) {
                for (int j = i + 1; j < n; j++) {
                    dist[i][j] = dist[j][i];
                }
            }
        }
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
                sum += sqr(e1.x - e2.x);
                e1 = it1.hasNext() ? it1.next() : null;
                e2 = it2.hasNext() ? it2.next() : null;
            } else if (e1.i > e2.i) {
                sum += sqr(e2.x);
                e2 = it2.hasNext() ? it2.next() : null;
            } else {
                sum += sqr(e1.x);
                e1 = it1.hasNext() ? it1.next() : null;
            }
        }
        
        while (it1.hasNext()) {
            sum += sqr(it1.next().x);
        }

        while (it2.hasNext()) {
            sum += sqr(it2.next().x);
        }
        
        return sum;
    }

    /**
     * The squared Euclidean distance with handling missing values (represented as NaN).
     */
    public static double squaredDistanceWithMissingValues(double[] x, double[] y) {
        int n = x.length;
        int m = 0;
        double dist = 0.0;

        for (int i = 0; i < n; i++) {
            if (!Double.isNaN(x[i]) && !Double.isNaN(y[i])) {
                m++;
                double d = x[i] - y[i];
                dist += d * d;
            }
        }

        if (m == 0) {
            dist = Double.MAX_VALUE;
        } else {
            dist = n * dist / m;
        }

        return dist;
    }

    /**
     * Shannon's entropy.
     * @param p the probabilities.
     */
    public static double entropy(double[] p) {
        double h = 0.0;
        for (double pi : p) {
            if (pi > 0) {
                h -= pi * Math.log(pi);
            }
        }
        return h;
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
    public static int dot(int[] x, int[] y) {
        if (x.length != y.length) {
            throw new IllegalArgumentException("Arrays have different length.");
        }

        int p = 0;
        for (int i = 0; i < x.length; i++) {
            p += x[i] * y[i];
        }

        return p;
    }

    /**
     * Returns the dot product between two vectors.
     */
    public static float dot(float[] x, float[] y) {
        if (x.length != y.length) {
            throw new IllegalArgumentException("Arrays have different length.");
        }

        float p = 0.0F;
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
        return cov(data, colMeans(data));
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

        return Sxy / sqrt(Sxx * Syy);
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

        return Sxy / sqrt(Sxx * Syy);
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

        return Sxy / sqrt(Sxx * Syy);
    }

    /**
     * Returns the sample correlation matrix.
     */
    public static double[][] cor(double[][] data) {
        return cor(data, colMeans(data));
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
        crank(wksp1);
        QuickSort.sort(wksp2, wksp1);
        crank(wksp2);

        return cor(wksp1, wksp2);
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
        crank(wksp1);
        QuickSort.sort(wksp2, wksp1);
        crank(wksp2);

        return cor(wksp1, wksp2);
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

        double[] wksp1 = x.clone();
        double[] wksp2 = y.clone();

        QuickSort.sort(wksp1, wksp2);
        crank(wksp1);
        QuickSort.sort(wksp2, wksp1);
        crank(wksp2);

        return cor(wksp1, wksp2);
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

        double tau = is / (sqrt(n1) * sqrt(n2));
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

        double tau = is / (sqrt(n1) * sqrt(n2));
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

        double tau = is / (sqrt(n1) * sqrt(n2));
        return tau;
    }

    /**
     * L1 vector norm.
     */
    public static float norm1(float[] x) {
        float norm = 0.0F;

        for (float n : x) {
            norm += abs(n);
        }

        return norm;
    }

    /**
     * L1 vector norm.
     */
    public static double norm1(double[] x) {
        double norm = 0.0;

        for (double n : x) {
            norm += abs(n);
        }

        return norm;
    }

    /**
     * L2 vector norm.
     */
    public static float norm2(float[] x) {
        float norm = 0.0F;

        for (float n : x) {
            norm += n * n;
        }

        norm = (float) sqrt(norm);

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

        norm = sqrt(norm);

        return norm;
    }

    /**
     * L-infinity vector norm. Maximum absolute value.
     */
    public static float normInf(float[] x) {
        int n = x.length;

        float f = abs(x[0]);
        for (int i = 1; i < n; i++) {
            f = Math.max(f, abs(x[i]));
        }

        return f;
    }

    /**
     * L-infinity vector norm. Maximum absolute value.
     */
    public static double normInf(double[] x) {
        int n = x.length;

        double f = abs(x[0]);
        for (int i = 1; i < n; i++) {
            f = Math.max(f, abs(x[i]));
        }

        return f;
    }

    /**
     * L2 vector norm.
     */
    public static float norm(float[] x) {
        return norm2(x);
    }

    /**
     * L2 vector norm.
     */
    public static double norm(double[] x) {
        return norm2(x);
    }

    /** Returns the cosine similarity. */
    public static float cos(float[] x, float[] y) {
        return dot(x, y) / (norm2(x) * norm2(y));
    }

    /** Returns the cosine similarity. */
    public static double cos(double[] x, double[] y) {
        return dot(x, y) / (norm2(x) * norm2(y));
    }

    /**
     * Standardizes an array to mean 0 and variance 1.
     */
    public static void standardize(double[] x) {
        double mu = mean(x);
        double sigma = sd(x);

        if (isZero(sigma)) {
            logger.warn("array has variance of 0.");
            return;
        }

        for (int i = 0; i < x.length; i++) {
            x[i] = (x[i] - mu) / sigma;
        }
    }

    /**
     * Scales each column of a matrix to range [0, 1].
     */
    public static void scale(double[][] x) {
        int n = x.length;
        int p = x[0].length;

        double[] min = colMin(x);
        double[] max = colMax(x);

        for (int j = 0; j < p; j++) {
            double scale = max[j] - min[j];
            if (!isZero(scale)) {
                for (int i = 0; i < n; i++) {
                    x[i][j] = (x[i][j] - min[j]) / scale;
                }
            } else {
                for (int i = 0; i < n; i++) {
                    x[i][j] = 0.5;
                }
            }
        }
    }

    /**
     * Standardizes each column of a matrix to 0 mean and unit variance.
     */
    public static void standardize(double[][] x) {
        int n = x.length;
        int p = x[0].length;

        double[] center = colMeans(x);
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < p; j++) {
                x[i][j] = x[i][j] - center[j];
            }
        }

        double[] scale = new double[p];
        for (int j = 0; j < p; j++) {
            for (int i = 0; i < n; i++) {
                scale[j] += sqr(x[i][j]);
            }
            scale[j] = sqrt(scale[j] / (n-1));

            if (!isZero(scale[j])) {
                for (int i = 0; i < n; i++) {
                    x[i][j] /= scale[j];
                }
            }
        }
    }

    /**
     * Unitizes each column of a matrix to unit length (L_2 norm).
     */
    public static void normalize(double[][] x) {
        normalize(x, false);
    }

    /**
     * Unitizes each column of a matrix to unit length (L_2 norm).
     * @param centerizing If true, centerize each column to 0 mean.
     */
    public static void normalize(double[][] x, boolean centerizing) {
        int n = x.length;
        int p = x[0].length;

        if (centerizing) {
            double[] center = colMeans(x);
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < p; j++) {
                    x[i][j] = x[i][j] - center[j];
                }
            }
        }

        double[] scale = new double[p];
        for (int j = 0; j < p; j++) {
            for (int i = 0; i < n; i++) {
                scale[j] += sqr(x[i][j]);
            }
            scale[j] = sqrt(scale[j]);
        }

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < p; j++) {
                if (!isZero(scale[j])) {
                    x[i][j] /= scale[j];
                }
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
     * @param x an array of non-negative double
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
            if (abs(x[i] - y[i]) > epsilon) {
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
            if (abs(x[i] - y[i]) > eps) {
                return false;
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
                if (abs(x[i][j] - y[i][j]) > epsilon) {
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
                if (abs(x[i][j] - y[i][j]) > eps) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Tests if a floating number is zero. */
    public static boolean isZero(float x) {
        return isZero(x, EPSILON);
    }

    /** Tests if a floating number is zero with given epsilon. */
    public static boolean isZero(float x, float epsilon) {
        return abs(x) < epsilon;
    }

    /** Tests if a floating number is zero. */
    public static boolean isZero(double x) {
        return isZero(x, EPSILON);
    }

    /** Tests if a floating number is zero with given epsilon. */
    public static boolean isZero(double x, double epsilon) {
        return abs(x) < epsilon;
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
    public static void add(double[] y, double[] x) {
        if (x.length != y.length) {
            throw new IllegalArgumentException(String.format("Arrays have different length: x[%d], y[%d]", x.length, y.length));
        }

        for (int i = 0; i < x.length; i++) {
            y[i] += x[i];
        }
    }

    /**
     * Element-wise subtraction of two arrays y = y - x.
     * @param y minuend matrix
     * @param x subtrahend matrix
     */
    public static void sub(double[] y, double[] x) {
        if (x.length != y.length) {
            throw new IllegalArgumentException(String.format("Arrays have different length: x[%d], y[%d]", x.length, y.length));
        }

        for (int i = 0; i < x.length; i++) {
            y[i] -= x[i];
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
    public static double[] axpy(double a, double[] x, double[] y) {
        if (x.length != y.length) {
            throw new IllegalArgumentException(String.format("Arrays have different length: x[%d], y[%d]", x.length, y.length));
        }

        for (int i = 0; i < x.length; i++) {
            y[i] += a * x[i];
        }

        return y;
    }

    /**
     * Raise each element of an array to a scalar power.
     * @param x array
     * @param n scalar exponent
     * @return x<sup>n</sup>
     */
    public static double[] pow(double[] x, double n) {
        double[] y = new double[x.length];
        for (int i = 0; i < x.length; i++) y[i] = Math.pow(x[i], n);
        return y;
    }

    /**
     * Find unique elements of vector.
     * @param x an integer array.
     * @return the same values as in x but with no repetitions.
     */
    public static int[] unique(int[] x) {
        return Arrays.stream(x).distinct().toArray();
    }

    /**
     * Find unique elements of vector.
     * @param x an array of strings.
     * @return the same values as in x but with no repetitions.
     */
    public static String[] unique(String[] x) {
        return Arrays.stream(x).distinct().toArray(String[]::new);
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
}
