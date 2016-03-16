/*******************************************************************************
 * (C) Copyright 2015 Haifeng Li
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

package smile.vq

import smile.util._

/** High level vector quantization operators.
  *
  * @author Haifeng Li
  */
trait Operators {

  /** Self-Organizing Map. An SOM is a unsupervised learning method to produce
    * a low-dimensional (typically two-dimensional) discretized representation
    * (called a map) of the input space of the training samples. The model was
    * first described as an artificial neural network by Teuvo Kohonen, and is
    * sometimes called a Kohonen map.
    *
    * While it is typical to consider SOMs as related to feed-forward networks where
    * the nodes are visualized as being attached, this type of architecture is
    * fundamentally different in arrangement and motivation because SOMs use a
    * neighborhood function to preserve the topological properties of the input
    * space. This makes SOMs useful for visualizing low-dimensional views of
    * high-dimensional data, akin to multidimensional scaling.
    *
    * SOMs belong to a large family of competitive learning process and vector
    * quantization. An SOM consists of components called nodes or neurons.
    * Associated with each node is a weight vector of the same dimension as
    * the input data vectors and a position in the map space. The usual arrangement
    * of nodes is a regular spacing in a hexagonal or rectangular grid. The
    * self-organizing map describes a mapping from a higher dimensional input
    * space to a lower dimensional map space. During the (iterative) learning,
    * the input vectors are compared to the weight vector of each neuron. Neurons
    * who most closely match the input are known as the best match unit (BMU) of
    * the system. The weight vector of the BMU and those of nearby neurons are
    * adjusted to be closer to the input vector by a certain step size.
    *
    * There are two ways to interpret a SOM. Because in the training phase weights
    * of the whole neighborhood are moved in the same direction, similar items
    * tend to excite adjacent neurons. Therefore, SOM forms a semantic map where
    * similar samples are mapped close together and dissimilar apart.
    * The other way is to think of neuronal weights as pointers to the input space.
    * They form a discrete approximation of the distribution of training samples.
    * More neurons point to regions with high training sample concentration and
    * fewer where the samples are scarce.
    *
    * SOM may be considered a nonlinear generalization of Principal components
    * analysis (PCA). It has been shown, using both artificial and real
    * geophysical data, that SOM has many advantages over the conventional feature
    * extraction methods such as Empirical Orthogonal Functions (EOF) or PCA.
    *
    * It has been shown that while SOMs with a small number of nodes behave in a
    * way that is similar to K-means. However, larger SOMs rearrange data
    * in a way that is fundamentally topological in character and display properties
    * which are emergent. Therefore, large maps are preferable to smaller ones.
    * In maps consisting of thousands of nodes, it is possible to perform cluster
    * operations on the map itself.
    *
    * A common way to display SOMs is the heat map of U-matrix. The U-matrix value
    * of a particular node is the minimum/maximum/average distance between the node
    * and its closest neighbors. In a rectangular grid for instance, we might
    * consider the closest 4 or 8 nodes.
    *
    * ====References:====
    *  - Teuvo KohonenDan. Self-organizing maps. Springer, 3rd edition, 2000. </li>
    *
    * @param data the dataset for clustering.
    * @param width the width of map.
    * @param height the height of map.
    */
  def som(data: Array[Array[Double]], width: Int, height: Int): SOM = {
    time {
      new SOM(data, width, height)
    }
  }


