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
 * Vector quantization is a lossy compression technique used in speech
 * and image coding. In vector quantization, a vector is selected from
 * a finite list of possible vectors to represent an input vector of
 * samples. Each input vector can be viewed as a point in an n-dimensional
 * space. The vector quantizer is defined by a partition of this space
 * into a set of non-overlapping regions. The vector is encoded by
 * the nearest reference vector (known as codevector) in the codebook.
 *
 * @author Haifeng Li
 */
package smile.vq;