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

package smile.spark

import org.apache.spark.ml.DataTypeOps
import org.apache.spark.sql.{Row, SparkSession}
import smile.data.{DataFrame, Tuple}
import scala.collection.JavaConverters._

/** Converts Spark DataFrame to Smile DataFrame. */
object SmileDataFrame {
  /** Returns a distributed Spark DataFrame. */
  def apply(df: DataFrame)(implicit spark: SparkSession): org.apache.spark.sql.DataFrame = {
    val schema = DataTypeOps.toSparkSchema(df.schema)
    spark.createDataFrame(
      df.toList.asScala.map(tuple => SparkRow(tuple, schema).asInstanceOf[Row]).asJava,
      schema)
  }
}

case class SparkRow(tuple:Tuple, override val schema:org.apache.spark.sql.types.StructType) extends org.apache.spark.sql.Row {
  override def length: Int = tuple.length()
  override def get(i: Int): Any = tuple.get(i)
  override def copy(): Row = this
}
