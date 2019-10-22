/*******************************************************************************
 * Copyright (c) 2010-2019 Haifeng Li
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
 *******************************************************************************/

package smile

import java.io._
import scala.io.Source
import scala.collection.mutable.ArrayBuffer
import com.thoughtworks.xstream.XStream

/** Output operators. */
object write {
  /** Serializes a `Serializable` object/model to a file. */
  def apply[T <: Serializable](x: T, file: String): Unit = {
    val oos = new ObjectOutputStream(new FileOutputStream(file))
    oos.writeObject(x)
    oos.close
  }

  /** Serializes an object/model to a file by XStream. */
  def xstream[T <: Object](x: T, file: String): Unit = {
    val xstream = new XStream
    val xml = xstream.toXML(x)
    new PrintWriter(file) {
      write(xml)
      close
    }
  }

  /** Writes an array to a text file line by line.
    *
    * @param data an array.
    * @param file the file path
    */
  def apply[T](data: Array[T], file: String): Unit = {
    val writer = new PrintWriter(new File(file))
    data.foreach(writer.println(_))
    writer.close
  }
}

/** Input operators. */
object read {
  /** Reads a `Serializable` object/model. */
  def apply(file: String): AnyRef = {
    val ois = new ObjectInputStream(new FileInputStream(file))
    val o = ois.readObject
    ois.close
    o
  }

  /** Reads an object/model that was serialized by XStream. */
  def xstream(file: String): AnyRef = {
    val xml = Source.fromFile(file).mkString
    val xstream = new XStream
    xstream.fromXML(xml)
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
