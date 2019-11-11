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
 * Hebbian theory is a neuroscientific theory claiming that an increase in
 * synaptic efficacy arises from a presynaptic cell's repeated and persistent
 * stimulation of a postsynaptic cell. It is an attempt to explain synaptic
 * plasticity, the adaptation of brain neurons during the learning process.
 * It was introduced by Donald Hebb in his 1949 book The Organization of
 * Behavior.
 *
 * The theory is often summarized as "Cells that fire together wire together."
 * This summary, however, should not be taken too literally. Hebb emphasized
 * that cell A needs to "take part in firing" cell B, and such causality can
 * occur only if cell A fires just before, not at the same time as, cell B.
 * This important aspect of causation in Hebb's work foreshadowed what is
 * now known about spike-timing-dependent plasticity, which requires temporal
 * precedence.
 *
 * The theory attempts to explain associative or Hebbian learning, in which
 * simultaneous activation of cells leads to pronounced increases in synaptic
 * strength between those cells. In the study of neural networks in cognitive
 * function, it is often regarded as the neuronal basis of unsupervised
 * learning.
 *
 * @author Haifeng Li
 */
package smile.vq.hebb;