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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.RowFactory;
import org.apache.spark.sql.SparkSession;
import smile.data.DataFrame;
import smile.data.Tuple;

/**
 * Converts a SMILE {@link DataFrame} into an Apache Spark SQL {@link Dataset}&lt;{@link Row}&gt;.
 *
 * @author Haifeng Li
 */
public final class SmileDataFrames {

    private SmileDataFrames() {
    }

    /**
     * Converts a SMILE {@link Tuple} to an Apache Spark {@link Row}.
     *
     * @param tuple the SMILE tuple.
     * @return the Spark row.
     */
    public static Row toRow(Tuple tuple) {
        if (tuple instanceof SparkRowTuple srt) {
            return srt.row();
        }
        int n = tuple.length();
        Object[] values = new Object[n];
        for (int i = 0; i < n; i++) {
            if (tuple.isNullAt(i)) {
                values[i] = null;
            } else {
                Object val = tuple.get(i);
                if (val instanceof LocalDate d) {
                    values[i] = java.sql.Date.valueOf(d);
                } else if (val instanceof LocalDateTime dt) {
                    values[i] = java.sql.Timestamp.valueOf(dt);
                } else if (val instanceof Tuple sub) {
                    values[i] = toRow(sub);
                } else {
                    values[i] = val;
                }
            }
        }
        return RowFactory.create(values);
    }

    /**
     * Converts a local SMILE DataFrame into a distributed Apache Spark Dataset of Rows.
     *
     * @param spark the active SparkSession.
     * @param df    the SMILE DataFrame.
     * @return the distributed Spark DataFrame.
     */
    public static Dataset<Row> toSpark(SparkSession spark, DataFrame df) {
        Objects.requireNonNull(spark, "spark cannot be null");
        Objects.requireNonNull(df, "df cannot be null");
        org.apache.spark.sql.types.StructType schema = DataTypeUtils.toSparkSchema(df.schema());
        List<Row> rows = new ArrayList<>(df.size());
        for (Tuple tuple : df) {
            rows.add(toRow(tuple));
        }
        return spark.createDataFrame(rows, schema);
    }
}
