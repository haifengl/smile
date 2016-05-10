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

import java.io.PrintWriter
import scala.io.Source
import scala.collection.mutable.ArrayBuffer
import com.thoughtworks.xstream.XStream
import smile.data.parser.microarray.{TXTParser, RESParser, PCLParser, GCTParser}
import smile.data.{Attribute, BinarySparseDataset, SparseDataset, AttributeDataset}
import smile.data.parser._
import smile.math.matrix.SparseMatrix

/** Output operators. */
object write {
  /** Writes an object/model to a file. */
  def model[T <: Object](x: T, file: String): Unit = {
    val xstream = new XStream
    val xml = xstream.toXML(x)
    new PrintWriter(file) {
      write(xml)
      close
    }
  }
}

/** Input operators. */
object read {
  /** Reads an object/model back from a file created by write command. */
  def model(file: String): AnyRef = {
    val xml = Source.fromFile(file).mkString
    val xstream = new XStream
    xstream.fromXML(xml)
  }

  /** Reads an ARFF file. */
  def arff(file: String, responseIndex: Int = -1): AttributeDataset = {
    new ArffParser().setResponseIndex(responseIndex).parse(file)
  }

  /** Reads a LivSVM file. */
  def libsvm(file: String): SparseDataset = {
    new LibsvmParser().parse(file)
  }

  /** Reads Harwell-Boeing column-compressed sparse matrix. */
  def hb(file: String): SparseMatrix = {
    new SparseMatrixParser().parse(file)
  }

  /** Reads spare dataset in coordinate triple tuple list format.
    * Coordinate file stores a list of (row, column, value) tuples:
    * {{{
    * instanceID attributeID value
    * instanceID attributeID value
    * instanceID attributeID value
    * instanceID attributeID value
    * ...
    * instanceID attributeID value
    * instanceID attributeID value
    * instanceID attributeID value
    * }}}
    * Ideally, the entries are sorted (by row index, then column index) to
    * improve random access times. This format is good for incremental matrix
    * construction.
    *
    * Optionally, there may be 2 header lines
    * {{{
    * D    // The number of instances
    * W    // The number of attributes
    * }}}
    * or 3 header lines
    * {{{
    * D    // The number of instances
    * W    // The number of attributes
    * N    // The total number of nonzero items in the dataset.
    * }}}
    * These header lines will be ignored.
    *
    * @param file the file path.
    * @param arrayIndexOrigin the starting index of array. By default, it is
    * 0 as in C/C++ and Java. But it could be 1 to parse data produced
    * by other programming language such as Fortran.
    */
  def coo(file: String, arrayIndexOrigin: Int = 0): SparseDataset = {
    new SparseDatasetParser(arrayIndexOrigin).parse(file)
  }

  /** Reads sparse binary dataset. Each item is stored as an integer array, which
    * are the indices of nonzero elements in ascending order.
    */
  def sb(file: String): BinarySparseDataset = {
    new BinarySparseDatasetParser().parse(file)
  }

  /** Reads a delimited text file with response variable. By default, the parser expects a
    * white-space-separated-values file. Each line in the file corresponds
    * to a row in the table. Within a line, fields are separated by white spaces,
    * each field belonging to one table column. This class can also be
    * used to read other text tabular files by setting delimiter character
    * such ash ','. The file may contain comment lines (starting with '%')
    * and missing values (indicated by placeholder '?').
    *
    * @param file the file path
    * @param response optional response variable attribute and the column index of response variable. The column index starts at 0.
    * @param delimiter delimiter string
    * @param comment the start of comment lines
    * @param missing the missing value placeholder
    * @param header true if the first row is header/column names
    * @param rowNames true if the first column is row id/names
    * @return an attribute dataset
    */
  def table(file: String, response: Option[(Attribute, Int)] = None, delimiter: String = "\\s+", comment: String = "%", missing: String = "?", header: Boolean = false, rowNames: Boolean = false): AttributeDataset = {
    val parser = new DelimitedTextParser

    response match {
      case Some((attr, index)) => parser.setResponseIndex(attr, index)
      case None => ()
    }

    parser.setDelimiter(delimiter)
      .setCommentStartWith(comment)
      .setMissingValuePlaceholder(missing)
      .setColumnNames(header)
      .setRowNames(rowNames)
      .parse(file)
  }

