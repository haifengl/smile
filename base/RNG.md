# SMILE — Random Number Generators

The `smile.math.random` package provides high-quality pseudorandom number
generators (PRNGs) designed to replace `java.util.Random` in performance- and
quality-sensitive code such as Monte Carlo simulations, statistical sampling,
and machine-learning algorithms.

---

## Background

Java's built-in `java.util.Random` uses a 48-bit linear congruential generator
(LCG). LCGs are fast and simple, but they have short periods, poor dimensional
uniformity, and fail many standard statistical tests. The generators in this
package offer substantially better statistical properties:

| Property | `java.util.Random` | `MersenneTwister` | `MersenneTwister64` | `UniversalGenerator` |
|---|---|---|---|---|
| Algorithm | 48-bit LCG | MT19937 | MT19937-64 | Marsaglia-Zaman-Tsang |
| Period | 2⁴⁸ | **2¹⁹⁹³⁷ − 1** | **2¹⁹⁹³⁷ − 1** | **2¹⁴⁴** |
| State size | 48 bits | 624 × 32 bit | 312 × 64 bit | 97 doubles |
| Output width | 32 bit | 32 bit | 64 bit | 53-bit double |
| Equidistribution | 1-dimensional | 623-dimensional | 311-dimensional | — |
| Thread-safe | No | No | No | No |

All three generators pass the DIEHARD battery of tests. They are **not**
cryptographically secure — use `java.security.SecureRandom` if security is a
requirement.

---

## The Interface: `RandomNumberGenerator`

All generators implement the `RandomNumberGenerator` interface:

```java
public interface RandomNumberGenerator {
    void   setSeed(long seed);        // re-seed the generator
    int    next(int numbits);         // return up to 32 random bits (unsigned)
    int    nextInt();                 // full 32-bit random int
    int    nextInt(int n);            // uniform int in [0, n)
    long   nextLong();                // full 64-bit random long
    double nextDouble();              // uniform double in [0.0, 1.0)
    void   nextDoubles(double[] d);   // fill array with uniform doubles in [0,1)

    // Default method — available on all implementations:
    default double nextGaussian();    // N(0,1) via Marsaglia polar method
}
```

### `nextGaussian()`

The default `nextGaussian()` method uses the **Marsaglia polar form** of the
Box-Muller transform. It draws pairs of uniform samples until they fall inside
the unit circle, then applies:

```
z = u · sqrt(−2 ln(s) / s),   s = u² + v²
```

This avoids the `sin`/`cos` calls of the original Box-Muller method and
produces a standard normal variate with mean 0 and variance 1.

```java
MersenneTwister rng = new MersenneTwister(42);
double z = rng.nextGaussian();   // drawn from N(0, 1)
```

---

## Generators

### `MersenneTwister` — 32-bit MT19937

The workhorse generator. Implements the original 32-bit Mersenne Twister
algorithm (MT19937) by Matsumoto and Nishimura (1998). It has an enormous
period of 2¹⁹⁹³⁷ − 1 and passes all DIEHARD tests.

**Key properties:**
- State: 624 × 32-bit integers (≈ 2.5 KB)
- Output: 32-bit integers, 53-bit doubles
- Period: 2¹⁹⁹³⁷ − 1 (the number itself has about 6,000 digits)
- 623-dimensionally equidistributed up to 32 bits

#### Construction

```java
// Default seed (19650218)
MersenneTwister mt = new MersenneTwister();

// Integer seed
MersenneTwister mt = new MersenneTwister(12345);

// Long seed (XOR-folded into 32 bits)
MersenneTwister mt = new MersenneTwister(System.nanoTime());

// Array seed — highest-quality initialization, mixes all bits thoroughly
MersenneTwister mt = new MersenneTwister();
mt.setSeed(new int[]{0x123, 0x234, 0x345, 0x456});
```

#### Basic Usage

```java
MersenneTwister mt = new MersenneTwister(42);

// Uniform integer in [0, 2^32)
int   i = mt.nextInt();

// Uniform integer in [0, n)  — unbiased rejection sampling
int   j = mt.nextInt(100);      // j ∈ [0, 100)

// Uniform double in [0.0, 1.0)
double d = mt.nextDouble();

// Fill an array
double[] buf = new double[1000];
mt.nextDoubles(buf);

// Standard normal
double z = mt.nextGaussian();   // z ~ N(0, 1)

// Re-seed for reproducibility
mt.setSeed(42);
```

#### Reproducibility

Setting the same seed produces the **exact same sequence**:

```java
MersenneTwister a = new MersenneTwister(99);
MersenneTwister b = new MersenneTwister(99);
// a.nextDouble() == b.nextDouble() for every call
```

#### Array Seeding

