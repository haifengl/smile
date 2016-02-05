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

package smile.shell

/** High level operators that are accessible in the shell without explicit qualification.
  *
  * @author Haifeng Li
  */
trait Operators extends smile.association.Operators
  with smile.classification.Operators
  with smile.regression.Operators
  with smile.clustering.Operators
  with smile.manifold.Operators
  with smile.mds.Operators
  with smile.projection.Operators
  with smile.wavelet.Operators
  with smile.io.Operators {

}