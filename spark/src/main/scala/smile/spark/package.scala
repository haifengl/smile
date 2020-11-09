package smile

import org.apache.spark.sql.SparkSession
import smile.data.DataFrame

/**
 * Package for better integration of Spark MLLib Pipelines and SMILE
 */
package object spark {

  /**
   * Extension method to Spark [[org.apache.spark.sql.DataFrame]] to convert them to SMILE [[DataFrame]]
   */
  implicit class SparkDataFrameOps(df: org.apache.spark.sql.DataFrame) {
    def toSmileDF: DataFrame = SparkDataFrame(df)
  }

  /**
   * Extension method to SMILE [[DataFrame]] to convert them to Spark [[org.apache.spark.sql.DataFrame]]
   */
  implicit class SmileDataFrameOps(df: DataFrame) {
    def toSparkDF(spark:SparkSession): org.apache.spark.sql.DataFrame = SmileDataFrame(df,spark)
  }

}