  /** Reads a CSV file  with response variable. */
  def csv(file: String, response: Option[(Attribute, Int)] = None, comment: String = "%", missing: String = "?", header: Boolean = false, rowNames: Boolean = false): AttributeDataset = {
    table(file, response, ",", comment, missing, header, rowNames)
  }

  /** Reads GCT microarray gene expression file. */
  def gct(file: String): AttributeDataset = {
    new GCTParser().parse(file)
  }

  /** Reads PCL microarray gene expression file. */
  def pcl(file: String): AttributeDataset = {
    new PCLParser().parse(file)
  }

  /** Reads RES microarray gene expression file. */
  def res(file: String): AttributeDataset = {
    new RESParser().parse(file)
  }

  /** Reads TXT microarray gene expression file.
    * The TXT format is a tab delimited file
    * format that describes an expression dataset. It is organized as follows:
    *
    * The first line contains the labels Name and Description followed by the
    * identifiers for each sample in the dataset. The Description is optional.
    *
    * Line format:
    * {{{
    * Name(tab)Description(tab)(sample 1 name)(tab)(sample 2 name) (tab) ... (sample N name)
    * }}}
    * Example:
    * {{{
    * Name Description DLBC1_1 DLBC2_1 ... DLBC58_0
    * }}}
    * The remainder of the file contains data for each of the genes. There is one
    * line for each gene. Each line contains the gene name, gene description, and
    * a value for each sample in the dataset. If the first line contains the
    * Description label, include a description for each gene. If the first line
    * does not contain the Description label, do not include descriptions for
    * any gene. Gene names and descriptions can contain spaces since fields are
    * separated by tabs.
    *
    * Line format:
    * {{{
    * (gene name) (tab) (gene description) (tab) (col 1 data) (tab) (col 2 data) (tab) ... (col N data)
    * }}}
    *
    * Example:
    * {{{
    *  AFFX-BioB-5_at AFFX-BioB-5_at (endogenous control) -104 -152 -158 ... -44
    * }}}
    */
  def txt(file: String): AttributeDataset = {
    new TXTParser().parse(file)
  }

  /** Reads a Wavefront OBJ file. The OBJ file format is a simple format of 3D geometry including
    * the position of each vertex, the UV position of each texture coordinate vertex,
    * vertex normals, and the faces that make each polygon defined as a list of vertices,
    * and texture vertices. Vertices are stored in a counter-clockwise order by default,
    * making explicit declaration of face normals unnecessary. OBJ coordinates have no units,
    * but OBJ files can contain scale information in a human readable comment line.
    *
    * Note that we parse only vertex and face elements. All other information ignored.
    *
    * @param file the file path
    * @return a tuple of vertex array and edge array.
    */
  def wavefront(file: String): (Array[Array[Double]], Array[Array[Int]]) = {
    val vertices = new ArrayBuffer[Array[Double]]
    val edges = new ArrayBuffer[Array[Int]]

    Source.fromFile(file).getLines foreach { line =>
      val tokens = line.split("\\s+")

      if (tokens.size > 1) {
        tokens(0) match {
          case "v" =>
            require(tokens.size == 4 || tokens.size == 5, s"Invalid vertex element: $line")
            vertices += Array(tokens(1).toDouble, tokens(2).toDouble, tokens(3).toDouble)
          case "f" =>
            require(tokens.size >= 3, s"Invalid face element: $line")
            val face = tokens.drop(1).map(_.toInt - 1)
            for (i <- 1 until face.size) edges += Array(face(i-1), face(i))
            edges += Array(face(0), face.last)
          case _ => // ignore all other elements
        }
      }
    }
    (vertices.toArray, edges.toArray)
  }
}