For the highest-quality initialization — for example, to seed from a
cryptographic source — pass an `int[]`:

```java
// Seed from SecureRandom
byte[] bytes = SecureRandom.getSeed(16);
int[]  seeds = new int[4];
for (int k = 0; k < 4; k++) {
    seeds[k] = ((bytes[k*4] & 0xFF) << 24)
             | ((bytes[k*4+1] & 0xFF) << 16)
             | ((bytes[k*4+2] & 0xFF) <<  8)
             |  (bytes[k*4+3] & 0xFF);
}
MersenneTwister mt = new MersenneTwister();
mt.setSeed(seeds);
```

---

### `MersenneTwister64` — 64-bit MT19937-64

The 64-bit variant of the Mersenne Twister that operates on 64-bit registers
(`long`). It produces the same quality of randomness as the 32-bit version but
is optimized for 64-bit platforms and generates full 64-bit output in a single
step.

**Key properties:**
- State: 312 × 64-bit integers (≈ 2.5 KB — same size as 32-bit version)
- Output: 64-bit integers, 63-bit doubles
- Period: 2¹⁹⁹³⁷ − 1 (same period, different sequence from MT19937)
- 311-dimensionally equidistributed up to 64 bits

> **Note:** `MersenneTwister` and `MersenneTwister64` produce **different**
> output sequences even for the same seed. They are independent algorithms.

#### Construction

```java
// Default seed
MersenneTwister64 mt64 = new MersenneTwister64();

// Long seed
MersenneTwister64 mt64 = new MersenneTwister64(System.nanoTime());

// Array seed — best initialization
MersenneTwister64 mt64 = new MersenneTwister64(0L);
mt64.setSeed(new long[]{0x12345L, 0x23456L, 0x34567L, 0x45678L});
```

#### Usage

All methods are identical to `MersenneTwister`:

```java
MersenneTwister64 mt64 = new MersenneTwister64(42L);

long   v  = mt64.nextLong();       // full 64-bit random long
int    i  = mt64.nextInt(1000);    // [0, 1000)
double d  = mt64.nextDouble();     // [0.0, 1.0)
double z  = mt64.nextGaussian();   // N(0, 1)
```

#### When to prefer MT64 over MT

- You need **full 64-bit output** (`nextLong()`) — MT64 produces it natively
  in one step; MT32 combines two 32-bit values.
- Your platform is 64-bit and you want higher equidistribution in the
  upper bits.
- You are generating large double arrays where the extra precision matters.

---

### `UniversalGenerator`

Based on the multiplicative congruential algorithm originally published by
Marsaglia, Zaman and Tsang ("Toward a Universal Random Number Generator", 1987)
and later improved by F. James ("A Review of Pseudo-random Number Generators").

**Key properties:**
- State: 97 doubles + 3 control values (≈ 800 bytes)
- Period: 2¹⁴⁴ ≈ 2.2 × 10⁴³
- Output: 24-bit doubles (24 bits of mantissa are random)
- Passes all DIEHARD tests
- Completely portable: bit-identical results on any platform with at least
  24-bit floating-point mantissa

The generator is somewhat slower than the Mersenne Twister variants but is
well-suited to applications that need a **simpler state representation** or
**cross-platform reproducibility** at the 24-bit precision level.

#### Construction

```java
// Default seed (54217137)
UniversalGenerator rng = new UniversalGenerator();

// Integer seed
UniversalGenerator rng = new UniversalGenerator(12345);

// Long seed
UniversalGenerator rng = new UniversalGenerator(System.nanoTime());
```

#### Usage

```java
UniversalGenerator rng = new UniversalGenerator(42);

double d = rng.nextDouble();    // [0.0, 1.0)
int    i = rng.nextInt(100);    // [0, 100)
long   v = rng.nextLong();      // full signed 64-bit long
double z = rng.nextGaussian();  // N(0, 1)
```

#### Seed Constraints

Internally, the long seed is mapped through:
```
ijkl = |seed mod 899999963|
ij   = ijkl / 30082    ∈ [0, 29900]
kl   = ijkl % 30082    ∈ [0, 30081]
```

Seeds with the same `ijkl` value will produce the same sequence. For maximum
diversity use seeds spread across `[0, 899999963)`.

---

## Integration with `MathEx`

SMILE's primary math facade `smile.math.MathEx` maintains a **thread-local**
`java.util.Random` instance per thread. The first thread uses the default seed
(for reproducibility in unit tests and benchmarks); subsequent threads receive
deterministic seeds from a pre-generated table, or a cryptographic seed if the
table is exhausted.

