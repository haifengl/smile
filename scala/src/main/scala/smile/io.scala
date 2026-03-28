/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile

import java.io.*
import java.nio.file.{Path, Paths}
import java.sql.ResultSet
import scala.io.Source
import scala.collection.mutable.ArrayBuffer
import org.apache.commons.csv.CSVFormat
import smile.data.{DataFrame, SparseDataset}
import smile.data.`type`.StructType
import smile.io.{Read, Write, JSON}

/** Data saving utilities. */
object write {
  /** Serializes a `Serializable` object/model to a file. */
  def apply[T <: Serializable](x: T, file: String): Unit = apply(x, Paths.get(file))

  /** Serializes a `Serializable` object/model to a file. */
  def apply[T <: Serializable](x: T, file: Path): Unit = {
    val oos = new ObjectOutputStream(new FileOutputStream(file.toFile))
    oos.writeObject(x)
    oos.close()
  }

  /** Writes an array to a text file line by line.
    *
    * @param data an array.
    * @param file the file path
    */
  def array[T](data: Array[T], file: String): Unit = array(data, Paths.get(file))

  /** Writes an array to a text file line by line.
    *
    * @param data an array.
    * @param file the file path
    */
  def array[T](data: Array[T], file: Path): Unit = {
    val writer = new PrintWriter(file.toFile)
    data.foreach(writer.println(_))
    writer.close()
  }

  /** Writes a data frame to an Apache Arrow file. */
  def arrow(data: DataFrame, file: String): Unit = arrow(data, Paths.get(file))

  /** Writes a data frame to an Apache Arrow file. */
  def arrow(data: DataFrame, file: Path): Unit = Write.arrow(data, file)

  /** Writes a data frame to an ARFF file. */
  def arff(data: DataFrame, file: String, relation: String): Unit = arff(data, Paths.get(file), relation)

  /** Writes a data frame to an ARFF file. */
  def arff(data: DataFrame, file: Path, relation: String): Unit = Write.arff(data, file, relation)

  /** Writes a DataFrame to a comma-delimited text file.
    *
    * @param data an attribute dataset.
    * @param file the file path.
    * @param delimiter delimiter string.
    */
  def csv(data: DataFrame, file: String, delimiter: String = ","): Unit =
    csv(data, Paths.get(file), delimiter)

  /** Writes a DataFrame to a delimited text file.
    *
    * @param data an attribute dataset.
    * @param file the file path.
    * @param delimiter delimiter string.
    */
  def csv(data: DataFrame, file: Path, delimiter: String): Unit = {
    val format = CSVFormat.Builder.create().setDelimiter(delimiter)
    Write.csv(data, file, format.get())
  }

  /** Writes a two-dimensional array to a comma-delimited text file.
    *
    * @param data a two-dimensional array.
    * @param file the file path.
    * @param delimiter delimiter string.
    */
  def table[T](data: Array[Array[T]], file: String, delimiter: String = ","): Unit =
    table(data, Paths.get(file), delimiter)

  /** Writes a two-dimensional array to a delimited text file.
    *
    * @param data a two-dimensional array.
    * @param file the file path.
    * @param delimiter delimiter string.
    */
  def table[T](data: Array[Array[T]], file: Path, delimiter: String): Unit = {
    val writer = new PrintWriter(file.toFile)
    val sb = new StringBuilder
    val del = sb.append(delimiter).toString

    data.foreach { row =>
      writer.println(row.mkString(del))
    }

    writer.close()
  }
}

/** Data loading utilities. */
object read {
  /** Reads a serialized object from a file. */
  def apply(file: String): AnyRef = apply(Paths.get(file))

  /** Reads a serialized object from a file. */
  def apply(file: Path): AnyRef = {
    val ois = new ObjectInputStream(new FileInputStream(file.toFile))
    val o = ois.readObject
    ois.close()
    o
  }

  /**
    * Reads a data file. Infers the data format by the file name extension.
    * @param path the input file path.
    * @param format the optional file format specification. For csv files,
    *               it is such as <code>delimiter=\t,header=true,comment=#,escape=\,quote="</code>.
    *               For json files, it is the file mode (single-line or
    *               multi-line). For avro files, it is the path to the schema
    *               file.
    * @return the data frame.
    */
  def data(path: String, format: String = null): DataFrame = {
    Read.data(path, format)
  }

  /** Reads a JDBC query result to a data frame. */
  def jdbc(rs: ResultSet): DataFrame = {
    DataFrame.of(rs)
  }

  /** Reads a CSV file. */
  def csv(file: String, delimiter: String = ",", header: Boolean = true, quote: Char = '"', escape: Char = '\\',
          comment: Char = '\u0000', schema: StructType = null): DataFrame = {
    val format = CSVFormat.Builder.create()
      .setDelimiter(delimiter)
      .setQuote(quote)
      .setEscape(escape)
    if (header) format.setHeader().setSkipHeaderRecord(true)
    if (comment != '\u0000') format.setCommentMarker(comment)
    Read.csv(file, format.get(), schema)
  }

