# SMILE — Tensors and Linear Algebra

The `smile.tensor` package provides the core numerical data structures used
throughout SMILE: multi-dimensional tensors, dense and sparse matrices, vectors,
and band/symmetric packed matrices. All matrix operations delegate to
**native BLAS/LAPACK** via the Java Foreign Function & Memory (FFM) API
(Java 22+), achieving performance on a par with C/Fortran scientific libraries.

---

## Table of Contents

1. [Type Hierarchy](#1-type-hierarchy)
2. [Scalar Types](#2-scalar-types)
3. [JTensor — General N-Dimensional Tensor](#3-jtensor--general-n-dimensional-tensor)
4. [Vector — 1-D Array](#4-vector--1-d-array)
5. [DenseMatrix — 2-D Array](#5-densematrix--2-d-array)
6. [SymmMatrix — Symmetric / Packed Matrix](#6-symmmatrix--symmetric--packed-matrix)
7. [BandMatrix — Banded Matrix](#7-bandmatrix--banded-matrix)
8. [SparseMatrix — Compressed-Column Sparse](#8-sparsematrix--compressed-column-sparse)
9. [Matrix Decompositions](#9-matrix-decompositions)
10. [Iterative Solvers](#10-iterative-solvers)
11. [Performance Notes](#11-performance-notes)
12. [Quick Reference](#12-quick-reference)

---

## 1. Type Hierarchy

```
Tensor (interface)
├── AbstractTensor          — stride/shape bookkeeping, bounds-checked offset
│   └── JTensor             — on-heap N-dimensional tensor (any ScalarType)
└── Matrix (interface)
    ├── DenseMatrix         — abstract base for dense matrices (FFM memory)
    │   ├── DenseMatrix64   — Float64 concrete implementation
    │   ├── DenseMatrix32   — Float32 concrete implementation
    │   └── Vector          — 1-D slice of DenseMatrix
    │       ├── Vector64    — Float64 column / row vector
    │       └── Vector32    — Float32 column / row vector
    ├── SymmMatrix          — packed symmetric matrix (UPLO storage)
    │   ├── SymmMatrix64
    │   └── SymmMatrix32
    ├── BandMatrix          — banded matrix (compact LAPACK storage)
    │   ├── BandMatrix64
    │   └── BandMatrix32
    └── SparseMatrix        — Harwell-Boeing column-compressed sparse
```

All dense types use **column-major** (Fortran-order) off-heap `MemorySegment`
storage and are therefore directly passable to native BLAS/LAPACK routines
without any copy.

---

## 2. Scalar Types

`ScalarType` is an enum describing the element data type and its byte width:

| Constant | Bytes | Notes |
|----------|-------|-------|
| `Int8` | 1 | Signed 8-bit integer |
| `Int16` | 2 | Signed 16-bit integer |
| `Int32` | 4 | Signed 32-bit integer |
| `Int64` | 8 | Signed 64-bit integer |
| `QInt8` | 1 | Quantized signed 8-bit float |
| `QUInt8` | 1 | Quantized unsigned 8-bit float |
| `Float16` | 2 | IEEE half-precision |
| `BFloat16` | 2 | Brain float16 (8-bit exponent, 7-bit mantissa) |
| `Float32` | 4 | IEEE single-precision (**fp32**) |
| `Float64` | 8 | IEEE double-precision (**fp64**) — default |

```java
import static smile.tensor.ScalarType.*;

ScalarType t = Float64;
System.out.println(t.byteSize());       // 8
System.out.println(t.isFloating());     // true
System.out.println(t.isInteger());      // false
System.out.println(t.isCompatible(Float32)); // false
```

> **Note**: BLAS/LAPACK operations are only supported for `Float32` and
> `Float64`. Attempting other types throws `UnsupportedOperationException`.

---

## 3. JTensor — General N-Dimensional Tensor

`JTensor` stores any primitive type in an on-heap `MemorySegment` with
**row-major** (C-order) layout. It supports arbitrary rank, reshape, and
sub-tensor slicing.

### Creating a JTensor

```java
// 1-D float tensor (vector)
float[] data1 = {1f, 2f, 3f};
JTensor t1 = JTensor.of(data1, 3);

// 2-D double tensor (matrix)
double[] data2 = {1, 2, 3, 4, 5, 6};
JTensor t2 = JTensor.of(data2, 2, 3);   // shape [2, 3]

// 3-D int tensor
int[] data3 = new int[24];
JTensor t3 = JTensor.of(data3, 2, 3, 4); // shape [2, 3, 4]
```

### Shape and Dimensions

```java
int[] shape = t2.shape();   // [2, 3]
int dim   = t2.dim();       // 2
int rows  = t2.size(0);     // 2
int cols  = t2.size(1);     // 3
long len  = t2.length();    // 6
```

### Element Access

```java
// row-major [row, col] indexing
double v  = t2.getDouble(0, 2);   // element at row 0, col 2
float  f  = t1.getFloat(1);
int    i  = t3.getInt(1, 2, 3);   // element at [1,2,3] in 3-D tensor

// typed setters
t2.set(3.14, 1, 1);
t3.set(42,   0, 0, 0);
```

Accessing an out-of-bounds index throws `IndexOutOfBoundsException`.
Providing more indices than the tensor has dimensions throws
`IllegalArgumentException`. Partial indices (fewer indices than `dim()`)
select a sub-tensor slice.

### Reshape (View)

```java
// Reshape [2,3] → [3,2]; shares the backing memory
JTensor r = (JTensor) t2.reshape(3, 2);
// Total element count must match
assertThrows(IllegalArgumentException.class, () -> t2.reshape(2, 4));
```

### Sub-tensor Slicing

```java
// get(i) returns the i-th slice along dimension 0
JTensor row0 = (JTensor) t2.get(0);  // shape [3] — row 0
JTensor row1 = (JTensor) t2.get(1);  // shape [3] — row 1
```

### Updating a Sub-tensor In Place

```java
double[] row = {10, 20, 30};
JTensor newRow = JTensor.of(row, 3);
t2.set(newRow, 0);   // replaces row 0 in t2
```

---

## 4. Vector — 1-D Array

`Vector` is a specialized `DenseMatrix` with either one row or one column.
Column vectors are the standard representation. All storage is
**off-heap** via `MemorySegment` and BLAS-ready.

### Creating a Vector

```java
// From a double array
Vector v64 = Vector.column(new double[]{1.0, 2.0, 3.0});

// From a float array
Vector v32 = Vector.column(new float[]{1f, 2f, 3f});

// Zero vector of given type and length
Vector zeros = Vector.zeros(Float64, 5);
Vector ones  = Vector.ones(Float64, 5);
```

### Element Access and Mutation

```java
double x = v64.get(2);      // read element 2
v64.set(2, 99.0);           // write element 2
v64.add(0, 1.0);            // v[0] += 1.0
v64.sub(1, 0.5);            // v[1] -= 0.5
v64.mul(2, 2.0);            // v[2] *= 2.0
v64.div(0, 3.0);            // v[0] /= 3.0
v64.fill(0, v64.size(), 0.0); // fill range [0, size) with 0
v64.scale(2.0);             // v *= 2.0  (in place, returns this)
```

### Size and Type

```java
int n = v64.size();              // number of elements
int nrow = v64.nrow();           // same as size() for column vector
int ncol = v64.ncol();           // 1 for column vector
ScalarType t = v64.scalarType(); // Float64
```

### Statistics

```java
double sum  = v64.sum();
double mean = v64.mean();
double var  = v64.variance();  // sample variance (denominator n-1)
double sd   = v64.sd();        // sample std dev
double min  = v64.min();
double max  = v64.max();       // correctly handles all-negative vectors
```

### Norms

```java
double l2   = v64.norm2();    // Euclidean norm  ‖v‖₂
double l1   = v64.norm1();    // L1 norm  ‖v‖₁  (= asum for real vectors)
double linf = v64.normInf();  // ‖v‖∞ = max |vᵢ|
double asum = v64.asum();     // sum of absolute values
```

### BLAS Operations

```java
// dot product: a · b
double d = a.dot(b);

// axpy: y += alpha * x
y.axpy(3.0, x);   // y = y + 3*x

// swap contents
a.swap(b);

// index of element with largest absolute value
int idx = v64.iamax();
```

### Slice and Copy

```java
Vector slice = v64.slice(1, 4);    // view of elements [1, 4)  — shares storage
Vector copy  = v64.copy();          // deep copy
Vector part  = v64.copy(1, 4);      // copy of range [1, 4)

// Static copy between vectors
Vector.copy(src, 0, dest, 2, 3);   // copy 3 elements from src[0] to dest[2]
```

### Conversion

```java
double[] arr = v64.toArray(new double[0]);  // extract to array
float[]  f32 = v32.toArray(new float[0]);

// Build a diagonal matrix from a vector
DenseMatrix D = v64.diagflat();
```

### Softmax

```java
// Applies softmax in place; returns the index of the max probability
int maxIdx = v64.softmax();
```

---

## 5. DenseMatrix — 2-D Array

`DenseMatrix` is the main matrix type. Its subclasses `DenseMatrix64`
(Float64) and `DenseMatrix32` (Float32) store data in column-major
off-heap `MemorySegment` with a cache-optimised leading dimension.

### Factory Methods

```java
import static smile.tensor.ScalarType.*;

// From a 2-D Java array (infers Float64)
DenseMatrix A = DenseMatrix.of(new double[][]{{1,2},{3,4}});
DenseMatrix B = DenseMatrix.of(new float[][]{{1f,2f},{3f,4f}});

// Zero matrices
DenseMatrix Z64 = DenseMatrix.zeros(Float64, 3, 4);
DenseMatrix Z32 = DenseMatrix.zeros(Float32, 3, 4);

// Identity
DenseMatrix I = DenseMatrix.eye(Float64, 5);      // 5×5 I
DenseMatrix I = DenseMatrix.eye(Float64, 3, 5);   // 3×5 rectangular I

// Diagonal
DenseMatrix D = DenseMatrix.diagflat(new double[]{1, 2, 3});

// Random
DenseMatrix R  = DenseMatrix.rand(Float64, 4, 4);             // uniform [0,1)
DenseMatrix R  = DenseMatrix.rand(Float64, 4, 4, -1.0, 1.0); // uniform [lo,hi)
DenseMatrix N  = DenseMatrix.randn(Float64, 4, 4);            // standard normal
DenseMatrix Rd = DenseMatrix.rand(Float64, 4, 4, distribution); // any Distribution

// Toeplitz
DenseMatrix T  = DenseMatrix.toeplitz(new double[]{1,2,3,4}); // symmetric
DenseMatrix T2 = DenseMatrix.toeplitz(kl, ku);                // non-symmetric
```

### Element Access

```java
double v = A.get(i, j);      // A[i,j]
A.set(i, j, 3.14);           // A[i,j] = 3.14
A.add(i, j, 1.0);            // A[i,j] += 1.0
A.sub(i, j, 0.5);
A.mul(i, j, 2.0);
A.div(i, j, 4.0);
A.fill(0.0);                 // fill all elements
A.scale(2.0);                // A *= 2.0
```

### Shape and Metadata

```java
int m = A.nrow();
int n = A.ncol();
ScalarType t = A.scalarType();  // Float64 or Float32
Order o = A.order();            // COL_MAJOR (always)

// Row / column names (optional, for display)
A.withRowNames(new String[]{"r1","r2"});
A.withColNames(new String[]{"c1","c2"});
```

### Rows, Columns, and Submatrices

```java
// Single row or column as a Vector
Vector row = A.row(0);          // copy of row 0
Vector col = A.column(1);       // view of column 1

// Multiple rows / columns
DenseMatrix sub = A.rows(0, 2);           // rows [0,2)
DenseMatrix sub = A.rows(new int[]{0,2}); // rows 0 and 2 (negative indices OK)
DenseMatrix sub = A.columns(1, 3);        // columns [1,3)
DenseMatrix sub = A.columns(new int[]{0}); // column 0

// Block submatrix A[i:k, j:l]
DenseMatrix block = A.submatrix(0, 1, 2, 3);  // rows [0,2), cols [1,3)
```

### Column/Row Statistics

```java
Vector csum  = A.colSums();    // sum of each column
Vector rsum  = A.rowSums();    // sum of each row
Vector cmean = A.colMeans();   // mean of each column
Vector rmean = A.rowMeans();   // mean of each row
Vector csd   = A.colSds();     // sample std dev of each column (÷ n-1)
Vector rsd   = A.rowSds();     // sample std dev of each row   (÷ n-1)
```

### Standardization

```java
// Zero-mean, unit-variance for each column (returns new matrix)
DenseMatrix std = A.standardize();

// Centre and scale with explicit vectors (null = skip that step)
DenseMatrix centred = A.standardize(colMeans, null);
DenseMatrix scaled  = A.standardize(null, colSds);
```

### Norms

```java
double frob = A.norm();     // Frobenius norm  ‖A‖_F = √(Σ aᵢⱼ²)
double col1 = A.norm1();    // L1 matrix norm  = max_j Σᵢ |aᵢⱼ|
double inf  = A.normInf();  // ∞ matrix norm   = max_i Σⱼ |aᵢⱼ|
double tr   = A.trace();    // trace (sum of diagonal)
```

### Diagonal

```java
Vector diag = A.diagonal();  // extracts main diagonal as a Vector
```

### Linear Algebra Operations

```java
// Transpose (always returns a new matrix)
DenseMatrix At = A.transpose();

// Symmetric/triangular annotation (for BLAS dispatch)
A.withUplo(LOWER);  // mark A as lower-triangular symmetric
A.withUplo(UPPER);

// Copy
DenseMatrix copy = A.copy();

// Equality check (tolerance = 10 * FLOAT_EPSILON)
boolean eq = A.equals(B);
```

### Matrix-Vector Multiplication

```java
// y = A * x
Vector y = A.mv(x);

// y = A' * x
Vector y = A.tv(x);

// y = alpha * A * x + beta * y  (in place, general BLAS gemv / symv / trmv)
A.mv(NO_TRANSPOSE, alpha, x, beta, y);
A.mv(TRANSPOSE,    alpha, x, beta, y);

// Using workspace array (for iterative solvers)
A.mv(work, inputOffset, outputOffset);
A.tv(work, inputOffset, outputOffset);

// Quadratic form  x' A x
double q = A.xAx(x);
```

### Matrix-Matrix Multiplication

```java
// C = A * B
DenseMatrix C = A.mm(B);

// C = A' * B
DenseMatrix C = A.tm(B);

// C = A * B'
DenseMatrix C = A.mt(B);

// Gram matrices
DenseMatrix AtA = A.ata();   // A' * A  (symmetric, LOWER)
DenseMatrix AAt = A.aat();   // A * A'  (symmetric, LOWER)

// General: C := alpha * op(A) * op(B) + beta * C
DenseMatrix.mm(alpha, TRANSPOSE, A, NO_TRANSPOSE, B, beta, C);
```

### Rank-1 Update

```java
// A += alpha * x * y'
A.ger(alpha, x, y);
```

### BLAS `axpy`

```java
// y += alpha * x  (in place)
y.axpy(alpha, x);

// y = alpha*A + beta*B  (stored in a pre-allocated output)
C.add(alpha, A, beta, B);

// A += B  (shorthand)
A.add(B);

// A -= B
A.sub(B);
```

### Matrix Inverse

```java
// Returns A⁻¹ without modifying A
DenseMatrix inv = A.copy().inverse();  // uses GESV (general) or SYSV (symmetric)
```

### Serialization

`DenseMatrix` implements `java.io.Serializable`. The off-heap `MemorySegment`
is transparently re-created on deserialization from the on-heap `double[]`/`float[]`.

```java
// Write
Path tmp = Write.object(matrix);
// Read
DenseMatrix loaded = (DenseMatrix) Read.object(tmp);
```

### Reading Matrix Market Files

```java
// Reads .mtx files; returns DenseMatrix or SparseMatrix depending on format
Matrix M = Matrix.market(Path.of("data.mtx"));
```

---

## 6. SymmMatrix — Symmetric / Packed Matrix

`SymmMatrix` stores only the upper or lower triangle of a symmetric matrix,
halving memory. BLAS `dsymv`/`ssymv` and `dsymm`/`ssymm` are automatically
dispatched for matrix-vector and matrix-matrix products.

```java
import static smile.linalg.UPLO.*;

// Create a 4×4 symmetric matrix (lower triangle)
SymmMatrix S = SymmMatrix.zeros(Float64, LOWER, 4);
S.set(0, 0, 2.0);
S.set(1, 0, 1.0);  // also sets S[0,1] = 1.0 by symmetry

// Read by index (both triangles return consistent values)
System.out.println(S.get(0, 1)); // 1.0

// All DenseMatrix operations are available
Vector y = S.mv(x);
DenseMatrix C = S.mm(B);
Cholesky chol = S.copy().cholesky();

// Fit from data (computes A'A or AA')
SymmMatrix AtA = SymmMatrix.from(A, LOWER); // equivalent to A.ata()
```

---

## 7. BandMatrix — Banded Matrix

`BandMatrix` uses the compact LAPACK band storage format: only the band
entries are stored, reducing memory from O(n²) to O(n·(kl+ku+1)) for a
matrix with `kl` sub-diagonals and `ku` super-diagonals.

```java
// 5×5 tridiagonal (1 sub-diagonal, 1 super-diagonal)
BandMatrix B = BandMatrix.zeros(Float64, 5, 5, 1, 1);
B.set(0, 0,  2.0); B.set(0, 1, -1.0);
B.set(1, 0, -1.0); B.set(1, 1,  2.0); B.set(1, 2, -1.0);
// ...

// Matrix-vector product (dispatches to BLAS gbmv)
Vector y = B.mv(x);

// LU decomposition of a band matrix (LAPACK gbtrf/gbsv)
LU lu = B.copy().lu();
Vector sol = lu.solve(rhs);
```

---

## 8. SparseMatrix — Compressed-Column Sparse

`SparseMatrix` uses the **Harwell-Boeing** column-compressed format (CCS):
non-zero values are stored column by column together with their row indices
and column start pointers. This is the standard format for iterative solvers.

### Construction

```java
// From arrays: values, rowIndex, colIndex (CSC format)
SparseMatrix S = new SparseMatrix(m, n, values, rowIndex, colIndex);

// From Matrix Market file (.mtx)
SparseMatrix S = (SparseMatrix) Matrix.market(Path.of("sparse.mtx"));

// From a SparseDataset
SparseMatrix S = SparseMatrix.of(dataset);

// From a Harwell-Boeing exchange file
SparseMatrix S = SparseMatrix.harwell(Path.of("data.hb"));
```

### Basic Operations

```java
int m = S.nrow();
int n = S.ncol();
long nz = S.length();          // number of non-zero elements
double density = S.density();  // nz / (m*n)

double v = S.get(i, j);        // O(log kj) lookup; 0 for structural zeros
S.set(i, j, 3.14);             // updates existing non-zero only
```

### Matrix-Vector Multiplication

```java
// y = A * x  (dispatches to sparse BLAS)
Vector y = S.mv(x);

// y = A' * x
Vector y = S.tv(x);

// In-place version
S.mv(NO_TRANSPOSE, alpha, x, beta, y);
```

### Iteration Over Non-Zeros

```java
// Functional iteration (fastest — avoids object allocation)
S.forEachNonZero((row, col, val) -> System.out.printf("[%d,%d] = %f%n", row, col, val));

// Java Stream API (more flexible)
S.nonzeros()
 .filter(e -> e.value > 0)
 .forEach(e -> System.out.println(e));

// Iterable
for (SparseMatrix.Entry e : S) {
    System.out.printf("[%d,%d] = %f%n", e.row, e.col, e.value);
}
```

### Graph Interpretation

```java
// Strongly connected components (Tarjan's algorithm)
int[] scc = S.scc();

// Largest connected component
SparseMatrix lcc = S.lcc();
```

---

## 9. Matrix Decompositions

All decompositions are performed by **LAPACK** via the Java FFM API.
By default the decompositions operate **in place** — call `copy()` first
to preserve the original matrix.

### LU Decomposition

```java
LU lu = A.copy().lu();       // DGETRF/SGETRF
Vector x  = lu.solve(b);     // solve A*x = b
DenseMatrix X = lu.solve(B); // solve A*X = B  (multiple RHS)
double det = lu.det();       // determinant
int rank   = lu.rank();      // numerical rank
```

### Cholesky Decomposition

For **symmetric positive-definite** matrices (must call `withUplo()` first).

```java
A.withUplo(LOWER);           // mark as symmetric
Cholesky chol = A.copy().cholesky();  // DPOTRF/SPOTRF
Vector x = chol.solve(b);
double logDet = chol.logDet();        // log-determinant
```

### QR Decomposition

```java
QR qr = A.copy().qr();       // DGEQRF/SGEQRF
Vector x = qr.solve(b);      // least-squares solution
```

### Singular Value Decomposition (SVD)

```java
// Compact SVD: U (m×k), s (k), Vt (k×n), where k = min(m,n)
SVD svd = A.copy().svd();
Vector s     = svd.s();      // singular values in descending order
DenseMatrix U  = svd.U();
DenseMatrix Vt = svd.Vt();   // V-transposed

// Truncated SVD (no vectors — only singular values)
SVD svd = A.copy().svd(false);

// Pseudo-inverse
DenseMatrix pinv = A.copy().svd().pinv();

// Solve minimum-norm least-squares
Vector x = A.copy().svd().solve(b);
```

### Eigenvalue Decomposition (EVD)

```java
// Right eigenvectors only (default)
EVD evd = A.copy().eigen();     // DSYEVD for symmetric, DGEEV for general

// Symmetric matrix (real eigenvalues)
A.withUplo(LOWER);
EVD evd = A.copy().eigen();
Vector  w  = evd.wr();           // real eigenvalues
DenseMatrix V = evd.Vr();        // eigenvector columns

// General matrix (possibly complex eigenvalues)
EVD evd = A.copy().eigen(false, true);  // vl=false, vr=true
Vector wr  = evd.wr();   // real parts
Vector wi  = evd.wi();   // imaginary parts
DenseMatrix Vr = evd.Vr();

// Sort by descending eigenvalue magnitude
evd.sort();
```

### ARPACK (Large Sparse Eigenvalue Problems)

For large sparse matrices use `ARPACK` (implicitly-restarted Arnoldi/Lanczos):

```java
// k largest eigenvalues of a symmetric sparse matrix
EVD evd = ARPACK.syev(A, ARPACK.Mode.SA, k);

// k largest singular values
SVD svd = ARPACK.svd(A, k);
```

---

## 10. Iterative Solvers

### Biconjugate Gradient Stabilized (BiCGSTAB)

Solves `A*x = b` iteratively for large sparse systems:

```java
Vector x0 = Vector.zeros(Float64, b.size());  // initial guess
Vector sol = BiconjugateGradient.solve(A, b, x0, 1e-6, 1000);
```

A custom `Preconditioner` can be supplied:

```java
Preconditioner P = /* e.g. diagonal (Jacobi) preconditioner */;
Vector sol = BiconjugateGradient.solve(A, b, x0, P, 1e-6, 1000);
```

---

## 11. Performance Notes

### Native BLAS/LAPACK via FFM

All matrix operations delegate to **OpenBLAS** (or system BLAS/LAPACK) via
the Java Foreign Function & Memory (FFM) API. This means:

- **Zero-copy**: `DenseMatrix` stores data in `MemorySegment` that is
  allocated off-heap and passed directly to native routines without copying.
- **SIMD / multi-threading**: OpenBLAS exploits AVX2/AVX-512 auto-vectorization
  and spawns multiple threads for large GEMM calls automatically.
- **Float32 vs Float64**: Single-precision (Float32) operations are roughly
  2× faster than double (Float64) on most CPUs and 4× faster on GPUs, at the
  cost of ~7 decimal digits of precision instead of ~15.

### Leading Dimension Padding

`DenseMatrix` pads the leading dimension to the next cache-friendly size
(aligned to 512-byte cache lines, avoiding cache-set conflicts):

```java
int ld = DenseMatrix.ld(m);  // optimal ld for m rows
```

For matrices with fewer than 64 rows, `ld == m` (no padding). Larger
matrices get padding to avoid false sharing in NUMA systems.

### Symmetric Dispatch

When a `DenseMatrix` is annotated with `withUplo()`, `mv()` dispatches to
`dsymv`/`ssymv` instead of `dgemv`/`sgemv` (roughly 2× faster for the
triangular access pattern), and `mm()` dispatches to `dsymm`/`ssymm`.

### Sparse vs Dense

| Matrix type | Memory | mv cost | mm cost |
|-------------|--------|---------|---------|
| `DenseMatrix` | O(m·n) | O(m·n) | O(m·n·k) |
| `SparseMatrix` | O(nnz) | O(nnz) | O(nnz·k) |
| `BandMatrix` (bw=b) | O(n·b) | O(n·b) | O(n·b·k) |
| `SymmMatrix` | O(n²/2) | O(n²/2) | O(n²/2·k) |

For matrices with sparsity > 90%, `SparseMatrix` is almost always faster and
more memory-efficient. For tridiagonal/banded PDE systems, use `BandMatrix`.

---

## 12. Quick Reference

### Construction

```java
// Dense
DenseMatrix.of(double[][] A)
DenseMatrix.zeros(Float64, m, n)
DenseMatrix.eye(Float64, n)
DenseMatrix.diagflat(double[] d)
DenseMatrix.rand(Float64, m, n)
DenseMatrix.randn(Float64, m, n)
DenseMatrix.rand(Float64, m, n, distribution)
DenseMatrix.rand(Float64, m, n, lo, hi)
DenseMatrix.toeplitz(double[] a)         // symmetric
DenseMatrix.toeplitz(double[] kl, ku)    // non-symmetric

// Symmetric packed
SymmMatrix.zeros(Float64, LOWER, n)

// Band
BandMatrix.zeros(Float64, m, n, kl, ku)

// Sparse (CSC)
new SparseMatrix(m, n, values, rowIndex, colIndex)
Matrix.market(path)                      // Matrix Market file

// Tensor (any type, any rank)
JTensor.of(double[] data, shape...)
JTensor.of(float[]  data, shape...)
JTensor.of(int[]    data, shape...)
JTensor.of(byte[]   data, shape...)

// Vector
Vector.column(double[])
Vector.column(float[])
Vector.zeros(Float64, n)
Vector.ones(Float64, n)
```

### Element Operations

```java
A.get(i, j)          // read
A.set(i, j, v)       // write
A.add(i, j, v)       // +=
A.sub(i, j, v)       // -=
A.mul(i, j, v)       // *=
A.div(i, j, v)       // /=
A.fill(v)            // fill all
A.scale(alpha)       // A *= alpha
```

### Matrix Arithmetic

```java
A.add(B)             // A += B  (in-place)
A.sub(B)             // A -= B
A.axpy(alpha, B)     // A += alpha * B
C.add(alpha,A, beta,B) // C = alpha*A + beta*B

A.mm(B)              // A * B
A.tm(B)              // A' * B
A.mt(B)              // A * B'
A.ata()              // A' * A  (symmetric)
A.aat()              // A * A'  (symmetric)
DenseMatrix.mm(alpha, transA, A, transB, B, beta, C)  // general gemm

A.mv(x)              // A * x
A.tv(x)              // A' * x
A.mv(trans, alpha, x, beta, y)  // y = alpha * op(A) * x + beta * y
A.ger(alpha, x, y)   // A += alpha * x * y'  (rank-1 update)
A.xAx(x)             // x' * A * x  (quadratic form)
```

### Decompositions

```java
A.copy().lu()          // → LU
A.copy().cholesky()    // → Cholesky  (symmetric positive-definite)
A.copy().qr()          // → QR
A.copy().svd()         // → SVD  (compact)
A.copy().svd(false)    // → SVD  (singular values only)
A.copy().eigen()       // → EVD  (right eigenvectors)
A.copy().eigen(vl, vr) // → EVD  (choose left/right)
A.copy().svd().pinv()  // → pseudo-inverse
ARPACK.syev(A, mode, k) // → EVD  (large sparse, k values)
ARPACK.svd(A, k)        // → SVD  (large sparse, k values)
```

### Statistics and Norms

```java
A.colSums()   A.rowSums()
A.colMeans()  A.rowMeans()
A.colSds()    A.rowSds()    // sample std dev (n-1)
A.standardize()             // zero-mean, unit-variance columns
A.trace()                   // sum of diagonal
A.diagonal()                // extract diagonal as Vector
A.norm()                    // Frobenius norm
A.norm1()                   // max column sum
A.normInf()                 // max row sum
A.inverse()                 // A⁻¹

v.sum()   v.mean()
v.variance()  v.sd()        // sample (n-1)
v.min()   v.max()
v.norm1() v.norm2() v.normInf() v.asum()
v.dot(w)
v.axpy(alpha, w)            // v += alpha * w
v.iamax()                   // index of |max|
v.softmax()                 // apply softmax; return argmax
v.diagflat()                // construct diagonal matrix
```


---

*SMILE — © 2010-2026 Haifeng Li. GNU GPL licensed.*

