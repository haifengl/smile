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

/**
 * netlib-java is a wrapper for low-level BLAS, LAPACK and ARPACK that
 * performs as fast as the C / Fortran interfaces with a pure JVM fallback.
 *
 * To enable machine optimised natives in netlib-java, end-users make
 * their machine-optimised libblas3 (CBLAS) and liblapack3 (Fortran)
 * available as shared libraries at runtime.
 *
 * OS X
 *
 * Apple OS X requires no further setup because OS X ships with the veclib
 * framework, boasting incredible CPU performance that is difficult to
 * surpass.
 *
 * Linux
 *
 * Generically-tuned ATLAS and OpenBLAS are available with most distributions
 * and must be enabled explicitly using the package-manager. For example,
 *
 * sudo apt-get install libatlas3-base libopenblas-base
 * sudo update-alternatives --config libblas.so
 * sudo update-alternatives --config libblas.so.3
 * sudo update-alternatives --config liblapack.so
 * sudo update-alternatives --config liblapack.so.3
 *
 * However, these are only generic pre-tuned builds.
 *
 * If you have an Intel MKL licence, you could also create symbolic links
 * from libblas.so.3 and liblapack.so.3 to libmkl_rt.so or use Debian's
 * alternatives system.
 *
 * Windows
 *
 * The native_system builds expect to find libblas3.dll and liblapack3.dll
 * on the %PATH% (or current working directory). Besides vendor-supplied
 * implementations, OpenBLAS provide generically tuned binaries, and it
 * is possible to build ATLAS.
 *
 * A specific implementation may be forced like so:
 *
 * -Dcom.github.fommil.netlib.BLAS=com.github.fommil.netlib.NativeRefBLAS
 * -Dcom.github.fommil.netlib.LAPACK=com.github.fommil.netlib.NativeRefLAPACK
 * -Dcom.github.fommil.netlib.ARPACK=com.github.fommil.netlib.NativeRefARPACK
 *
 * A specific (non-standard) JNI binary may be forced like so:
 *
 * -Dcom.github.fommil.netlib.NativeSystemBLAS.natives=netlib-native_system-myos-myarch.so
 *
 * To turn off natives altogether, add these to the JVM flags:
 *
 * -Dcom.github.fommil.netlib.BLAS=com.github.fommil.netlib.F2jBLAS
 * -Dcom.github.fommil.netlib.LAPACK=com.github.fommil.netlib.F2jLAPACK
 * -Dcom.github.fommil.netlib.ARPACK=com.github.fommil.netlib.F2jARPACK
 *
 * @author Haifeng Li
 */
package smile.netlib;
