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
 * Matrix implementation based on low-level BLAS, LAPACK and ARPACK that
 * performs as fast as the C / Fortran interfaces with a pure JVM fallback.
 * <p>
 * This module employs the highly efficient <a href="https://github.com/fommil/netlib-java#netlib-java">netlib-java</a>
 * library. To enable machine optimized natives in netlib-java, the users
 * should make their machine-optimized libblas3 (CBLAS) and liblapack3
 * (Fortran) available as shared libraries at runtime.
 * <p>
 * <h3>OS X</h3>
 *
 * Apple OS X requires no further setup because OS X ships with the veclib
 * framework, boasting incredible CPU performance that is difficult to
 * surpass.
 *
 * <h3>Linux</h3>
 *
 * Generically-tuned ATLAS and OpenBLAS are available with most distributions
 * and must be enabled explicitly using the package-manager. For example,
 * <p>
 * <pre><code>
 * sudo apt-get install libatlas3-base libopenblas-base
 * sudo update-alternatives --config libblas.so
 * sudo update-alternatives --config libblas.so.3
 * sudo update-alternatives --config liblapack.so
 * sudo update-alternatives --config liblapack.so.3
 * </code></pre>
 * <p>
 * However, these are only generic pre-tuned builds.
 * <p>
 * If you have an Intel MKL licence, you could also create symbolic links
 * from libblas.so.3 and liblapack.so.3 to libmkl_rt.so or use Debian's
 * alternatives system.
 *
 * <h3>Windows</h3>
 *
 * The native_system builds expect to find libblas3.dll and liblapack3.dll
 * on the %PATH% (or current working directory). Besides vendor-supplied
 * implementations, OpenBLAS provide generically tuned binaries, and it
 * is possible to build ATLAS.
 *
 * <h3>Customization</h3>
 *
 * A specific implementation may be forced like so:
 * <p>
 * <pre><code>
 * -Dcom.github.fommil.netlib.BLAS=com.github.fommil.netlib.NativeRefBLAS
 * -Dcom.github.fommil.netlib.LAPACK=com.github.fommil.netlib.NativeRefLAPACK
 * -Dcom.github.fommil.netlib.ARPACK=com.github.fommil.netlib.NativeRefARPACK
 * </code></pre>
 * <p>
 * A specific (non-standard) JNI binary may be forced like so:
 * <p>
 * <pre><code>
 * -Dcom.github.fommil.netlib.NativeSystemBLAS.natives=netlib-native_system-myos-myarch.so
 * </code></pre>
 * <p>
 * To turn off natives altogether, add these to the JVM flags:
 * <p>
 * <pre><code>
 * -Dcom.github.fommil.netlib.BLAS=com.github.fommil.netlib.F2jBLAS
 * -Dcom.github.fommil.netlib.LAPACK=com.github.fommil.netlib.F2jLAPACK
 * -Dcom.github.fommil.netlib.ARPACK=com.github.fommil.netlib.F2jARPACK
 * </code></pre>
 *
 * @author Haifeng Li
 */
package smile.netlib;
