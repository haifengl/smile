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

/** Utility functions.
  *
  * @author Haifeng Li
  */
package object util {

  /** Measure running time of a function/block */
  object time {
    var echo = true

    def on = {
      echo = true
    }

    def off = {
      echo = false
    }

    def apply[A](f: => A) = {
      val s = System.nanoTime
      val ret = f
      if (echo) println("runtime: " + (System.nanoTime - s)/1e6 + " ms")
      ret
    }
  }
}
