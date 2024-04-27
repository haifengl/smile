/*
 * Copyright (c) 2010-2021 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */

package smile.math;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Iterator;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

import smile.math.blas.UPLO;
import smile.math.distance.Distance;
import smile.math.matrix.Matrix;
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
     * The machine precision for the float type, which is the difference between 1
     * and the smallest value greater than 1 that is representable for the float type.
     */
    public static final float FLOAT_EPSILON = fpu.FLOAT_EPSILON;
    /**
     * The base of the exponent of the double type.
     */
    public static final int RADIX = fpu.RADIX;
    /**
     * The number of digits (in radix base) in the mantissa.
     */
    public static final int DIGITS = fpu.DIGITS;
    /**
     * The number of digits (in radix base) in the mantissa.
     */
    public static final int FLOAT_DIGITS = fpu.FLOAT_DIGITS;

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
     * The largest negative integer such that 1.0 + RADIX<sup>MACHEP</sup> &ne; 1.0,
     * except that machep is bounded below by -(DIGITS+3)
     */
    public static final int FLOAT_MACHEP = fpu.FLOAT_MACHEP;
    /**
     * The largest negative integer such that 1.0 - RADIX<sup>NEGEP</sup> &ne; 1.0,
     * except that negeps is bounded below by -(DIGITS+3)
     */
    public static final int NEGEP = fpu.NEGEP;
    /**
     * The largest negative integer such that 1.0 - RADIX<sup>NEGEP</sup> &ne; 1.0,
     * except that negeps is bounded below by -(DIGITS+3)
     */
    public static final int FLOAT_NEGEP = fpu.FLOAT_NEGEP;

    /**
     * This RNG is to generate the seeds for multi-threads.
     * Each thread will use different seed and unlikely generates
     * the correlated sequences with other threads.
     */
    private static final SecureRandom seedRNG = new SecureRandom();

    /**
     * The default seeds for thread-local RNGs for reproducible results.
     */
    private static final long[] DEFAULT_SEEDS = {
            -4106602711295138952L,  7872020634117869514L, -1722503517109829138L, -3386820675908254116L,
            -1736715870046201019L,  3854590623768163340L,  4984519038350406438L,   831971085876758331L,
             7131773007627236777L, -3609561992173376238L, -8759399602515137276L,  6192158663294695439L,
            -5656470009161653116L, -7984826214821970800L, -9113192788977418232L, -8979910231410580019L,
            -4619021025191354324L, -5082417586190057466L, -6554946940783144090L, -3610462176018822900L,
             8959796931768911980L, -4251632352234989839L,  4922191169088134258L, -7282805902317830669L,
             3869302430595840919L,  2517690626940415460L,  4056663221614950174L,  6429856319379397738L,
             7298845553914383313L,  8179510284261677971L,  4282994537597585253L,  7300184601511783348L,
             2596703774884172704L,  1089838915342514714L,  4323657609714862439L,   777826126579190548L,
            -1902743089794461140L, -2460431043688989882L, -3261708534465890932L,  4007861469505443778L,
             8067600139237526646L,  5717273542173905853L,  2938568334013652889L, -2972203304739218305L,
             6544901794394958069L,  7013723936758841449L, -4215598453287525312L, -1454689091401951913L,
            -5699280845313829011L, -9147984414924288540L,  5211986845656222459L, -1287642354429721659L,
            -1509334943513011620L, -9000043616528857326L, -2902817511399216571L,  -742823064588229527L,
            -4937222449957498789L,  -455679889440396397L, -6109470266907575296L,  5515435653880394376L,
             5557224587324997029L,  8904139390487005840L,  6560726276686488510L,  6959949429287621625L,
            -6055733513105375650L,  5762016937143172332L, -9186652929482643329L, -1105816448554330895L,
            -8200377873547841359L,  9107473159863354619L,  3239950546973836199L, -8104429975176305012L,
             3822949195131885242L, -5261390396129824777L,  9176101422921943895L, -5102541493993205418L,
            -1254710019595692814L, -6668066200971989826L, -2118519708589929546L,  5428466612765068681L,
            -6528627776941116598L, -5945449163896244174L, -3293290115918281076L,  6370347300411991230L,
            -7043881693953271167L,  8078993941165238212L,  6894961504641498099L, -8798276497942360228L,
             2276271091333773917L, -7184141741385833013L, -4787502691178107481L,  1255068205351917608L,
            -8644146770023935609L,  5124094110137147339L,  4917075344795488880L,  3423242822219783102L,
             1588924456880980404L,  8515495360312448868L, -5563691320675461929L, -2352238951654504517L,
            -7416919543420127888L,   631412478604690114L,   689144891258712875L, -9001615284848119152L,
            -6275065758899203088L,  8164387857252400515L, -4122060123604826739L, -2016541034210046261L,
            -7178335877193796678L,  3354303106860129181L,  5731595363486898779L, -2874315602397298018L,
             5386746429707619069L,  9036622191596156315L, -7950190733284789459L, -5741691593792426169L,
            -8600462258998065159L,  5460142111961227035L,   276738899508534641L,  2358776514903881139L,
             -837649704945720257L, -3608906204977108245L,  2960825464614526243L,  7339056324843827739L,
            -5709958573878745135L, -5885403829221945248L,  6611935345917126768L,  2588814037559904539L
    };

    /**
     * The index of next RNG seed.
     */
    private static int nextSeed = -1;

    /**
     * High quality random number generator.
     */
    private static final ThreadLocal<Random> random = ThreadLocal.withInitial(() -> {
        synchronized(DEFAULT_SEEDS) {
            // For the first RNG instance, we use the default seed of RNG algorithms.
            if (nextSeed < 0) {
                nextSeed = 0;
                return new Random();
            }
            if (nextSeed < DEFAULT_SEEDS.length) {
                return new Random(DEFAULT_SEEDS[nextSeed++]);
            } else {
                return new Random(generateSeed());
            }
        }
    });

    /**
     * Dynamically determines the machine parameters of the floating-point arithmetic.
     */
    private static class FPU {
        final int RADIX;
        int DIGITS;
        final int FLOAT_DIGITS = 24;
        int ROUND_STYLE;
        int MACHEP;
        final int FLOAT_MACHEP = -23;
        int NEGEP;
        final int FLOAT_NEGEP = -24;
        final float FLOAT_EPSILON = (float) Math.pow(2.0, FLOAT_MACHEP);
        final double EPSILON;

        FPU() {
            double beta, betain, betah, a, b, ZERO, ONE, TWO, temp, tempa, temp1;
            int i, itemp;

            ONE = 1;
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
            beta = RADIX;

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
     * Private constructor to prevent instance creation.
     */
    private MathEx() {

    }

    /**
     * log(2), used in log2().
     */
    private static final double LOG2 = Math.log(2);

    /**
     * Log of base 2.
     * @param x a real number.
     * @return the value <code>log2(x)</code>.
     */
    public static double log2(double x) {
        return Math.log(x) / LOG2;
    }

    /**
     * Returns natural log without underflow.
     * @param x a real number.
     * @return the value <code>log(x)</code>.
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
     * @param x a real number.
     * @return the value <code>log(1+exp(x))</code>.
     */
    public static double log1pe(double x) {
        double y = x;
        if (x <= 15) {
            y = Math.log1p(Math.exp(x));
        }

        return y;
    }

    /**
     * Returns true if x is an integer.
     * @param x a real number.
     * @return true if x is an integer.
     */
    public static boolean isInt(float x) {
        return (x == (float) Math.floor(x)) && !Float.isInfinite(x);
    }

    /**
     * Returns true if x is an integer.
     * @param x a real number.
     * @return true if x is an integer.
     */
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
     * Logistic sigmoid function <code>1 / (1 + exp(-x))</code>.
     * @param x a real number.
     * @return the sigmoid function.
     */
    public static double sigmoid(double x) {
        // clip x in [-36, 36] to prevent overflow/underflow.
        x = Math.max(-36, Math.min(x, 36));
        return 1.0 / (1.0 + exp(-x));
    }

    /**
     * Returns x * x.
     * @param x a real number.
     * @return the square of x.
     */
    public static double pow2(double x) {
        return x * x;
    }

    /**
     * Returns true if x is a power of 2.
     * @param x a real number.
     * @return true if x is a power of 2.
     */
    public static boolean isPower2(int x) {
        return x > 0 && (x & (x - 1)) == 0;
    }

    /**
     * Returns true if n is probably prime, false if it's definitely composite.
     * This implements Miller-Rabin primality test.
     * @param n an odd integer to be tested for primality
     * @param k the number of rounds of primality test to perform.
     *          With larger number of rounds, the test is more
     *          accurate but also takes longer time.
     * @return true if n is probably prime, false if it's definitely composite.
     */
    public static boolean isProbablePrime(long n, int k) {
        return isProbablePrime(n, k, random.get());
    }

    /**
     * Returns true if n is probably prime, false if it's definitely composite.
     * This implements Miller-Rabin primality test.
     * @param n an odd integer to be tested for primality
     * @param k the number of rounds of primality test to perform.
     *          With larger number of rounds, the test is more
     *          accurate but also takes longer time.
     * @param rng random number generator
     * @return true if n is probably prime, false if it's definitely composite.
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
     * Modular exponentiation <code>x<sup>y</sup> % p</code>.
     * @param x the base.
     * @param y the exponent.
     * @param p the modular.
     * @return the modular exponentation.
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
     * @param x a real number.
     * @param decimal the number of digits to round to.
     * @return the rounded value.
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
     * @param n a positive integer.
     * @return the factorial returned as double but is, numerically, an integer.
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
     *
     * @param n a positive integer.
     * @return the log of factorial .
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
     * @param n the total number of objects in the set.
     * @param k the number of choosing objects from the set.
     * @return the number of combinations.
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
     * @param n the total number of objects in the set.
     * @param k the number of choosing objects from the set.
     * @return the log of the number of combinations.
     */
    public static double lchoose(int n, int k) {
        if (k < 0 || k > n) {
            throw new IllegalArgumentException(String.format("Invalid n = %d, k = %d", n, k));
        }

        return lfactorial(n) - lfactorial(k) - lfactorial(n - k);
    }

    /**
     * Returns a random number to seed other random number generators.
     * @return a random number to seed other random number generators.
     */
    public static long generateSeed() {
        byte[] bytes = generateSeed(Long.BYTES);
        long seed = 0;
        for (int i = 0; i < Long.BYTES; i++) {
            seed <<= 8;
            seed |= (bytes[i] & 0xFF);
        }
        return seed;
    }

    /**
     * Returns the given number of random bytes to seed other random number
     * generators.
     * @param numBytes the number of seed bytes to generate.
     * @return the seed bytes.
     */
    public static byte[] generateSeed(int numBytes) {
        synchronized(seedRNG) {
            return seedRNG.generateSeed(numBytes);
        }
    }

    /**
     * Returns a stream of random numbers to be used as RNG seeds.
     * @return a stream of random numbers to be used as RNG seeds.
     */
    public static LongStream seeds() {
        return LongStream.generate(MathEx::generateSeed).sequential();
    }

    /**
     * Initialize the random number generator with a seed.
     * @param seed the RNG seed.
     */
    public static void setSeed(long seed) {
        random.get().setSeed(seed);
    }

    /**
     * Returns a probably prime number greater than n.
     * @param n the returned value should be greater than n.
     * @param k the number of rounds of primality test to perform.
     *          With larger number of rounds, the test is more
     *          accurate but also takes longer time.
     * @return a probably prime number greater than n.
     */
    public static long probablePrime(long n, int k) {
        return probablePrime(n, k, random.get());
    }

    /**
     * Returns a probably prime number.
     * @param n the returned value should be greater than n.
     * @param k the number of rounds of primality test to perform.
     *          With larger number of rounds, the test is more
     *          accurate but also takes longer time.
     * @param rng the random number generator.
     * @return a probably prime number greater than n.
     */
    private static long probablePrime(long n, int k, Random rng) {
        long seed = n + rng.nextInt(899999963); // The largest prime less than 9*10^8
        for (int i = 0; i < 4096; i++) {
            if (isProbablePrime(seed, k, rng)) break;
            seed = n + rng.nextInt(899999963);
        }

        return seed;
    }

    /**
     * Given a set of n probabilities, generate a random number in [0, n).
     *
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
     *
     * @param prob probabilities of size n. The prob argument can be used to
     * give a vector of weights for obtaining the elements of the vector being
     * sampled. They need not sum to one, but they should be non-negative and
     * not all zero.
     * @param n the number of random numbers.
     * @return random numbers in range of [0, m).
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
     * @return a random number.
     */
    public static double random() {
        return random.get().nextDouble();
    }

    /**
     * Generate n random numbers in [0, 1).
     * @param n the number of random numbers.
     * @return the random numbers.
     */
    public static double[] random(int n) {
        double[] x = new double[n];
        random.get().nextDoubles(x);
        return x;
    }

    /**
     * Generate a uniform random number in the range [lo, hi).
     *
     * @param lo lower limit of range
     * @param hi upper limit of range
     * @return a uniform random number in the range [lo, hi)
     */
    public static double random(double lo, double hi) {
        return random.get().nextDouble(lo, hi);
    }

    /**
     * Generate uniform random numbers in the range [lo, hi).
     *
     * @param lo lower limit of range
     * @param hi upper limit of range
     * @param n the number of random numbers.
     * @return uniform random numbers in the range [lo, hi)
     */
    public static double[] random(double lo, double hi, int n) {
        double[] x = new double[n];
        random.get().nextDoubles(x, lo, hi);
        return x;
    }

    /**
     * Returns a random long integer.
     * @return a random long integer.
     */
    public static long randomLong() {
        return random.get().nextLong();
    }

    /**
     * Returns a random integer in [0, n).
     * @param n the upper bound of random number.
     * @return a random integer.
     */
    public static int randomInt(int n) {
        return random.get().nextInt(n);
    }

    /**
     * Returns a random integer in [lo, hi).
     *
     * @param lo lower limit of range
     * @param hi upper limit of range
     * @return a uniform random number in the range [lo, hi)
     */
    public static int randomInt(int lo, int hi) {
        int w = hi - lo;
        return lo + random.get().nextInt(w);
    }

    /**
     * Returns a permutation of <code>(0, 1, 2, ..., n-1)</code>.
     *
     * @param n the upper bound.
     * @return the permutation of <code>(0, 1, 2, ..., n-1)</code>.
     */
    public static int[] permutate(int n) {
        return random.get().permutate(n);
    }

    /**
     * Permutates an array.
     * @param x the array.
     */
    public static void permutate(int[] x) {
        random.get().permutate(x);
    }

    /**
     * Permutates an array.
     * @param x the array.
     */
    public static void permutate(float[] x) {
        random.get().permutate(x);
    }

    /**
     * Permutates an array.
     * @param x the array.
     */
    public static void permutate(double[] x) {
        random.get().permutate(x);
    }

    /**
     * Permutates an array.
     * @param x the array.
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

    /**
     * Combines the arguments to form a vector.
     * @param x the vector elements.
     * @return the vector.
     */
    public static int[] c(int... x) {
        return x;
    }

    /**
     * Combines the arguments to form a vector.
     * @param x the vector elements.
     * @return the vector.
     */
    public static float[] c(float... x) {
        return x;
    }

    /**
     * Combines the arguments to form a vector.
     * @param x the vector elements.
     * @return the vector.
     */
    public static double[] c(double... x) {
        return x;
    }

    /**
     * Combines the arguments to form a vector.
     * @param x the vector elements.
     * @return the vector.
     */
    public static String[] c(String... x) {
        return x;
    }

    /**
     * Concatenates multiple vectors into one.
     * @param list the vectors.
     * @return the concatenated vector.
     */
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

    /**
     * Concatenates multiple vectors into one.
     * @param list the vectors.
     * @return the concatenated vector.
     */
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

    /**
     * Concatenates multiple vectors into one.
     * @param list the vectors.
     * @return the concatenated vector.
     */
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

    /**
     * Concatenates multiple vectors into one array of strings.
     * @param list the vectors.
     * @return the concatenated vector.
     */
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

    /**
     * Concatenates vectors by columns.
     * @param x the vectors.
     * @return the concatenated vector.
     */
    public static int[] cbind(int[]... x) {
        return c(x);
    }

    /**
     * Concatenates vectors by columns.
     * @param x the vectors.
     * @return the concatenated vector.
     */
    public static float[] cbind(float[]... x) {
        return c(x);
    }

    /**
     * Concatenates vectors by columns.
     * @param x the vectors.
     * @return the concatenated vector.
     */
    public static double[] cbind(double[]... x) {
        return c(x);
    }

    /**
     * Concatenates vectors by columns.
     * @param x the vectors.
     * @return the concatenated vector.
     */
    public static String[] cbind(String[]... x) {
        return c(x);
    }

    /**
     * Concatenates vectors by rows.
     * @param x the vectors.
     * @return the matrix.
     */
    public static int[][] rbind(int[]... x) {
        return x;
    }

    /**
     * Concatenates vectors by rows.
     * @param x the vectors.
     * @return the matrix.
     */
    public static float[][] rbind(float[]... x) {
        return x;
    }

    /**
     * Concatenates vectors by rows.
     * @param x the vectors.
     * @return the matrix.
     */
    public static double[][] rbind(double[]... x) {
        return x;
    }

    /**
     * Concatenates vectors by rows.
     * @param x the vectors.
     * @return the matrix.
     */
    public static String[][] rbind(String[]... x) {
        return x;
    }

    /**
     * Returns a slice of data for given indices.
     * @param data the array.
     * @param index the indices of selected elements.
     * @param <E> the data type of elements.
     * @return the selected elements.
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
     * @param data the array.
     * @param index the indices of selected elements.
     * @return the selected elements.
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
     * @param data the array.
     * @param index the indices of selected elements.
     * @return the selected elements.
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
     * @param data the array.
     * @param index the indices of selected elements.
     * @return the selected elements.
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
     * Determines if the polygon contains the point.
     *
     * @param polygon the vertices of polygon.
     * @param point the point.
     * @return true if the Polygon contains the point.
     */
    public static boolean contains(double[][] polygon, double[] point) {
        return contains(polygon, point[0], point[1]);
    }
    
    /**
     * Determines if the polygon contains the point.
     *
     * @param polygon the vertices of polygon.
     * @param x the x coordinate of point.
     * @param y the y coordinate of point.
     * @return true if the Polygon contains the point.
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
     * Returns a new array without the specified value.
     * @param a an input array.
     * @param value the value to omit.
     * @return a new array without the specified value.
     */
    public static int[] omit(int[] a, int value) {
        int n = 0;
        for (int x : a) {
            if (x != value) n++;
        }

        int i = 0;
        int[] b = new int[n];
        for (int x : a) {
            if (x != value) b[i++] = x;
        }

        return b;
    }


    /**
     * Returns a new array without the specified value.
     * @param a an input array.
     * @param value the value to omit.
     * @return a new array without the specified value.
     */
    public static float[] omit(float[] a, float value) {
        int n = 0;
        for (float x : a) {
            if (x != value) n++;
        }

        int i = 0;
        float[] b = new float[n];
        for (float x : a) {
            if (x != value) b[i++] = x;
        }

        return b;
    }

    /**
     * Returns a new array without the specified value.
     * @param a an input array.
     * @param value the value to omit.
     * @return a new array without the specified value.
     */
    public static double[] omit(double[] a, double value) {
        int n = 0;
        for (double x : a) {
            if (x != value) n++;
        }

        int i = 0;
        double[] b = new double[n];
        for (double x : a) {
            if (x != value) b[i++] = x;
        }

        return b;
    }

    /**
     * Returns a new array without NaN values.
     * @param a an input array that may contain NaN.
     * @return a new array without NaN values.
     */
    public static float[] omitNaN(float[] a) {
        int n = 0;
        for (float x : a) {
            if (!Float.isNaN(x)) n++;
        }

        int i = 0;
        float[] b = new float[n];
        for (float x : a) {
            if (!Float.isNaN(x)) b[i++] = x;
        }

        return b;
    }

    /**
     * Returns a new array without NaN values.
     * @param a an input array that may contain NaN.
     * @return a new array without NaN values.
     */
    public static double[] omitNaN(double[] a) {
        int n = 0;
        for (double x : a) {
            if (!Double.isNaN(x)) n++;
        }

        int i = 0;
        double[] b = new double[n];
        for (double x : a) {
            if (!Double.isNaN(x)) b[i++] = x;
        }

        return b;
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
     * @param a the array to reverse.
     */
    public static void reverse(float[] a) {
        int i = 0, j = a.length - 1;
        while (i < j) {
            Sort.swap(a, i++, j--);  // code for swap not shown, but easy enough
        }
    }

    /**
     * Reverses the order of the elements in the specified array.
     * @param a the array to reverse.
     */
    public static void reverse(double[] a) {
        int i = 0, j = a.length - 1;
        while (i < j) {
            Sort.swap(a, i++, j--);  // code for swap not shown, but easy enough
        }
    }

    /**
     * Reverses the order of the elements in the specified array.
     * @param a the array to reverse.
     * @param <T> the data type of array elements.
     */
    public static <T> void reverse(T[] a) {
        int i = 0, j = a.length - 1;
        while (i < j) {
            Sort.swap(a, i++, j--);
        }
    }

    /**
     * Returns the mode of the array, which is the most frequent element.
     * If there are multiple modes, one of them will be returned.
     *
     * @param a the array. The order of elements will be changed on output.
     * @return the mode.
     */
    public static int mode(int[] a) {
        Arrays.sort(a);

        int mode = -1;
        int count = 0;

        int currentValue = a[0];
        int currentCount = 1;

        for (int i = 1; i < a.length; i++) {
            if (a[i] != currentValue) {
                if (currentCount > count) {
                    mode = currentValue;
                    count = currentCount;
                }

                currentValue = a[i];
                currentCount = 1;
            } else {
                currentCount++;
            }
        }

        if (currentCount > count) {
            mode = currentValue;
        }

        return mode;
    }

    /**
     * Returns the minimum of 3 integer numbers.
     * @param a a number.
     * @param b a number.
     * @param c a number.
     * @return the minimum.
     */
    public static int min(int a, int b, int c) {
        return Math.min(Math.min(a, b), c);
    }

    /**
     * Returns the minimum of 3 float numbers.
     * @param a a number.
     * @param b a number.
     * @param c a number.
     * @return the minimum.
     */
    public static float min(float a, float b, float c) {
        return Math.min(Math.min(a, b), c);
    }

    /**
     * Returns the minimum of 3 double numbers.
     * @param a a number.
     * @param b a number.
     * @param c a number.
     * @return the minimum.
     */
    public static double min(double a, double b, double c) {
        return Math.min(Math.min(a, b), c);
    }

    /**
     * Returns the minimum of 4 integer numbers.
     * @param a a number.
     * @param b a number.
     * @param c a number.
     * @param d a number.
     * @return the minimum.
     */
    public static int min(int a, int b, int c, int d) {
        return Math.min(Math.min(Math.min(a, b), c), d);
    }

    /**
     * Returns the minimum of 4 float numbers.
     * @param a a number.
     * @param b a number.
     * @param c a number.
     * @param d a number.
     * @return the minimum.
     */
    public static float min(float a, float b, float c, float d) {
        return Math.min(Math.min(Math.min(a, b), c), d);
    }

    /**
     * Returns the minimum of 4 double numbers.
     * @param a a number.
     * @param b a number.
     * @param c a number.
     * @param d a number.
     * @return the minimum.
     */
    public static double min(double a, double b, double c, double d) {
        return Math.min(Math.min(Math.min(a, b), c), d);
    }

    /**
     * Returns the maximum of 3 integer numbers.
     * @param a a number.
     * @param b a number.
     * @param c a number.
     * @return the maximum.
     */
    public static int max(int a, int b, int c) {
        return Math.max(Math.max(a, b), c);
    }

    /**
     * Returns the maximum of 4 float numbers.
     * @param a a number.
     * @param b a number.
     * @param c a number.
     * @return the maximum.
     */
    public static float max(float a, float b, float c) {
        return Math.max(Math.max(a, b), c);
    }

    /**
     * Returns the maximum of 4 double numbers.
     * @param a a number.
     * @param b a number.
     * @param c a number.
     * @return the maximum.
     */
    public static double max(double a, double b, double c) {
        return Math.max(Math.max(a, b), c);
    }

    /**
     * Returns the maximum of 4 integer numbers.
     * @param a a number.
     * @param b a number.
     * @param c a number.
     * @param d a number.
     * @return the maximum.
     */
    public static int max(int a, int b, int c, int d) {
        return Math.max(Math.max(Math.max(a, b), c), d);
    }

    /**
     * Returns the maximum of 4 float numbers.
     * @param a a number.
     * @param b a number.
     * @param c a number.
     * @param d a number.
     * @return the maximum.
     */
    public static float max(float a, float b, float c, float d) {
        return Math.max(Math.max(Math.max(a, b), c), d);
    }

    /**
     * Returns the maximum of 4 double numbers.
     * @param a a number.
     * @param b a number.
     * @param c a number.
     * @param d a number.
     * @return the maximum.
     */
    public static double max(double a, double b, double c, double d) {
        return Math.max(Math.max(Math.max(a, b), c), d);
    }

    /**
     * Returns the minimum value of an array.
     * @param array the array.
     * @return the minimum.
     */
    public static int min(int[] array) {
        int min = array[0];

        for (int n : array) {
            if (n < min) {
                min = n;
            }
        }

        return min;
    }

    /**
     * Returns the minimum value of an array.
     * @param array the array.
     * @return the minimum.
     */
    public static float min(float[] array) {
        float min = Float.POSITIVE_INFINITY;

        for (float n : array) {
            if (n < min) {
                min = n;
            }
        }

        return min;
    }

    /**
     * Returns the minimum value of an array.
     * @param array the array.
     * @return the minimum.
     */
    public static double min(double[] array) {
        double min = Double.POSITIVE_INFINITY;

        for (double n : array) {
            if (n < min) {
                min = n;
            }
        }

        return min;
    }

    /**
     * Returns the index of minimum value of an array.
     * @param array the array.
     * @return the index of minimum.
     */
    public static int whichMin(int[] array) {
        int min = array[0];
        int which = 0;

        for (int i = 1; i < array.length; i++) {
            if (array[i] < min) {
                min = array[i];
                which = i;
            }
        }

        return which;
    }

    /**
     * Returns the index of minimum value of an array.
     * @param array the array.
     * @return the index of minimum.
     */
    public static int whichMin(float[] array) {
        float min = Float.POSITIVE_INFINITY;
        int which = 0;

        for (int i = 0; i < array.length; i++) {
            if (array[i] < min) {
                min = array[i];
                which = i;
            }
        }

        return which;
    }

    /**
     * Returns the index of minimum value of an array.
     * @param array the array.
     * @return the index of minimum.
     */
    public static int whichMin(double[] array) {
        double min = Double.POSITIVE_INFINITY;
        int which = 0;

        for (int i = 0; i < array.length; i++) {
            if (array[i] < min) {
                min = array[i];
                which = i;
            }
        }

        return which;
    }

    /**
     * Returns the maximum value of an array.
     * @param array the array.
     * @return the index of maximum.
     */
    public static int max(int[] array) {
        int max = array[0];

        for (int n : array) {
            if (n > max) {
                max = n;
            }
        }

        return max;
    }

    /**
     * Returns the maximum value of an array.
     * @param array the array.
     * @return the index of maximum.
     */
    public static float max(float[] array) {
        float max = Float.NEGATIVE_INFINITY;

        for (float n : array) {
            if (n > max) {
                max = n;
            }
        }

        return max;
    }

    /**
     * Returns the maximum value of an array.
     * @param array the array.
     * @return the index of maximum.
     */
    public static double max(double[] array) {
        double max = Double.NEGATIVE_INFINITY;

        for (double n : array) {
            if (n > max) {
                max = n;
            }
        }

        return max;
    }

    /**
     * Returns the index of maximum value of an array.
     * @param array the array.
     * @return the index of maximum.
     */
    public static int whichMax(int[] array) {
        int max = array[0];
        int which = 0;

        for (int i = 1; i < array.length; i++) {
            if (array[i] > max) {
                max = array[i];
                which = i;
            }
        }

        return which;
    }

    /**
     * Returns the index of maximum value of an array.
     * @param array the array.
     * @return the index of maximum.
     */
    public static int whichMax(float[] array) {
        float max = Float.NEGATIVE_INFINITY;
        int which = 0;

        for (int i = 0; i < array.length; i++) {
            if (array[i] > max) {
                max = array[i];
                which = i;
            }
        }

        return which;
    }

    /**
     * Returns the index of maximum value of an array.
     * @param array the array.
     * @return the index of maximum.
     */
    public static int whichMax(double[] array) {
        double max = Double.NEGATIVE_INFINITY;
        int which = 0;

        for (int i = 0; i < array.length; i++) {
            if (array[i] > max) {
                max = array[i];
                which = i;
            }
        }

        return which;
    }

    /**
     * Returns the minimum of a matrix.
     * @param matrix the matrix.
     * @return the minimum.
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
     * @param matrix the matrix.
     * @return the minimum.
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
     * @param matrix the matrix.
     * @return the maximum.
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
     * @param matrix the matrix.
     * @return the maximum.
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
     * Returns the index of minimum value of a matrix.
     * @param matrix the matrix.
     * @return the index of minimum.
     */
    public static IntPair whichMin(double[][] matrix) {
        double min = Double.POSITIVE_INFINITY;
        int whichRow = 0;
        int whichCol = 0;

        for (int i = 0; i < matrix.length; i++) {
            for (int j = 0; j < matrix[i].length; j++) {
                if (matrix[i][j] < min) {
                    min = matrix[i][j];
                    whichRow = i;
                    whichCol = j;
                }
            }
        }

        return new IntPair(whichRow, whichCol);
    }

    /**
     * Returns the index of maximum value of a matrix.
     * @param matrix the matrix.
     * @return the index of maximum.
     */
    public static IntPair whichMax(double[][] matrix) {
        double max = Double.NEGATIVE_INFINITY;
        int whichRow = 0;
        int whichCol = 0;

        for (int i = 0; i < matrix.length; i++) {
            for (int j = 0; j < matrix[i].length; j++) {
                if (matrix[i][j] > max) {
                    max = matrix[i][j];
                    whichRow = i;
                    whichCol = j;
                }
            }
        }

        return new IntPair(whichRow, whichCol);
    }

    /**
     * Returns the matrix transpose.
     * @param matrix the matrix.
     * @return the transpose.
     */
    public static double[][] transpose(double[][] matrix) {
        int m = matrix.length;
        int n = matrix[0].length;

        double[][] t = new double[n][m];
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                t[j][i] = matrix[i][j];
            }
        }

        return t;
    }

    /**
     * Returns the row minimum of a matrix.
     * @param matrix the matrix.
     * @return the row minimums.
     */
    public static int[] rowMin(int[][] matrix) {
        int[] x = new int[matrix.length];

        for (int i = 0; i < x.length; i++) {
            x[i] = min(matrix[i]);
        }

        return x;
    }

    /**
     * Returns the row maximum of a matrix.
     * @param matrix the matrix.
     * @return the row maximums.
     */
    public static int[] rowMax(int[][] matrix) {
        int[] x = new int[matrix.length];

        for (int i = 0; i < x.length; i++) {
            x[i] = max(matrix[i]);
        }

        return x;
    }

    /**
     * Returns the row sums of a matrix.
     * @param matrix the matrix.
     * @return the row sums.
     */
    public static long[] rowSums(int[][] matrix) {
        long[] x = new long[matrix.length];

        for (int i = 0; i < x.length; i++) {
            x[i] = sum(matrix[i]);
        }

        return x;
    }

    /**
     * Returns the row minimum of a matrix.
     * @param matrix the matrix.
     * @return the row minimums.
     */
    public static double[] rowMin(double[][] matrix) {
        double[] x = new double[matrix.length];

        for (int i = 0; i < x.length; i++) {
            x[i] = min(matrix[i]);
        }

        return x;
    }

    /**
     * Returns the row maximum of a matrix.
     * @param matrix the matrix.
     * @return the row maximums.
     */
    public static double[] rowMax(double[][] matrix) {
        double[] x = new double[matrix.length];

        for (int i = 0; i < x.length; i++) {
            x[i] = max(matrix[i]);
        }

        return x;
    }

    /**
     * Returns the row sums of a matrix.
     * @param matrix the matrix.
     * @return the row sums.
     */
    public static double[] rowSums(double[][] matrix) {
        double[] x = new double[matrix.length];

        for (int i = 0; i < x.length; i++) {
            x[i] = sum(matrix[i]);
        }

        return x;
    }

    /**
     * Returns the row means of a matrix.
     * @param matrix the matrix.
     * @return the row means.
     */
    public static double[] rowMeans(double[][] matrix) {
        double[] x = new double[matrix.length];

        for (int i = 0; i < x.length; i++) {
            x[i] = mean(matrix[i]);
        }

        return x;
    }

    /**
     * Returns the row standard deviations of a matrix.
     * @param matrix the matrix.
     * @return the row standard deviations.
     */
    public static double[] rowSds(double[][] matrix) {
        double[] x = new double[matrix.length];

        for (int i = 0; i < x.length; i++) {
            x[i] = sd(matrix[i]);
        }

        return x;
    }

    /**
     * Returns the column minimum of a matrix.
     * @param matrix the matrix.
     * @return the column minimums.
     */
    public static int[] colMin(int[][] matrix) {
        int[] x = new int[matrix[0].length];
        Arrays.fill(x, Integer.MAX_VALUE);

        for (int[] row : matrix) {
            for (int j = 0; j < x.length; j++) {
                if (x[j] > row[j]) {
                    x[j] = row[j];
                }
            }
        }

        return x;
    }

    /**
     * Returns the column maximum of a matrix.
     * @param matrix the matrix.
     * @return the column maximums.
     */
    public static int[] colMax(int[][] matrix) {
        int[] x = new int[matrix[0].length];
        Arrays.fill(x, Integer.MIN_VALUE);

        for (int[] row : matrix) {
            for (int j = 0; j < x.length; j++) {
                if (x[j] < row[j]) {
                    x[j] = row[j];
                }
            }
        }

        return x;
    }

    /**
     * Returns the column sums of a matrix.
     * @param matrix the matrix.
     * @return the column sums.
     */
    public static long[] colSums(int[][] matrix) {
        long[] x = new long[matrix[0].length];

        for (int[] row : matrix) {
            for (int j = 0; j < x.length; j++) {
                x[j] += row[j];
            }
        }

        return x;
    }

    /**
     * Returns the column minimum of a matrix.
     * @param matrix the matrix.
     * @return the column minimums.
     */
    public static double[] colMin(double[][] matrix) {
        double[] x = new double[matrix[0].length];
        Arrays.fill(x, Double.POSITIVE_INFINITY);

        for (double[] row : matrix) {
            for (int j = 0; j < x.length; j++) {
                if (x[j] > row[j]) {
                    x[j] = row[j];
                }
            }
        }

        return x;
    }

    /**
     * Returns the column maximum of a matrix.
     * @param matrix the matrix.
     * @return the column maximums.
     */
    public static double[] colMax(double[][] matrix) {
        double[] x = new double[matrix[0].length];
        Arrays.fill(x, Double.NEGATIVE_INFINITY);

        for (double[] row : matrix) {
            for (int j = 0; j < x.length; j++) {
                if (x[j] < row[j]) {
                    x[j] = row[j];
                }
            }
        }

        return x;
    }

    /**
     * Returns the column sums of a matrix.
     * @param matrix the matrix.
     * @return the column sums.
     */
    public static double[] colSums(double[][] matrix) {
        double[] x = matrix[0].clone();

        for (int i = 1; i < matrix.length; i++) {
            for (int j = 0; j < x.length; j++) {
                x[j] += matrix[i][j];
            }
        }

        return x;
    }

    /**
     * Returns the column means of a matrix.
     * @param matrix the matrix.
     * @return the column means.
     */
    public static double[] colMeans(double[][] matrix) {
        double[] x = matrix[0].clone();

        for (int i = 1; i < matrix.length; i++) {
            for (int j = 0; j < x.length; j++) {
                x[j] += matrix[i][j];
            }
        }

        scale(1.0 / matrix.length, x);

        return x;
    }

    /**
     * Returns the column standard deviations of a matrix.
     * @param matrix the matrix.
     * @return the column standard deviations.
     */
    public static double[] colSds(double[][] matrix) {
        if (matrix.length < 2) {
            throw new IllegalArgumentException("matrix length is less than 2.");
        }

        int p = matrix[0].length;
        double[] sum = new double[p];
        double[] sumsq = new double[p];
        for (double[] row : matrix) {
            for (int i = 0; i < p; i++) {
                sum[i] += row[i];
                sumsq[i] += row[i] * row[i];
            }
        }

        int n = matrix.length - 1;
        for (int i = 0; i < p; i++) {
            sumsq[i] = sqrt(sumsq[i] / n - (sum[i] / matrix.length) * (sum[i] / n));
        }

        return sumsq;
    }

    /**
     * Returns the sum of an array.
     * @param x the array.
     * @return the sum.
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
     * @param x the array.
     * @return the sum.
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
     * @param x the array.
     * @return the sum.
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
     * @param x the array.
     * @return the sum.
     */
    public static double sum(double[] x) {
        double sum = 0.0;

        for (double n : x) {
            sum += n;
        }

        return sum;
    }

    /**
     * Find the median of an array of type int.
     * The input array will be rearranged.
     * @param x the array.
     * @return the median.
     */
    public static int median(int[] x) {
        return QuickSelect.median(x);
    }

    /**
     * Find the median of an array of type float.
     * The input array will be rearranged.
     * @param x the array.
     * @return the median.
     */
    public static float median(float[] x) {
        return QuickSelect.median(x);
    }

    /**
     * Find the median of an array of type double.
     * The input array will be rearranged.
     * @param x the array.
     * @return the median.
     */
    public static double median(double[] x) {
        return QuickSelect.median(x);
    }

    /**
     * Find the median of an array of type double.
     * The input array will be rearranged.
     * @param x the array.
     * @param <T> the data type of array elements.
     * @return the median.
     */
    public static <T extends Comparable<? super T>> T median(T[] x) {
        return QuickSelect.median(x);
    }

    /**
     * Find the first quantile (p = 1/4) of an array of type int.
     * The input array will be rearranged.
     * @param x the array.
     * @return the first quantile.
     */
    public static int q1(int[] x) {
        return QuickSelect.q1(x);
    }

    /**
     * Find the first quantile (p = 1/4) of an array of type float.
     * The input array will be rearranged.
     * @param x the array.
     * @return the first quantile.
     */
    public static float q1(float[] x) {
        return QuickSelect.q1(x);
    }

    /**
     * Find the first quantile (p = 1/4) of an array of type double.
     * The input array will be rearranged.
     * @param x the array.
     * @return the first quantile.
     */
    public static double q1(double[] x) {
        return QuickSelect.q1(x);
    }

    /**
     * Find the first quantile (p = 1/4) of an array of type double.
     * The input array will be rearranged.
     * @param x the array.
     * @param <T> the data type of array elements.
     * @return the first quantile.
     */
    public static <T extends Comparable<? super T>> T q1(T[] x) {
        return QuickSelect.q1(x);
    }

    /**
     * Find the third quantile (p = 3/4) of an array of type int.
     * The input array will be rearranged.
     * @param x the array.
     * @return the third quantile.
     */
    public static int q3(int[] x) {
        return QuickSelect.q3(x);
    }

    /**
     * Find the third quantile (p = 3/4) of an array of type float.
     * The input array will be rearranged.
     * @param x the array.
     * @return the third quantile.
     */
    public static float q3(float[] x) {
        return QuickSelect.q3(x);
    }

    /**
     * Find the third quantile (p = 3/4) of an array of type double.
     * The input array will be rearranged.
     * @param x the array.
     * @return the third quantile.
     */
    public static double q3(double[] x) {
        return QuickSelect.q3(x);
    }

    /**
     * Returns the breakpoints that break data into equal-sized buckets
     * based on sample quantiles.
     * @param x the data set, which will be sorted.
     * @param q the number of quantiles.
     * @return the breakpoints between buckets.
     */
    static double[] qcut(double[] x, int q) {
        int n = x.length;
        double[] cuts = new double[q - 1];

        Arrays.sort(x);
        for (int i = 0; i < q - 1; i++) {
            cuts[i] = x[(i + 1) * n / q];
        }
        return cuts;
    }

    /**
     * Find the third quantile (p = 3/4) of an array of type double.
     * The input array will be rearranged.
     * @param x the array.
     * @param <T> the data type of array elements.
     * @return the third quantile.
     */
    public static <T extends Comparable<? super T>> T q3(T[] x) {
        return QuickSelect.q3(x);
    }

    /**
     * Returns the mean of an array.
     * @param array the array.
     * @return the mean.
     */
    public static double mean(int[] array) {
        return (double) sum(array) / array.length;
    }

    /**
     * Returns the mean of an array.
     * @param array the array.
     * @return the mean.
     */
    public static double mean(float[] array) {
        return sum(array) / array.length;
    }

    /**
     * Returns the mean of an array.
     * @param array the array.
     * @return the mean.
     */
    public static double mean(double[] array) {
        return sum(array) / array.length;
    }

    /**
     * Returns the variance of an array.
     * @param array the array.
     * @return the variance.
     */
    public static double var(int[] array) {
        if (array.length < 2) {
            throw new IllegalArgumentException("Array length is less than 2.");
        }

        double sum = 0.0;
        double sumsq = 0.0;
        for (int xi : array) {
            sum += xi;
            sumsq += xi * xi;
        }

        int n = array.length - 1;
        return sumsq / n - (sum / array.length) * (sum / n);
    }

    /**
     * Returns the variance of an array.
     * @param array the array.
     * @return the variance.
     */
    public static double var(float[] array) {
        if (array.length < 2) {
            throw new IllegalArgumentException("Array length is less than 2.");
        }

        double sum = 0.0;
        double sumsq = 0.0;
        for (float xi : array) {
            sum += xi;
            sumsq += xi * xi;
        }

        int n = array.length - 1;
        return sumsq / n - (sum / array.length) * (sum / n);
    }

    /**
     * Returns the variance of an array.
     * @param array the array.
     * @return the variance.
     */
    public static double var(double[] array) {
        if (array.length < 2) {
            throw new IllegalArgumentException("Array length is less than 2.");
        }

        double sum = 0.0;
        double sumsq = 0.0;
        for (double xi : array) {
            sum += xi;
            sumsq += xi * xi;
        }

        int n = array.length - 1;
        return sumsq / n - (sum / array.length) * (sum / n);
    }

    /**
     * Returns the standard deviation of an array.
     * @param array the array.
     * @return the standard deviation.
     */
    public static double sd(int[] array) {
        return sqrt(var(array));
    }

    /**
     * Returns the standard deviation of an array.
     * @param array the array.
     * @return the standard deviation.
     */
    public static double sd(float[] array) {
        return sqrt(var(array));
    }

    /**
     * Returns the standard deviation of an array.
     * @param array the array.
     * @return the standard deviation.
     */
    public static double sd(double[] array) {
        return sqrt(var(array));
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
     *
     * @param array the array.
     * @return the median absolute deviation.
     */
    public static double mad(int[] array) {
        int m = median(array);
        for (int i = 0; i < array.length; i++) {
            array[i] = abs(array[i] - m);
        }

        return median(array);
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
     *
     * @param array the array.
     * @return the median abolute deviation.
     */
    public static double mad(float[] array) {
        float m = median(array);
        for (int i = 0; i < array.length; i++) {
            array[i] = abs(array[i] - m);
        }

        return median(array);
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
     *
     * @param array the array.
     * @return the median abolute deviation.
     */
    public static double mad(double[] array) {
        double m = median(array);
        for (int i = 0; i < array.length; i++) {
            array[i] = abs(array[i] - m);
        }

        return median(array);
    }

    /**
     * The Euclidean distance on binary sparse arrays,
     * which are the indices of nonzero elements in ascending order.
     *
     * @param a a binary sparse vector.
     * @param b a binary sparse vector.
     * @return the Euclidean distance.
     */
    public static double distance(int[] a, int[] b) {
        return sqrt(squaredDistance(a, b));
    }

    /**
     * The Euclidean distance.
     *
     * @param a a vector.
     * @param b a vector.
     * @return the Euclidean distance.
     */
    public static double distance(float[] a, float[] b) {
        return sqrt(squaredDistance(a, b));
    }

    /**
     * The Euclidean distance.
     *
     * @param a a vector.
     * @param b a vector.
     * @return the Euclidean distance.
     */
    public static double distance(double[] a, double[] b) {
        return sqrt(squaredDistance(a, b));
    }

    /**
     * The Euclidean distance.
     *
     * @param a a sparse vector.
     * @param b a sparse vector.
     * @return the Euclidean distance.
     */
    public static double distance(SparseArray a, SparseArray b) {
        return sqrt(squaredDistance(a, b));
    }

    /**
     * The squared Euclidean distance on binary sparse arrays,
     * which are the indices of nonzero elements in ascending order.
     *
     * @param a a binary sparse vector.
     * @param b a binary sparse vector.
     * @return the square of Euclidean distance.
     */
    public static double squaredDistance(int[] a, int[] b) {
        double d = 0.0;

        int p1 = 0, p2 = 0;
        while (p1 < a.length && p2 < b.length) {
            int i1 = a[p1];
            int i2 = b[p2];
            if (i1 == i2) {
                p1++;
                p2++;
            } else if (i1 > i2) {
                d++;
                p2++;
            } else {
                d++;
                p1++;
            }
        }

        d += a.length - p1;
        d += b.length - p2;

        return d;
    }

    /**
     * The squared Euclidean distance.
     *
     * @param a a vector.
     * @param b a vector.
     * @return the square of Euclidean distance.
     */
    public static double squaredDistance(float[] a, float[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("Input vector sizes are different.");
        }

        switch (a.length) {
            case 2: {
                double d0 = (double) a[0] - (double) b[0];
                double d1 = (double) a[1] - (double) b[1];
                return d0 * d0 + d1 * d1;
            }

            case 3: {
                double d0 = (double) a[0] - (double) b[0];
                double d1 = (double) a[1] - (double) b[1];
                double d2 = (double) a[2] - (double) b[2];
                return d0 * d0 + d1 * d1 + d2 * d2;
            }

            case 4: {
                double d0 = (double) a[0] - (double) b[0];
                double d1 = (double) a[1] - (double) b[1];
                double d2 = (double) a[2] - (double) b[2];
                double d3 = (double) a[3] - (double) b[3];
                return d0 * d0 + d1 * d1 + d2 * d2 + d3 * d3;
            }
        }

        double sum = 0.0;
        for (int i = 0; i < a.length; i++) {
            // covert x and y for better precision
            double d = (double) a[i] - (double) b[i];
            sum += d * d;
        }

        return sum;
    }

    /**
     * The squared Euclidean distance.
     *
     * @param a a vector.
     * @param b a vector.
     * @return the square of Euclidean distance.
     */
    public static double squaredDistance(double[] a, double[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("Input vector sizes are different.");
        }

        switch (a.length) {
            case 2: {
                double d0 = a[0] - b[0];
                double d1 = a[1] - b[1];
                return d0 * d0 + d1 * d1;
            }

            case 3: {
                double d0 = a[0] - b[0];
                double d1 = a[1] - b[1];
                double d2 = a[2] - b[2];
                return d0 * d0 + d1 * d1 + d2 * d2;
            }

            case 4: {
                double d0 = a[0] - b[0];
                double d1 = a[1] - b[1];
                double d2 = a[2] - b[2];
                double d3 = a[3] - b[3];
                return d0 * d0 + d1 * d1 + d2 * d2 + d3 * d3;
            }
        }

        double sum = 0.0;
        for (int i = 0; i < a.length; i++) {
            double d = a[i] - b[i];
            sum += d * d;
        }

        return sum;
    }

    /**
     * The Euclidean distance on sparse arrays.
     *
     * @param a a sparse vector.
     * @param b a sparse vector.
     * @return the square of Euclidean distance.
     */
    public static double squaredDistance(SparseArray a, SparseArray b) {
        Iterator<SparseArray.Entry> it1 = a.iterator();
        Iterator<SparseArray.Entry> it2 = b.iterator();
        SparseArray.Entry e1 = it1.hasNext() ? it1.next() : null;
        SparseArray.Entry e2 = it2.hasNext() ? it2.next() : null;

        double sum = 0.0;
        while (e1 != null && e2 != null) {
            if (e1.i == e2.i) {
                sum += pow2(e1.x - e2.x);
                e1 = it1.hasNext() ? it1.next() : null;
                e2 = it2.hasNext() ? it2.next() : null;
            } else if (e1.i > e2.i) {
                sum += pow2(e2.x);
                e2 = it2.hasNext() ? it2.next() : null;
            } else {
                sum += pow2(e1.x);
                e1 = it1.hasNext() ? it1.next() : null;
            }
        }
        
        while (it1.hasNext()) {
            double d = it1.next().x;
            sum += d * d;
        }

        while (it2.hasNext()) {
            double d = it2.next().x;
            sum += d * d;
        }
        
        return sum;
    }

    /**
     * The squared Euclidean distance with handling missing values (represented as NaN).
     * NaN will be treated as missing values and will be excluded from the
     * calculation. Let m be the number nonmissing values, and n be the
     * number of all values. The returned distance is (n * d / m),
     * where d is the square of distance between nonmissing values.
     *
     * @param x a vector.
     * @param y a vector.
     * @return the square of Euclidean distance.
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
     * Returns the pairwise distance matrix of multiple binary sparse vectors.
     * @param x binary sparse vectors, which are the indices of
     *          nonzero elements in ascending order.
     * @return the full pairwise distance matrix.
     */
    public static Matrix pdist(int[][] x) {
        return pdist(x, false);
    }

    /**
     * Returns the pairwise distance matrix of multiple binary sparse vectors.
     * @param x binary sparse vectors, which are the indices of
     *          nonzero elements in ascending order.
     * @param squared If true, compute the squared Euclidean distance.
     * @return the pairwise distance matrix.
     */
    public static Matrix pdist(int[][] x, boolean squared) {
        int n = x.length;
        double[][] dist = new double[n][n];

        pdist(x, dist, squared ? MathEx::squaredDistance : MathEx::distance);
        return Matrix.of(dist);
    }

    /**
     * Returns the pairwise distance matrix of multiple vectors.
     * @param x the vectors.
     * @return the full pairwise distance matrix.
     */
    public static Matrix pdist(float[][] x) {
        return pdist(x, false);
    }

    /**
     * Returns the pairwise distance matrix of multiple vectors.
     * @param x the vectors.
     * @param squared If true, compute the squared Euclidean distance.
     * @return the pairwise distance matrix.
     */
    public static Matrix pdist(float[][] x, boolean squared) {
        int n = x.length;
        double[][] dist = new double[n][n];

        pdist(x, dist, squared ? MathEx::squaredDistance : MathEx::distance);
        return Matrix.of(dist);
    }

    /**
     * Returns the pairwise distance matrix of multiple vectors.
     * @param x the vectors.
     * @return the full pairwise distance matrix.
     */
    public static Matrix pdist(double[][] x) {
        return pdist(x, false);
    }

    /**
     * Returns the pairwise distance matrix of multiple vectors.
     * @param x the vectors.
     * @param squared If true, compute the squared Euclidean distance.
     * @return the pairwise distance matrix.
     */
    public static Matrix pdist(double[][] x, boolean squared) {
        int n = x.length;
        double[][] dist = new double[n][n];

        pdist(x, dist, squared ? MathEx::squaredDistance : MathEx::distance);
        return Matrix.of(dist);
    }

    /**
     * Returns the pairwise distance matrix of multiple vectors.
     * @param x the vectors.
     * @return the full pairwise distance matrix.
     */
    public static Matrix pdist(SparseArray[] x) {
        return pdist(x, false);
    }

    /**
     * Returns the pairwise distance matrix of multiple vectors.
     * @param x the vectors.
     * @param squared If true, compute the squared Euclidean distance.
     * @return the pairwise distance matrix.
     */
    public static Matrix pdist(SparseArray[] x, boolean squared) {
        int n = x.length;
        double[][] dist = new double[n][n];

        pdist(x, dist, squared ? MathEx::squaredDistance : MathEx::distance);
        return Matrix.of(dist);
    }

    /**
     * Computes the pairwise distance matrix of multiple vectors.
     * @param x the vectors.
     * @param d The output distance matrix. It may be only the lower half.
     * @param distance the distance lambda.
     * @param <T> the data type of vectors.
     */
    public static <T> void pdist(T[] x, double[][] d, Distance<T> distance) {
        int n = x.length;

        if (d[0].length < n) {
            IntStream.range(0, n).parallel().forEach(i -> {
                T xi = x[i];
                double[] di = d[i];
                for (int j = 0; j < i; j++) {
                    di[j] = distance.d(xi, x[j]);
                }
            });
        } else {
            IntStream.range(0, n).parallel().forEach(i -> {
                T xi = x[i];
                double[] di = d[i];
                for (int j = 0; j < n; j++) {
                    di[j] = distance.d(xi, x[j]);
                }
            });
        }
    }

    /**
     * Shannon's entropy.
     * @param p the probabilities.
     * @return Shannon's entropy.
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
     *
     * @param p a probability distribution.
     * @param q a probability distribution.
     * @return Kullback-Leibler divergence
     */
    public static double KullbackLeiblerDivergence(double[] p, double[] q) {
        boolean intersection = false;
        double kl = 0.0;

        for (int i = 0; i < p.length; i++) {
            if (p[i] != 0.0 && q[i] != 0.0) {
                intersection = true;
                kl += p[i] * Math.log(p[i] / q[i]);
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
     *
     * @param p a probability distribution.
     * @param q a probability distribution.
     * @return Kullback-Leibler divergence
     */
    public static double KullbackLeiblerDivergence(SparseArray p, SparseArray q) {
        if (p.isEmpty()) {
            throw new IllegalArgumentException("p is empty.");
        }

        if (q.isEmpty()) {
            throw new IllegalArgumentException("q is empty.");
        }

        Iterator<SparseArray.Entry> pIter = p.iterator();
        Iterator<SparseArray.Entry> qIter = q.iterator();

        SparseArray.Entry a = pIter.hasNext() ? pIter.next() : null;
        SparseArray.Entry b = qIter.hasNext() ? qIter.next() : null;

        boolean intersection = false;
        double kl = 0.0;

        while (a != null && b != null) {
            if (a.i < b.i) {
                a = pIter.hasNext() ? pIter.next() : null;
            } else if (a.i > b.i) {
                b = qIter.hasNext() ? qIter.next() : null;
            } else {
                intersection = true;
                kl += a.x * Math.log(a.x / b.x);

                a = pIter.hasNext() ? pIter.next() : null;
                b = qIter.hasNext() ? qIter.next() : null;
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
     *
     * @param p a probability distribution.
     * @param q a probability distribution.
     * @return Kullback-Leibler divergence
     */
    public static double KullbackLeiblerDivergence(double[] p, SparseArray q) {
        return KullbackLeiblerDivergence(q, p);
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
     *
     * @param p a probability distribution.
     * @param q a probability distribution.
     * @return Kullback-Leibler divergence
     */
    public static double KullbackLeiblerDivergence(SparseArray p, double[] q) {
        if (p.isEmpty()) {
            throw new IllegalArgumentException("p is empty.");
        }

        Iterator<SparseArray.Entry> iter = p.iterator();

        boolean intersection = false;
        double kl = 0.0;
        while (iter.hasNext()) {
            SparseArray.Entry b = iter.next();
            int i = b.i;
            if (q[i] > 0) {
                intersection = true;
                kl += b.x * Math.log(b.x / q[i]);
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
     *
     * @param p a probability distribution.
     * @param q a probability distribution.
     * @return Jensen-Shannon divergence
     */
    public static double JensenShannonDivergence(double[] p, double[] q) {
        double[] m = new double[p.length];
        for (int i = 0; i < m.length; i++) {
            m[i] = (p[i] + q[i]) / 2;
        }

        return (KullbackLeiblerDivergence(p, m) + KullbackLeiblerDivergence(q, m)) / 2;
    }

    /**
     * Jensen-Shannon divergence JS(P||Q) = (KL(P||M) + KL(Q||M)) / 2, where
     * M = (P+Q)/2. The Jensen-Shannon divergence is a popular
     * method of measuring the similarity between two probability distributions.
     * It is also known as information radius or total divergence to the average.
     * It is based on the Kullback-Leibler divergence, with the difference that
     * it is always a finite value. The square root of the Jensen-Shannon divergence
     * is a metric.
     *
     * @param p a probability distribution.
     * @param q a probability distribution.
     * @return Jensen-Shannon divergence
     */
    public static double JensenShannonDivergence(SparseArray p, SparseArray q) {
        if (p.isEmpty()) {
            throw new IllegalArgumentException("p is empty.");
        }

        if (q.isEmpty()) {
            throw new IllegalArgumentException("q is empty.");
        }

        Iterator<SparseArray.Entry> pIter = p.iterator();
        Iterator<SparseArray.Entry> qIter = q.iterator();

        SparseArray.Entry a = pIter.hasNext() ? pIter.next() : null;
        SparseArray.Entry b = qIter.hasNext() ? qIter.next() : null;

        double js = 0.0;

        while (a != null && b != null) {
            if (a.i < b.i) {
                double mi = a.x / 2;
                js += a.x * Math.log(a.x / mi);
                a = pIter.hasNext() ? pIter.next() : null;
            } else if (a.i > b.i) {
                double mi = b.x / 2;
                js += b.x * Math.log(b.x / mi);
                b = qIter.hasNext() ? qIter.next() : null;
            } else {
                double mi = (a.x + b.x) / 2;
                js += a.x * Math.log(a.x / mi) + b.x * Math.log(b.x / mi);

                a = pIter.hasNext() ? pIter.next() : null;
                b = qIter.hasNext() ? qIter.next() : null;
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
     *
     * @param p a probability distribution.
     * @param q a probability distribution.
     * @return Jensen-Shannon divergence
     */
    public static double JensenShannonDivergence(double[] p, SparseArray q) {
        return JensenShannonDivergence(q, p);
    }

    /**
     * Jensen-Shannon divergence JS(P||Q) = (KL(P||M) + KL(Q||M)) / 2, where
     * M = (P+Q)/2. The Jensen-Shannon divergence is a popular
     * method of measuring the similarity between two probability distributions.
     * It is also known as information radius or total divergence to the average.
     * It is based on the Kullback-Leibler divergence, with the difference that
     * it is always a finite value. The square root of the Jensen-Shannon divergence
     * is a metric.
     *
     * @param p a probability distribution.
     * @param q a probability distribution.
     * @return Jensen-Shannon divergence
     */
    public static double JensenShannonDivergence(SparseArray p, double[] q) {
        if (p.isEmpty()) {
            throw new IllegalArgumentException("p is empty.");
        }

        Iterator<SparseArray.Entry> iter = p.iterator();

        double js = 0.0;
        while (iter.hasNext()) {
            SparseArray.Entry b = iter.next();
            int i = b.i;
            double mi = (b.x + q[i]) / 2;
            js += b.x * Math.log(b.x / mi);
            if (q[i] > 0) {
                js += q[i] * Math.log(q[i] / mi);
            }
        }

        return js / 2;
    }

    /**
     * Returns the dot product between two binary sparse arrays,
     * which are the indices of nonzero elements in ascending order.
     * @param a a binary sparse vector.
     * @param b a binary sparse vector.
     * @return the dot product.
     */
    public static int dot(int[] a, int[] b) {
        int sum = 0;

        for (int p1 = 0, p2 = 0; p1 < a.length && p2 < b.length; ) {
            int i1 = a[p1];
            int i2 = b[p2];
            if (i1 == i2) {
                sum++;
                p1++;
                p2++;
            } else if (i1 > i2) {
                p2++;
            } else {
                p1++;
            }
        }

        return sum;
    }

    /**
     * Returns the dot product between two vectors.
     * @param a a vector.
     * @param b a vector.
     * @return the dot product.
     */
    public static float dot(float[] a, float[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("Arrays have different length.");
        }

        float sum = 0.0F;
        for (int i = 0; i < a.length; i++) {
            sum += a[i] * b[i];
        }

        return sum;
    }

    /**
     * Returns the dot product between two vectors.
     * @param a a vector.
     * @param b a vector.
     * @return the dot product.
     */
    public static double dot(double[] a, double[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("Arrays have different length.");
        }

        double sum = 0.0;
        for (int i = 0; i < a.length; i++) {
            sum += a[i] * b[i];
        }

        return sum;
    }

    /**
     * Returns the dot product between two sparse arrays.
     * @param x a sparse vector.
     * @param y a sparse vector.
     * @return the dot product.
     */
    public static double dot(SparseArray x, SparseArray y) {
        Iterator<SparseArray.Entry> it1 = x.iterator();
        Iterator<SparseArray.Entry> it2 = y.iterator();
        SparseArray.Entry e1 = it1.hasNext() ? it1.next() : null;
        SparseArray.Entry e2 = it2.hasNext() ? it2.next() : null;

        double sum = 0.0;
        while (e1 != null && e2 != null) {
            if (e1.i == e2.i) {
                sum += e1.x * e2.x;
                e1 = it1.hasNext() ? it1.next() : null;
                e2 = it2.hasNext() ? it2.next() : null;
            } else if (e1.i > e2.i) {
                e2 = it2.hasNext() ? it2.next() : null;
            } else {
                e1 = it1.hasNext() ? it1.next() : null;
            }
        }
        
        return sum;
    }

    /**
     * Returns the pairwise dot product matrix of binary sparse vectors.
     * @param x the binary sparse vectors, which are the indices of
     *          nonzero elements in ascending order.
     * @return the dot product matrix.
     */
    public static Matrix pdot(int[][] x) {
        int n = x.length;

        Matrix matrix = new Matrix(n, n);
        matrix.uplo(UPLO.LOWER);
        IntStream.range(0, n).parallel().forEach(j -> {
            int[] xj = x[j];
            for (int i = 0; i < n; i++) {
                matrix.set(i, j, dot(x[i], xj));
            }
        });

        return matrix;
    }

    /**
     * Returns the pairwise dot product matrix of float vectors.
     * @param x the vectors.
     * @return the dot product matrix.
     */
    public static Matrix pdot(float[][] x) {
        int n = x.length;

        Matrix matrix = new Matrix(n, n);
        matrix.uplo(UPLO.LOWER);
        IntStream.range(0, n).parallel().forEach(j -> {
            float[] xj = x[j];
            for (int i = 0; i < n; i++) {
                matrix.set(i, j, dot(x[i], xj));
            }
        });

        return matrix;
    }

    /**
     * Returns the pairwise dot product matrix of double vectors.
     * @param x the vectors.
     * @return the dot product matrix.
     */
    public static Matrix pdot(double[][] x) {
        int n = x.length;

        Matrix matrix = new Matrix(n, n);
        matrix.uplo(UPLO.LOWER);
        IntStream.range(0, n).parallel().forEach(j -> {
            double[] xj = x[j];
            for (int i = 0; i < n; i++) {
                matrix.set(i, j, dot(x[i], xj));
            }
        });

        return matrix;
    }

    /**
     * Returns the pairwise dot product matrix of multiple vectors.
     * @param x the sparse vectors.
     * @return the dot product matrix.
     */
    public static Matrix pdot(SparseArray[] x) {
        int n = x.length;

        Matrix matrix = new Matrix(n, n);
        matrix.uplo(UPLO.LOWER);
        IntStream.range(0, n).parallel().forEach(j -> {
            SparseArray xj = x[j];
            for (int i = 0; i < n; i++) {
                matrix.set(i, j, dot(x[i], xj));
            }
        });

        return matrix;
    }

    /**
     * Returns the covariance between two vectors.
     * @param x a vector.
     * @param y a vector.
     * @return the covariance.
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
     * @param x a vector.
     * @param y a vector.
     * @return the covariance.
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
     * @param x a vector.
     * @param y a vector.
     * @return the covariance.
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
     * @param data the samples
     * @return the covariance matrix.
     */
    public static double[][] cov(double[][] data) {
        return cov(data, colMeans(data));
    }

    /**
     * Returns the sample covariance matrix.
     * @param data the samples
     * @param mu the known mean of data.
     * @return the covariance matrix.
     */
    public static double[][] cov(double[][] data, double[] mu) {
        double[][] sigma = new double[data[0].length][data[0].length];
        for (double[] datum : data) {
            for (int j = 0; j < mu.length; j++) {
                for (int k = 0; k <= j; k++) {
                    sigma[j][k] += (datum[j] - mu[j]) * (datum[k] - mu[k]);
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
     * @param x a vector.
     * @param y a vector.
     * @return the correlation coefficient.
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
     * @param x a vector.
     * @param y a vector.
     * @return the correlation coefficient.
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
     * @param x a vector.
     * @param y a vector.
     * @return the correlation coefficient.
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
     * @param data the samples
     * @return the correlation matrix.
     */
    public static double[][] cor(double[][] data) {
        return cor(data, colMeans(data));
    }

    /**
     * Returns the sample correlation matrix.
     * @param data the samples
     * @param mu the known mean of data.
     * @return the correlation matrix.
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
     * coefficient with the data converted to rankings (i.e. when variables
     * are ordinal). It can be used when there is non-parametric data and hence
     * Pearson cannot be used.
     * @param x a vector.
     * @param y a vector.
     * @return the Spearman rank correlation coefficient.
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
     * coefficient with the data converted to rankings (i.e. when variables
     * are ordinal). It can be used when there is non-parametric data and hence
     * Pearson cannot be used.
     * @param x a vector.
     * @param y a vector.
     * @return the Spearman rank correlation coefficient.
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
     * coefficient with the data converted to rankings (i.e. when variables
     * are ordinal). It can be used when there is non-parametric data and hence
     * Pearson cannot be used.
     * @param x a vector.
     * @param y a vector.
     * @return the Spearman rank correlation coefficient.
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
     * @param x a vector.
     * @param y a vector.
     * @return the Kendall rank correlation coefficient.
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

        return is / (sqrt(n1) * sqrt(n2));
    }

    /**
     * The Kendall Tau Rank Correlation Coefficient is used to measure the
     * degree of correspondence between sets of rankings where the measures
     * are not equidistant. It is used with non-parametric data.
     * @param x a vector.
     * @param y a vector.
     * @return the Kendall rank correlation coefficient.
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

        return is / (sqrt(n1) * sqrt(n2));
    }

    /**
     * The Kendall Tau Rank Correlation Coefficient is used to measure the
     * degree of correspondence between sets of rankings where the measures
     * are not equidistant. It is used with non-parametric data.
     * @param x a vector.
     * @param y a vector.
     * @return the Kendall rank correlation coefficient.
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

        return is / (sqrt(n1) * sqrt(n2));
    }

    /**
     * L<sub>1</sub> vector norm.
     * @param x a vector.
     * @return L<sub>1</sub> norm.
     */
    public static float norm1(float[] x) {
        float norm = 0.0F;

        for (float n : x) {
            norm += abs(n);
        }

        return norm;
    }

    /**
     * L<sub>1</sub> vector norm.
     * @param x a vector.
     * @return L<sub>1</sub> norm.
     */
    public static double norm1(double[] x) {
        double norm = 0.0;

        for (double n : x) {
            norm += abs(n);
        }

        return norm;
    }

    /**
     * L<sub>2</sub> vector norm.
     * @param x a vector.
     * @return L<sub>2</sub> norm.
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
     * L<sub>2</sub> vector norm.
     * @param x a vector.
     * @return L<sub>2</sub> norm.
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
     * L<sub>&infin;</sub> vector norm that is the maximum absolute value.
     * @param x a vector.
     * @return L<sub>&infin;</sub> norm.
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
     * L<sub>&infin;</sub> vector norm. Maximum absolute value.
     * @param x a vector.
     * @return L<sub>&infin;</sub> norm.
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
     * L<sub>2</sub> vector norm.
     * @param x a vector.
     * @return L<sub>2</sub> norm.
     */
    public static float norm(float[] x) {
        return norm2(x);
    }

    /**
     * L<sub>2</sub> vector norm.
     * @param x a vector.
     * @return L<sub>2</sub> norm.
     */
    public static double norm(double[] x) {
        return norm2(x);
    }

    /**
     * Returns the cosine similarity.
     * @param a a vector.
     * @param b a vector.
     * @return the cosine similarity.
     */
    public static float cos(float[] a, float[] b) {
        return dot(a, b) / (norm2(a) * norm2(b));
    }

    /**
     * Returns the cosine similarity.
     * @param a a vector.
     * @param b a vector.
     * @return the cosine similarity.
     */
    public static double cos(double[] a, double[] b) {
        return dot(a, b) / (norm2(a) * norm2(b));
    }

    /**
     * Standardizes an array to mean 0 and variance 1.
     * @param array the array.
     */
    public static void standardize(double[] array) {
        double mu = mean(array);
        double sigma = sd(array);

        if (isZero(sigma)) {
            logger.warn("array has variance of 0.");
            return;
        }

        for (int i = 0; i < array.length; i++) {
            array[i] = (array[i] - mu) / sigma;
        }
    }

    /**
     * Scales each column of a matrix to range [0, 1].
     * @param matrix the matrix.
     */
    public static void scale(double[][] matrix) {
        int n = matrix.length;
        int p = matrix[0].length;

        double[] min = colMin(matrix);
        double[] max = colMax(matrix);

        for (int j = 0; j < p; j++) {
            double scale = max[j] - min[j];
            if (!isZero(scale)) {
                for (int i = 0; i < n; i++) {
                    matrix[i][j] = (matrix[i][j] - min[j]) / scale;
                }
            } else {
                for (int i = 0; i < n; i++) {
                    matrix[i][j] = 0.5;
                }
            }
        }
    }

    /**
     * Standardizes each column of a matrix to 0 mean and unit variance.
     * @param matrix the matrix.
     */
    public static void standardize(double[][] matrix) {
        int n = matrix.length;
        int p = matrix[0].length;

        double[] center = colMeans(matrix);
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < p; j++) {
                matrix[i][j] = matrix[i][j] - center[j];
            }
        }

        double[] scale = new double[p];
        for (int j = 0; j < p; j++) {
            for (double[] xi : matrix) {
                scale[j] += pow2(xi[j]);
            }
            scale[j] = sqrt(scale[j] / (n-1));

            if (!isZero(scale[j])) {
                for (int i = 0; i < n; i++) {
                    matrix[i][j] /= scale[j];
                }
            }
        }
    }

    /**
     * Unitizes each column of a matrix to unit length (L_2 norm).
     * @param matrix the matrix.
     */
    public static void normalize(double[][] matrix) {
        normalize(matrix, false);
    }

    /**
     * Unitizes each column of a matrix to unit length (L_2 norm).
     * @param matrix the matrix.
     * @param centerizing If true, centerize each column to 0 mean.
     */
    public static void normalize(double[][] matrix, boolean centerizing) {
        int n = matrix.length;
        int p = matrix[0].length;

        if (centerizing) {
            double[] center = colMeans(matrix);
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < p; j++) {
                    matrix[i][j] = matrix[i][j] - center[j];
                }
            }
        }

        double[] scale = new double[p];
        for (int j = 0; j < p; j++) {
            for (double[] xi : matrix) {
                scale[j] += pow2(xi[j]);
            }
            scale[j] = sqrt(scale[j]);
        }

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < p; j++) {
                if (!isZero(scale[j])) {
                    matrix[i][j] /= scale[j];
                }
            }
        }
    }

    /**
     * Unitize an array so that L<sub>2</sub> norm of array = 1.
     *
     * @param array the vector.
     */
    public static void unitize(double[] array) {
        unitize2(array);
    }

    /**
     * Unitize an array so that L<sub>1</sub> norm of array is 1.
     *
     * @param array the vector.
     */
    public static void unitize1(double[] array) {
        double n = norm1(array);

        for (int i = 0; i < array.length; i++) {
            array[i] /= n;
        }
    }

    /**
     * Unitize an array so that L<sub>2</sub> norm of array = 1.
     *
     * @param array the vector.
     */
    public static void unitize2(double[] array) {
        double n = norm(array);

        for (int i = 0; i < array.length; i++) {
            array[i] /= n;
        }
    }

    /**
     * Check if x element-wisely equals y with default epsilon 1E-7.
     * @param x an array.
     * @param y an array.
     * @return true if x element-wisely equals y.
     */
    public static boolean equals(float[] x, float[] y) {
        return equals(x, y, 1.0E-7f);
    }

    /**
     * Check if x element-wisely equals y in given precision.
     * @param x an array.
     * @param y an array.
     * @param epsilon a number close to zero.
     * @return true if x element-wisely equals y.
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
     * @param x an array.
     * @param y an array.
     * @return true if x element-wisely equals y.
     */
    public static boolean equals(double[] x, double[] y) {
        return equals(x, y, 1.0E-10);
    }

    /**
     * Check if x element-wisely equals y in given precision.
     * @param x an array.
     * @param y an array.
     * @param epsilon a number close to zero.
     * @return true if x element-wisely equals y.
     */
    public static boolean equals(double[] x, double[] y, double epsilon) {
        if (x.length != y.length) {
            throw new IllegalArgumentException(String.format("Arrays have different length: x[%d], y[%d]", x.length, y.length));
        }

        if (epsilon <= 0.0) {
            throw new IllegalArgumentException("Invalid epsilon: " + epsilon);
        }
        
        for (int i = 0; i < x.length; i++) {
            if (abs(x[i] - y[i]) > epsilon) {
                return false;
            }
        }

        return true;
    }

    /**
     * Check if x element-wisely equals y with default epsilon 1E-7.
     * @param x a two-dimensional array.
     * @param y a two-dimensional array.
     * @return true if x element-wisely equals y.
     */
    public static boolean equals(float[][] x, float[][] y) {
        return equals(x, y, 1.0E-7f);
    }

    /**
     * Check if x element-wisely equals y in given precision.
     * @param x a two-dimensional array.
     * @param y a two-dimensional array.
     * @param epsilon a number close to zero.
     * @return true if x element-wisely equals y.
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
     * @param x a two-dimensional array.
     * @param y a two-dimensional array.
     * @return true if x element-wisely equals y.
     */
    public static boolean equals(double[][] x, double[][] y) {
        return equals(x, y, 1.0E-10);
    }

    /**
     * Check if x element-wisely equals y in given precision.
     * @param x a two-dimensional array.
     * @param y a two-dimensional array.
     * @param epsilon a number close to zero.
     * @return true if x element-wisely equals y.
     */
    public static boolean equals(double[][] x, double[][] y, double epsilon) {
        if (x.length != y.length || x[0].length != y[0].length) {
            throw new IllegalArgumentException(String.format("Matrices have different rows: %d x %d vs %d x %d", x.length, x[0].length, y.length, y[0].length));
        }

        if (epsilon <= 0.0) {
            throw new IllegalArgumentException("Invalid epsilon: " + epsilon);
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
     * Tests if a floating number is zero in machine precision.
     * @param x a real number.
     * @return true if x is zero in machine precision.
     */
    public static boolean isZero(float x) {
        return isZero(x, FLOAT_EPSILON);
    }

    /**
     * Tests if a floating number is zero in given precision.
     * @param x a real number.
     * @param epsilon a number close to zero.
     * @return true if x is zero in <code>epsilon</code> precision.
     */
    public static boolean isZero(float x, float epsilon) {
        return abs(x) < epsilon;
    }

    /**
     * Tests if a floating number is zero in machine precision.
     * @param x a real number.
     * @return true if x is zero in machine precision.
     */
    public static boolean isZero(double x) {
        return isZero(x, EPSILON);
    }

    /**
     * Tests if a floating number is zero in given precision.
     * @param x a real number.
     * @param epsilon a number close to zero.
     * @return true if x is zero in <code>epsilon</code> precision.
     */
    public static boolean isZero(double x, double epsilon) {
        return abs(x) < epsilon;
    }

    /**
     * Deep clone a two-dimensional array.
     * @param x a two-dimensional array.
     * @return the deep clone.
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
     * @param x a two-dimensional array.
     * @return the deep clone.
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
     * @param x a two-dimensional array.
     * @return the deep clone.
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
     * @param x an array.
     * @param i the index of first element.
     * @param j the index of second element.
     */
    public static void swap(int[] x, int i, int j) {
        int s = x[i];
        x[i] = x[j];
        x[j] = s;
    }

    /**
     * Swap two elements of an array.
     * @param x an array.
     * @param i the index of first element.
     * @param j the index of second element.
     */
    public static void swap(float[] x, int i, int j) {
        float s = x[i];
        x[i] = x[j];
        x[j] = s;
    }

    /**
     * Swap two elements of an array.
     * @param x an array.
     * @param i the index of first element.
     * @param j the index of second element.
     */
    public static void swap(double[] x, int i, int j) {
        double s = x[i];
        x[i] = x[j];
        x[j] = s;
    }

    /**
     * Swap two elements of an array.
     * @param x an array.
     * @param i the index of first element.
     * @param j the index of second element.
     */
    public static void swap(Object[] x, int i, int j) {
        Object s = x[i];
        x[i] = x[j];
        x[j] = s;
    }

    /**
     * Swap two arrays.
     * @param x an array.
     * @param y the other array.
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
     * @param x an array.
     * @param y the other array.
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
     * @param x an array.
     * @param y the other array.
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
     * @param x an array.
     * @param y the other array.
     * @param <E> the data type of array elements.
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
     * @param x the input matrix.
     * @param y the output matrix.
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
     * Deep copy x into y.
     * @param x the input matrix.
     * @param y the output matrix.
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
     * Deep copy x into y.
     * @param x the input matrix.
     * @param y the output matrix.
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
     * @param x a vector.
     * @param y avector.
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
     * Element-wise subtraction of two arrays a -= b.
     * @param a the minuend array.
     * @param b the subtrahend array.
     */
    public static void sub(double[] a, double[] b) {
        if (b.length != a.length) {
            throw new IllegalArgumentException(String.format("Arrays have different length: a[%d] != b[%d]", a.length, b.length));
        }

        for (int i = 0; i < b.length; i++) {
            a[i] -= b[i];
        }
    }

    /**
     * Scale each element of an array by a constant x = a * x.
     * @param a the scale factor.
     * @param x the input and output vector.
     */
    public static void scale(double a, double[] x) {
        for (int i = 0; i < x.length; i++) {
            x[i] *= a;
        }
    }

    /**
     * Scale each element of an array by a constant y = a * x.
     * @param a the scale factor.
     * @param x a vector.
     * @param y the output vector.
     */
    public static void scale(double a, double[] x, double[] y) {
        for (int i = 0; i < x.length; i++) {
            y[i] = a * x[i];
        }
    }

    /**
     * Update an array by adding a multiple of another array y = a * x + y.
     * @param a the scale factor.
     * @param x a vector.
     * @param y the input and output vector.
     * @return the vector y.
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
     * @param x the base array.
     * @param n the scalar exponent.
     * @return x<sup>n</sup>
     */
    public static double[] pow(double[] x, double n) {
        double[] y = new double[x.length];
        for (int i = 0; i < x.length; i++) y[i] = Math.pow(x[i], n);
        return y;
    }

    /**
     * Find unique elements of vector.
     * @param array an integer array.
     * @return the same values as in x but with no repetitions.
     */
    public static int[] unique(int[] array) {
        return Arrays.stream(array).distinct().toArray();
    }

    /**
     * Find unique elements of vector.
     * @param array an array of strings.
     * @return the same values as in x but with no repetitions.
     */
    public static String[] unique(String[] array) {
        return Arrays.stream(array).distinct().toArray(String[]::new);
    }

    /**
     * Sorts each variable and returns the index of values in ascending order.
     * Note that the order of original array is NOT altered.
     * 
     * @param matrix a set of variables to be sorted. Each row is an instance. Each
     * column is a variable.
     * @return the index of values in ascending order
     */
    public static int[][] sort(double[][] matrix) {
        int n = matrix.length;
        int p = matrix[0].length;
        
        double[] a = new double[n];
        int[][] index = new int[p][];
        
        for (int j = 0; j < p; j++) {
            for (int i = 0; i < n; i++) {
                a[i] = matrix[i][j];
            }
            index[j] = QuickSort.sort(a);
        }
        
        return index;
    }

    /**
     * Solve the tridiagonal linear set which is of diagonal dominance
     *     |b<sub>i</sub>| {@code >} |a<sub>i</sub>| + |c<sub>i</sub>|.
     * <pre>
     *     | b0 c0  0  0  0 ...                        |
     *     | a1 b1 c1  0  0 ...                        |
     *     |  0 a2 b2 c2  0 ...                        |
     *     |                ...                        |
     *     |                ... a(n-2)  b(n-2)  c(n-2) |
     *     |                ... 0       a(n-1)  b(n-1) |
     * </pre>
     *
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