  /** Reads a CSV file. */
  def csv(file: Path, delimiter: String, header: Boolean, quote: Char, escape: Char,
          comment: Char, schema: StructType): DataFrame = {
    val format = CSVFormat.Builder.create()
      .setDelimiter(delimiter)
      .setQuote(quote)
      .setEscape(escape)
      .setCommentMarker(comment)
    if (header) format.setHeader().setSkipHeaderRecord(true)
    Read.csv(file, format.get(), schema)
  }

  /** Reads a CSV file. */
  def csv(file: String, format: CSVFormat, schema: StructType): DataFrame = Read.csv(file, format, schema)

  /** Reads a CSV file. */
  def csv(file: Path, format: CSVFormat, schema: StructType): DataFrame = Read.csv(file, format, schema)

  /** Reads a JSON file. */
  def json(file: String): DataFrame = Read.json(file)

  /** Reads a JSON file. */
  def json(file: Path): DataFrame = Read.json(file)

  /** Reads a JSON file. */
  def json(file: String, mode: JSON.Mode, schema: StructType): DataFrame = Read.json(file, mode, schema)

  /** Reads a JSON file. */
  def json(file: Path, mode: JSON.Mode, schema: StructType): DataFrame = Read.json(file, mode, schema)

  /** Reads an ARFF file. */
  def arff(file: String): DataFrame = Read.arff(file)

  /** Reads an ARFF file. */
  def arff(file: Path): DataFrame = Read.arff(file)

  /** Reads a SAS7BDAT file. */
  def sas(file: String): DataFrame = Read.sas(file)

  /** Reads a SAS7BDAT file. */
  def sas(file: Path): DataFrame = Read.sas(file)

  /** Reads an Apache Arrow file. */
  def arrow(file: String): DataFrame = Read.arrow(file)

  /** Reads an Apache Arrow file. */
  def arrow(file: Path): DataFrame = Read.arrow(file)

  /** Reads an Apache Avro file. */
  def avro(file: String, schema: InputStream): DataFrame = Read.avro(file, schema)

  /** Reads an Apache Avro file. */
  def avro(file: String, schema: String): DataFrame = Read.avro(file, schema)

  /** Reads an Apache Avro file. */
  def avro(file: Path, schema: InputStream): DataFrame = Read.avro(file, schema)

  /** Reads an Apache Avro file. */
  def avro(file: Path, schema: Path): DataFrame = Read.avro(file, schema)

  /** Reads an Apache Parquet file. */
  def parquet(file: String): DataFrame = Read.parquet(file)

  /** Reads an Apache Parquet file. */
  def parquet(file: Path): DataFrame = Read.parquet(file)

  /** Reads a LivSVM file. */
  def libsvm(file: String): SparseDataset[Integer] = Read.libsvm(file)

  /** Reads a LivSVM file. */
  def libsvm(file: Path): SparseDataset[Integer] = Read.libsvm(file)

  /** Reads a Wavefront OBJ file. */
  def wavefront(file: String): (Array[Array[Double]], Array[Array[Int]]) = wavefront(Paths.get(file))

  /** Reads a Wavefront OBJ file. The OBJ file format is a simple format of 3D geometry including
    * the position of each vertex, the UV position of each texture coordinate vertex,
    * vertex normals, and the faces that make each polygon defined as a list of vertices,
    * and texture vertices. Vertices are stored in a counter-clockwise order by default,
    * making explicit declaration of face normals unnecessary. OBJ coordinates have no units,
    * but OBJ files can contain scale information in a human-readable comment line.
    *
    * Note that we parse only vertex and face elements. All other information ignored.
    *
    * @param file the file path
    * @return a tuple of vertex array and edge array.
    */
  def wavefront(file: Path): (Array[Array[Double]], Array[Array[Int]]) = {
    val vertices = new ArrayBuffer[Array[Double]]
    val edges = new ArrayBuffer[Array[Int]]

    val source = Source.fromFile(file.toFile)
    try {
      source.getLines() foreach { line =>
        val tokens = line.split("\\s+")

        if (tokens.size > 1) {
          tokens(0) match {
            case "v" =>
              require(tokens.size == 4 || tokens.size == 5, s"Invalid vertex element: $line")
              vertices += Array(tokens(1).toDouble, tokens(2).toDouble, tokens(3).toDouble)
            case "f" =>
              require(tokens.size >= 3, s"Invalid face element: $line")
              val face = tokens.drop(1).map(_.toInt - 1)
              for (i <- 1 until face.length) edges += Array(face(i - 1), face(i))
              edges += Array(face(0), face.last)
            case _ => // ignore all other elements
          }
        }
      }

      (vertices.toArray, edges.toArray)
    } finally {
      source.close()
    }
  }
}
