/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */

/**
 * Compressed sensing is a signal processing technique for efficiently
 * acquiring and reconstructing a signal by finding solutions to
 * underdetermined linear systems. This is based on the principle that,
 * through optimization, the sparsity of a signal can be exploited to
 * recover it from far fewer samples than required by the Nyquist–Shannon
 * sampling theorem. There are two conditions under which recovery is possible.
 * The first one is sparsity, which requires the signal to be sparse
 * in some domain. The second one is incoherence, which is applied through
 * the isometric property, which is sufficient for sparse signals.
 *
 * @author Haifeng Li
 */
package smile.cs;