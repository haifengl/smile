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
    case "help" => println("print the command summary")
    case "read" => println(
      """
        |  def read(file: String): AnyRef
        |
        |  Reads an object/model back from a file created by write command.
      """.stripMargin)
    case "write" => println(
      """
        |  def write[T <: Object](x: T, file: String): Unit
        |
        |  Writes an object/model to a file.
      """.stripMargin)
    case "readArff" => println(
      """
        |  def readArff(file: String): AttributeDataset
        |
        |  Reads an ARFF file.
      """.stripMargin)
    case "readLibsvm" => println(
      """
        |  def readLibsvm(file: String): SparseDataset
        |
        |  Reads a LivSVM file.
      """.stripMargin)
    case "readSparseMatrix" => println(
      """
        |  def readSparseMatrix(file: String): SparseMatrix
        |
        |  Reads Harwell-Boeing column-compressed sparse matrix.
      """.stripMargin)
    case "readSparseData" => println(
      """
        |  def readSparseData(file: String, arrayIndexStartBase: Int = 0): SparseDataset
        |
        |  Reads spare dataset in coordinate triple tuple list format.
        |  Coordinate file stores a list of (row, column, value) tuples:
        |
        |    instanceID attributeID value
        |    instanceID attributeID value
        |    instanceID attributeID value
        |    instanceID attributeID value
        |    ...
        |    instanceID attributeID value
        |    instanceID attributeID value
        |    instanceID attributeID value
        |
        |  Ideally, the entries are sorted (by row index, then column index) to
        |  improve random access times. This format is good for incremental matrix
        |  construction.
        |
        |  Optionally, there may be 2 header lines
        |
        |    D    // The number of instances
        |    W    // The number of attributes
        |
        |  or 3 header lines
        |
        |    D    // The number of instances
        |    W    // The number of attributes
        |    N    // The total number of nonzero items in the dataset.
        |
        |  These header lines will be ignored.
        |
        |  @param arrayIndexStartBase the starting index of array. By default, it is
        |    0 as in C/C++ and Java. But it could be 1 to parse data produced
        |    by other programming language such as Fortran.
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
    case "" => println(
      """
        | General:
        |   help  -- print this summary
        |   :help -- print Scala shell command summary
        |   :quit -- exit the shell
        |   demo  -- show demo window
        |   benchmark -- benchmark tests
        |
        | I/O:
        |   read  -- Reads an object/model back from a file created by write command.
        |   write -- Writes an object/model to a file.
        |   readArff -- Reads an ARFF file.
        |   readLibsvm -- Reads a LivSVM file.
        |   readSparseMatrix -- Reads Harwell - Boeing column - compressed sparse matrix.
        |   readSparseData -- Reads spare dataset in coordinate triple tuple list format.
        |   readBinarySparseData -- Reads binary sparse dataset.
        |   readTable -- Reads a delimited text file.
        |   readGct -- Reads GCT microarray gene expression file.
        |   readPcl -- Reads PCL microarray gene expression file.
        |   readRes -- Reads RES microarray gene expression file.
        |   readTxt -- Reads TXT microarray gene expression file.
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
    case unknown => println(s"""Unknown command: $unknown, type "help()" to see available commands.""")
  }
}
