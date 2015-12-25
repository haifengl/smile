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

import smile.classification._
import smile.math._, kernel._
import smile.util._

/**
 * Classification shell commands.
 *
 * @author Haifeng Li
 */
package object classification {

  /**
   * Applys a classification model on a data sample.
   * @param classifier classification model
   * @param x data sample
   * @param posteriori optional double array of posertiori probability output. Note not all models support it.
   * @tparam T the data type
   * @return the predicted class label
   */
  def predict[T <: AnyRef](classifier: Classifier[T], x: T, posteriori: Array[Double] = null) = {
    if (posteriori == null)
      classifier.predict(x)
    else
      classifier.predict(x, posteriori)
  }

  /**
   * Trains support vector machine.
   * @param x training data
   * @param y training labels
   * @param kernel Mercer kernel
   * @param C Regularization parameter
   * @param strategy multi-class classification strategy, one vs all or one vs one.
   * @param epoch the number of training epochs
   * @tparam T the data type
   * @return SVM model.
   */
  def svm[T <: AnyRef](x: Array[T], y: Array[Int], kernel: MercerKernel[T], C: Double, strategy: SVM.Multiclass = SVM.Multiclass.ONE_VS_ONE, epoch: Int = 1): SVM[T] = {
    val k = Math.max(y: _*) + 1
    val svm = new SVM[T](kernel, C, k, strategy)
    time {
      svm.learn(x, y)
      for (i <- 1 to epoch) {
        println(s"SVM training epoch $i...")
        svm.finish
      }
    }
    svm
  }
}
