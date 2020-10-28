package smile

import org.apache.spark.sql.SparkSession

package object data {

  implicit class SparkDataFrameOps(df: org.apache.spark.sql.DataFrame) {
    def toSmileDF: DataFrame = SparkDataFrame(df)
  }

  implicit class SmileDataFrameOps(df: DataFrame) {
    def toSparkDF(spark:SparkSession): org.apache.spark.sql.DataFrame = SmileDataFrame(df,spark)
  }

}