```java
// Set a fixed seed on the current thread's RNG (for reproducibility)
MathEx.setSeed(12345L);

// Draw random numbers from the thread-local RNG
double d   = MathEx.random();        // uniform double in [0, 1)
int    i   = MathEx.randomInt(100);  // uniform int in [0, 100)
double z   = MathEx.randn();         // N(0, 1)
```

When you need the superior statistical quality of `MersenneTwister` in
downstream algorithms, create your own instance and pass it explicitly:

```java
MersenneTwister mt = new MersenneTwister(42);

// Sample without replacement using Fisher-Yates
int[] indices = IntStream.range(0, n).toArray();
for (int i = n - 1; i > 0; i--) {
    int j = mt.nextInt(i + 1);
    int tmp = indices[i]; indices[i] = indices[j]; indices[j] = tmp;
}
```

---

## Choosing a Generator

| Situation | Recommendation |
|-----------|---------------|
| General-purpose, best quality | `MersenneTwister` |
| Full 64-bit output needed | `MersenneTwister64` |
| Cross-platform 24-bit reproducibility | `UniversalGenerator` |
| Thread-local transparent RNG | `MathEx.setSeed()` + `MathEx.random()` |
| Cryptographic / security use | `java.security.SecureRandom` |

---

## Thread Safety

**None** of the generators in this package are thread-safe. Sharing a single
instance across threads without external synchronization will corrupt the
internal state and produce correlated or repeated output.

**Recommended patterns:**

### Pattern 1 — One instance per thread

```java
// Create a separate generator per thread
ThreadLocal<MersenneTwister> threadRng = ThreadLocal.withInitial(
    () -> new MersenneTwister(System.nanoTime() ^ Thread.currentThread().getId())
);

// In each thread:
double d = threadRng.get().nextDouble();
```

### Pattern 2 — Synchronized wrapper (low contention only)

```java
MersenneTwister sharedRng = new MersenneTwister(42);

// Synchronize externally:
synchronized(sharedRng) {
    double d = sharedRng.nextDouble();
}
```

### Pattern 3 — Seed from `MathEx` for reproducibility

```java
// SMILE's own thread-local RNG, seeded deterministically per thread:
MathEx.setSeed(42L);   // seed current thread
double d = MathEx.random();
```

---

## Reproducibility

To reproduce a sequence exactly:

1. **Record the seed** before any draws.
2. **Re-seed** with the same value before replaying.

```java
long seed = 12345L;
MersenneTwister mt = new MersenneTwister(seed);

double[] run1 = new double[10];
mt.nextDoubles(run1);

// Later — exactly reproduces run1
mt.setSeed(seed);
double[] run2 = new double[10];
mt.nextDoubles(run2);

assert Arrays.equals(run1, run2);  // always true
```

For **multi-threaded reproducibility**, SMILE's `MathEx` pre-generates 128
deterministic seeds so that the first 128 threads always receive the same seed
table regardless of startup order (within a single JVM run).

---

## Statistical Properties

### Period

The MT generators have a period of 2¹⁹⁹³⁷ − 1. At 10⁹ samples per second it
would take about 4.3 × 10⁵⁹⁷⁸ years to exhaust — effectively infinite for all
practical purposes.

### Equidistribution

MT19937 is 623-dimensionally equidistributed up to 32-bit precision: in any
623-dimensional hypercube, consecutive output tuples are uniformly distributed.
This makes it suitable for high-dimensional Monte Carlo integration.

### `nextDouble` precision

| Generator | Bits of randomness in `nextDouble()` |
|-----------|--------------------------------------|
| `MersenneTwister` | 31 bits (upper 31 bits of a 32-bit word) |
| `MersenneTwister64` | 63 bits (upper 63 bits of a 64-bit word) |
| `UniversalGenerator` | 24 bits (24-bit mantissa) |
| `java.util.Random` | 26 bits |

For applications sensitive to double precision (e.g., quasi-Monte Carlo),
prefer `MersenneTwister64`.

---

## References

1. M. Matsumoto and T. Nishimura, "Mersenne Twister: A 623-Dimensionally
   Equidistributed Uniform Pseudo-Random Number Generator," *ACM Transactions
   on Modeling and Computer Simulation*, Vol. 8, No. 1, Jan. 1998, pp. 3–30.
   [PDF](http://www.math.sci.hiroshima-u.ac.jp/m-mat/MT/ARTICLES/mt.pdf)

2. G. Marsaglia, A. Zaman, and W. Tsang, "Toward a Universal Random Number
   Generator," *Statistics & Probability Letters*, Vol. 9, No. 1, 1990,
   pp. 35–39.

3. F. James, "A Review of Pseudo-random Number Generators," *Computer Physics
   Communications*, Vol. 60, 1990, pp. 329–344.


---

*SMILE — © 2010-2026 Haifeng Li. GNU GPL licensed.*

