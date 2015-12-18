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
 * Graphs are mathematical structures used to model pairwise relations between
 * objects from a certain collection. A graph is an abstract representation of
 * a set of objects where some pairs of the objects are connected by links.
 * The interconnected objects are represented by mathematical abstractions
 * called vertices (also called nodes or points), and the links that connect
 * some pairs of vertices are called edges (also called lines).
 * The edges may be directed (asymmetric) or undirected (symmetric).
 * <p>
 * There are different ways to store graphs in a computer system. The data
 * structure used depends on both the graph structure and the algorithm
 * used for manipulating the graph. Theoretically one can distinguish between
 * list and matrix structures but in concrete applications the best structure
 * is often a combination of both. List structures are often preferred for
 * sparse graphs as they have smaller memory requirements. Matrix structures
 * on the other hand provide faster access for some applications but can
 * consume huge amounts of memory.
 * <dl>
 * <dt>List structures</dt>
 * <dd>
 * <i>Incidence list</i> The edges are represented by an array containing
 * pairs (tuples if directed) of vertices (that the edge connects) and possibly
 * weight and other data. Vertices connected by an edge are said to be adjacent.
 * <p>
 * <i>Adjacency list</i> Much like the incidence list, each vertex has a list
 * of which vertices it is adjacent to. This causes redundancy in an undirected
 * graph. Adjacency queries are faster, at the cost of extra storage space.
 * </dd>
 * <dt>Matrix structures</dt>
 * <dd>
 * <i>Incidence matrix</i> The graph is represented by a matrix of size
 * |V| (number of vertices) by |E| (number of edges) where the entry
 * [vertex, edge] contains the edge's endpoint data (simplest case:
 * 1 - incident, 0 - not incident).
 * <p>
 * <i>Adjacency matrix</i> This is an n by n matrix A, where n is the number
 * of vertices in the graph. If there is an edge from a vertex x to a vertex y,
 * then the element A(x,y) is 1 (or in general the number of edges), otherwise
 * it is 0. In computing, this matrix makes it easy to find subgraphs, and to
 * reverse a directed graph.
 * <p>
 * <i>Laplacian matrix or "Kirchhoff matrix" or "Admittance matrix"</i> 
 * This is defined as D - A, where D is the diagonal degree matrix. It
 * explicitly contains both adjacency information and degree information.
 * (However, there are other, similar matrices that are also called
 * "Laplacian matrices" of a graph.)
 * <p>
 * <i>Distance matrix</i> A symmetric n by n matrix D, where n is the number
 * of vertices in the graph. The element D(x,y) is the length of a shortest
 * path between x and y; if there is no such path D(x,y) = infinity. It can be
 * derived from powers of A.
 * </dd>
 * </dl>
 * 
 * @author Haifeng Li
 */
package smile.graph;