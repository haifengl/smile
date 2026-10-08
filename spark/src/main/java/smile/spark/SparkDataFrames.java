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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import smile.data.DataFrame;
import smile.data.Tuple;
import smile.data.type.StructType;

/**
 * Converts an Apache Spark SQL {@link Dataset}&lt;{@link Row}&gt; to a SMILE {@link DataFrame}.
 *
 * @author Haifeng Li
 */
public final class SparkDataFrames {

    private SparkDataFrames() {
    }

    /**
     * Converts an Apache Spark Dataset of Rows into an in-memory SMILE DataFrame.
     *
     * @param df the Spark DataFrame.
     * @return the SMILE DataFrame.
     */
    public static DataFrame toSmile(Dataset<Row> df) {
        Objects.requireNonNull(df, "df cannot be null");
        StructType schema = DataTypeUtils.toSmileSchema(df.schema());
        List<Row> rows = df.collectAsList();
        List<Tuple> tuples = new ArrayList<>(rows.size());
        for (Row row : rows) {
            tuples.add(new SparkRowTuple(schema, row));
        }
        return DataFrame.of(schema, tuples);
    }

    /**
     * Streams rows from a Spark Dataset as SMILE {@link Tuple}s without collecting
     * the entire dataset into a single list in memory.
     *
     * @param df the Spark DataFrame.
     * @return a stream of SMILE Tuples.
     */
    public static Stream<Tuple> stream(Dataset<Row> df) {
        Objects.requireNonNull(df, "df cannot be null");
        StructType schema = DataTypeUtils.toSmileSchema(df.schema());
        Iterator<Row> it = df.toLocalIterator();
        Iterator<Tuple> tupleIt = new Iterator<>() {
            @Override
            public boolean hasNext() {
                return it.hasNext();
            }

            @Override
            public Tuple next() {
                return new SparkRowTuple(schema, it.next());
            }
        };
        return StreamSupport.stream(
                Spliterators.spliteratorUnknownSize(tupleIt, Spliterator.ORDERED),
                false
        );
    }
}
