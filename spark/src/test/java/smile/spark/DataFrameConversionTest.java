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
package smile.spark;

import java.util.List;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.RowFactory;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.StructField;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import smile.data.DataFrame;
import smile.data.Tuple;
import smile.io.Paths;
import smile.io.Read;

import static org.junit.jupiter.api.Assertions.*;

class DataFrameConversionTest {

    private static SparkSession spark;

    @BeforeAll
    static void setUp() {
        spark = SparkTest.createSession("DataFrameConversionTest");
    }

    @AfterAll
    static void tearDown() {
        if (spark != null) {
            spark.stop();
        }
    }

    @Test
    void testSparkToSmile() {
        org.apache.spark.sql.types.StructType schema = new org.apache.spark.sql.types.StructType(new StructField[]{
                DataTypes.createStructField("name", DataTypes.StringType, false),
                DataTypes.createStructField("age", DataTypes.IntegerType, false),
                DataTypes.createStructField("score", DataTypes.DoubleType, false)
        });

        List<Row> rows = List.of(
                RowFactory.create("Alice", 25, 95.5),
                RowFactory.create("Bob", 30, 88.0)
        );

        Dataset<Row> sparkDf = spark.createDataFrame(rows, schema);
        DataFrame smileDf = SparkDataFrames.toSmile(sparkDf);

        assertEquals(2, smileDf.size());
        assertEquals(3, smileDf.schema().length());
        assertEquals("Alice", smileDf.get(0).getString("name"));
        assertEquals(25, smileDf.get(0).getInt("age"));
        assertEquals(95.5, smileDf.get(0).getDouble("score"));
        assertEquals("Bob", smileDf.get(1).getString("name"));
        assertEquals(30, smileDf.get(1).getInt("age"));
        assertEquals(88.0, smileDf.get(1).getDouble("score"));
    }

    @Test
    void testSmileToSpark() throws Exception {
        DataFrame smileMushrooms = Read.arff(Paths.getTestData("weka/mushrooms.arff")).dropna();
        Dataset<Row> sparkDf = SmileDataFrames.toSpark(spark, smileMushrooms);

        assertEquals(smileMushrooms.size(), sparkDf.count());
        assertEquals(smileMushrooms.schema().length(), sparkDf.schema().length());

        DataFrame convertedBack = SparkDataFrames.toSmile(sparkDf);
        assertEquals(smileMushrooms.size(), convertedBack.size());
        assertEquals(smileMushrooms.schema().length(), convertedBack.schema().length());
    }

    @Test
    void testSparkStream() {
        org.apache.spark.sql.types.StructType schema = new org.apache.spark.sql.types.StructType(new StructField[]{
                DataTypes.createStructField("id", DataTypes.IntegerType, false),
                DataTypes.createStructField("val", DataTypes.DoubleType, false)
        });

        List<Row> rows = List.of(
                RowFactory.create(1, 10.0),
                RowFactory.create(2, 20.0),
                RowFactory.create(3, 30.0)
        );

        Dataset<Row> sparkDf = spark.createDataFrame(rows, schema);
        List<Tuple> list = SparkDataFrames.stream(sparkDf).toList();

        assertEquals(3, list.size());
        assertEquals(1, list.get(0).getInt(0));
        assertEquals(10.0, list.get(0).getDouble(1));
        assertEquals(3, list.get(2).getInt(0));
        assertEquals(30.0, list.get(2).getDouble(1));
    }
}
