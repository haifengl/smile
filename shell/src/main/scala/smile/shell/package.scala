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

/**
 * Common shell commands.
 *
 * @author Haifeng Li
 */
package object shell {

  /** Built in benchmarks */
  def benchmark(tests: String*) = {
    smile.benchmark.Benchmark.main(tests.toArray)
  }

  /** Show demo window */
  def demo = {
    javax.swing.SwingUtilities.invokeLater(new Runnable {
      override def run(): Unit = {
        smile.demo.SmileDemo.createAndShowGUI(false)
      }
    })
  }

  /** Print help summary */
  def help(command: String = "") = command match {
    case "read" => println(
      """
        |
      """.stripMargin)
    case "write" => println(
      """
        |
      """.stripMargin)
    case "plot" => println(
      """
        |
      """.stripMargin)
    case "line" => println(
      """
        |
      """.stripMargin)
    case "boxplot" => println(
      """
        |
      """.stripMargin)
    case "stair" => println(
      """
        | General:
        |   help -- print this summary
        |   :help -- print Scala shell command summary
        |   :quit -- exit the shell
        |   demo -- show demo window
        |   benchmark -- benchmark tests
        |
        | I/O:
        |   read --
        |   write --
        |
        | Classification:
        |   randomForest --
        |   svm --
        |
        | Graphics:
        |   plot --
        |   line --
        |   boxplot --
      """.stripMargin)
    case unknown => println(s"""Unknown command: $unknown, type "help" to see available commands.""")
  }
}
