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

package smile

/** Originally used for data compression, Vector quantization (VQ)
  * allows the modeling of probability density functions by
  * the distribution of prototype vectors. It works by dividing a large set of points
  * (vectors) into groups having approximately the same number of
  * points closest to them. Each group is represented by its centroid
  * point, as in K-Means and some other clustering algorithms.
  *
  * Vector quantization is is based on the competitive learning paradigm,
  * and also closely related to sparse coding models
  * used in deep learning algorithms such as autoencoder.
  *
  * Algorithms in this package also support the <code>partition</code>
  * method for clustering purpose.
  *
  * @author Haifeng Li
  */
package object vq extends Operators {

}
