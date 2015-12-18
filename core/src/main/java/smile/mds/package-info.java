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
 * Multidimensional scaling. MDS is a set of related statistical techniques
 * often used in information visualization for exploring similarities or
 * dissimilarities in data. An MDS algorithm starts with a matrix of item-item
 * similarities, then assigns a location to each item in N-dimensional space.
 * For sufficiently small N, the resulting locations may be displayed in a
 * graph or 3D visualization.
 * <p>
 * The major types of MDS algorithms include:
 * <dl>
 * <dt>Classical multidimensional scaling</dt>
 * <dd>takes an input matrix giving dissimilarities between pairs of items and
 * outputs a coordinate matrix whose configuration minimizes a loss function
 * called strain.</dd>
 * <dt>Metric multidimensional scaling</dt>
 * <dd>A superset of classical MDS that generalizes the optimization procedure
 * to a variety of loss functions and input matrices of known distances with
 * weights and so on. A useful loss function in this context is called stress
 * which is often minimized using a procedure called stress majorization.</dd>
 * <dt>Non-metric multidimensional scaling</dt>
 * <dd>In contrast to metric MDS, non-metric MDS finds both a non-parametric
 * monotonic relationship between the dissimilarities in the item-item matrix
 * and the Euclidean distances between items, and the location of each item in
 * the low-dimensional space. The relationship is typically found using isotonic
 * regression.</dd>
 * <dt>Generalized multidimensional scaling</dt>
 * <dd>An extension of metric multidimensional scaling, in which the target
 * space is an arbitrary smooth non-Euclidean space. In case when the
 * dissimilarities are distances on a surface and the target space is another
 * surface, GMDS allows finding the minimum-distortion embedding of one surface
 * into another.</dd>
 * </dl>
 * 
 * @author Haifeng Li
 */
package smile.mds;