  /** Neural Gas soft competitive learning algorithm. The Neural Gas is inspired
    * by the Self-Organizing Map for finding optimal data representations based on
    * feature vectors. The algorithm was coined "Neural Gas" because of the
    * dynamics of the feature vectors during the adaptation process, which
    * distribute themselves like a gas within the data space. Although it is mainly
    * applied where data compression or vector quantization is an issue,
    * it is also used for cluster analysis as a robustly converging alternative to
    * the k-means clustering. A prominent extension is the Growing Neural Gas.
    *
    * Compared to SOM, neural gas has no topology of a fixed dimensionality
    * (in fact, no topology at all). For each input signal during learning, the
    * neural gas algorithm sorts the neurons of the network according to the
    * distance of their reference vectors to the input signal. Based on this
    * "rank order", neurons are adapted based on the adaptation strength that are
    * decreased according to a fixed schedule.
    *
    * The adaptation step of the Neural Gas can be interpreted as gradient descent
    * on a cost function. By adapting not only the closest feature vector but all
    * of them with a step size decreasing with increasing distance order,
    * compared to k-means clustering, a much more robust convergence of the
    * algorithm can be achieved.
    *
    * ====References:====
    *  - Thomas Martinetz and Klaus Schulten. A "neural gas" network learns topologies. Artificial Neural Networks, 397-402, 1991.
    *  - T. Martinetz, S. Berkovich, and K. Schulten. "Neural-gas" Network for Vector Quantization and its Application to Time-Series Prediction. IEEE Trans. on Neural Networks, 4(4):558-569, 1993.
    *  - T. Martinetz and K. Schulten. Topology representing networks. Neural Networks, 7(3):507-522, 1994.
    *
    * @param data the data set.
    * @param k the number of units in the neural gas.
    * @param lambda_i the initial value of lambda. lambda_i and lambda_f are
    *                 used to set the soft learning radius/rate, i.e. determining the number
    *                 of neural units significantly changing their synaptic weights with
    *                 each adaptation step.
    * @param lambda_f The final value of lambda.
    * @param eps_i the initial value of epsilon. epsilon_i and epsilon_f
    *              are the initial and final learning rate respectively.
    * @param eps_f the final value of epsilon.
    * @param steps the number of iterations. Note that for one iteration, we
    *              mean that the learning process goes through the whole dataset.
    */
  def neuralgas(data: Array[Array[Double]], k: Int, lambda_i: Double, lambda_f: Double = 0.01, eps_i: Double = 0.5, eps_f: Double = 0.005, steps: Int = 25): NeuralGas = {
    time {
      new NeuralGas(data, k, lambda_i, lambda_f, eps_i, eps_f, steps)
    }
  }

  /** Growing Neural Gas. As an extension of Neural Gas, Growing Neural Gas
    * can add and delete nodes during algorithm execution.  The growth mechanism
    * is based on growing cell structures and competitive Hebbian learning.
    *
    * Compared to Neural Gas, GNG has the following distinctions:
    *
    *  - The system has the ability to add and delete nodes.
    *  - Local Error measurements are noted at each step helping it to locally
    * insert/delete nodes.
    *  - Edges are connected between nodes, so a sufficiently old edges is
    * deleted. Such edges are intended place holders for localized data distribution.
    *  - Such edges also help to locate distinct clusters (those clusters are
    * not connected by edges).
    *
    * ====References:====
    *  - B. Fritzke. A growing neural gas network learns topologies. NIPS, 1995.
    *
    * @param data the data set.
    * @param epochs the number of epochs of learning.
    * @param epsBest the fraction to update nearest neuron.
    * @param epsNeighbor the fraction to update neighbors of nearest neuron.
    * @param maxEdgeAge the maximum age of edges.
    * @param lambda if the number of input signals so far is an integer multiple
    *               of lambda, insert a new neuron.
    * @param alpha decrease error variables by multiplying them with alpha
    *              during inserting a new neuron.
    * @param beta decrease all error variables by multiply them with beta.
    */
  def gng(data: Array[Array[Double]], epochs: Int = 25, epsBest: Double = 0.05, epsNeighbor: Double = 0.0006, maxEdgeAge: Int = 88, lambda: Int = 300, alpha: Double = 0.5, beta: Double = 0.9995): GrowingNeuralGas = {
    time {
      val gas = new GrowingNeuralGas(data(0).length, epsBest, epsNeighbor, maxEdgeAge, lambda, alpha, beta)
      for (i <- 0 until epochs) {
        data.foreach(gas.update(_))
      }
      gas
    }
  }

  /** NeuralMap is an efficient competitive learning algorithm inspired by growing
    * neural gas and BIRCH. Like growing neural gas, NeuralMap has the ability to
    * add and delete neurons with competitive Hebbian learning. Edges exist between
    * neurons close to each other. The edges are intended place holders for
    * localized data distribution. The edges also help to locate distinct clusters
    * (those clusters are not connected by edges). NeuralMap employs Locality-Sensitive
    * Hashing to speedup the learning while BIRCH uses balanced CF trees.
    *
    * @param data the data set.
    * @param radius the distance radius to activate a neuron for a given signal.
    * @param epsBest the fraction to update activated neuron.
    * @param epsNeighbor the fraction to update neighbors of activated neuron.
    * @param L the number of hash tables.
    * @param k the number of random projection hash functions.
    */
  def neuralmap(data: Array[Array[Double]], radius: Double, L: Int, k: Int, epsBest: Double = 0.05, epsNeighbor: Double = 0.0006): NeuralMap = {
    time {
      val cortex = new NeuralMap(data(0).length, radius, epsBest, epsNeighbor, L, k)
      data.foreach(cortex.update(_))
      cortex
    }
  }
}