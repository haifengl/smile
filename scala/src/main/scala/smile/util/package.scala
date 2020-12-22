/*
 * Copyright (c) 2010-2020 Haifeng Li. All rights reserved.
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
 */

package smile

import java.time.{Duration, LocalDateTime}
import scala.language.implicitConversions
import com.typesafe.scalalogging.LazyLogging

/** Utility functions.
  *
  * @author Haifeng Li
  */
package object util extends LazyLogging {
  /** Wraps Scala lambda as Java's. */
  implicit def toJavaFunction[T, R](f: T => R): java.util.function.Function[T, R] = t => f(t)

  /** Wraps Scala lambda as Java's. */
  implicit def toJavaBiFunction[T, U, R](f: (T, U) => R): java.util.function.BiFunction[T, U, R] = (t, u) => f(t, u)

  /** Measure running time of a function/block */
  object time {
    /** Print out switch. */
    var echo = true

    /** Turn on printing out running time. */
    def on(): Unit = {
      echo = true
    }

    /** Turn on printing out running time. */
    def off(): Unit = {
      echo = false
    }

    /** Executes a code block and measure the running time.
      * @param f a code block to measure the running time.
      * @tparam A The output type of code block.
      * @return the code block expression result.
      */
    def apply[A](f: => A): A = {
      apply("duration")(f)
    }

    /** Executes a code block and measure the running time.
      * @param message the log message.
      * @param f a code block to measure the running time.
      * @tparam A The output type of code block.
      * @return the code block expression result.
      */
    def apply[A](message: String)(f: => A): A = {
      val start = LocalDateTime.now
      val ret = f
      if (echo) {
        val end = LocalDateTime.now
        val duration = Duration.between(start, end)
        logger.info("{}: {}", message, duration)
      }
      ret
    }
  }
}
