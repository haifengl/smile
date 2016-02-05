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
    /** Print out switch. */
    var echo = true

    /** Turn on printing out running time. */
    def on = {
      echo = true
    }

    /** Turn on printing out running time. */
    def off = {
      echo = false
    }

    /**
      * Executes a code block and measure the running time.
      * @param f a code block to measure the running time.
      * @tparam A The output type of code block.
      * @return the code block expression result.
      */
    def apply[A](f: => A) = {
      val s = System.nanoTime
      val ret = f
      if (echo) println("runtime: " + (System.nanoTime - s)/1e6 + " ms")
      ret
    }
  }
}
