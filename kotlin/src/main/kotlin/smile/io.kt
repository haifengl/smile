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

package smile

import java.io.*
import java.nio.file.Path
import java.nio.file.Paths
import java.sql.ResultSet
import org.apache.commons.csv.CSVFormat
import smile.data.DataFrame
import smile.data.Dataset
import smile.data.Instance
import smile.data.`type`.StructType
import smile.io.Read
import smile.io.Write
import smile.io.JSON
import smile.util.SparseArray

/** Data saving utilities. */
object write {
    /** Writes a data frame to an Apache Arrow file. */
    fun arrow(data: DataFrame, file: String): Unit {
        arrow(data, Paths.get(file))
    }

    /** Writes a data frame to an Apache Arrow file. */
    fun arrow(data: DataFrame, file: Path): Unit {
        Write.arrow(data, file)
    }

    /** Writes a data frame to an ARFF file. */
    fun arff(data: DataFrame, file: String, relation: String): Unit {
        arff(data, Paths.get(file), relation)
    }

    /** Writes a data frame to an ARFF file. */
    fun arff(data: DataFrame, file: Path, relation: String): Unit {
        Write.arff(data, file, relation)
    }

    /** Writes a DataFrame to a delimited text file.
     *
     * @param data an attribute dataset.
     * @param file the file path
     */
    fun csv(data: DataFrame, file: String, delimiter: Char = ','): Unit {
        csv(data, Paths.get(file), delimiter)
    }

    /** Writes a DataFrame to a delimited text file.
     *
     * @param data an attribute dataset.
     * @param file the file path
     */
    fun csv(data: DataFrame, file: Path, delimiter: Char): Unit {
        val format = CSVFormat.DEFAULT.withDelimiter(delimiter)
        Write.csv(data, file, format)
    }
}

/** Data loading utilities. */
object read {
    /** Reads a JDBC query result to a data frame. */
    fun jdbc(rs: ResultSet): DataFrame {
        return DataFrame.of(rs)
    }

    /** Reads a CSV file. */
    fun csv(file: String, delimiter: Char = ',', header: Boolean = true, quote: Char = '"', escape: Char = '\\', schema: StructType? = null): DataFrame {
        var format = CSVFormat.DEFAULT.withDelimiter(delimiter).withQuote(quote).withEscape(escape)
        if (header) format = format.withFirstRecordAsHeader()
        return Read.csv(file, format, schema)
    }

    /** Reads a CSV file. */
    fun csv(file: Path, delimiter: Char = ',', header: Boolean = true, quote: Char = '"', escape: Char = '\\', schema: StructType? = null): DataFrame {
        var format = CSVFormat.DEFAULT.withDelimiter(delimiter).withQuote(quote).withEscape(escape)
        if (header) format = format.withFirstRecordAsHeader()
        return Read.csv(file, format, schema)
    }

    /** Reads a CSV file. */
    fun csv(file: String, format: CSVFormat, schema: StructType? = null): DataFrame {
        return Read.csv(file, format, schema)
    }

    /** Reads a CSV file. */
    fun csv(file: Path, format: CSVFormat, schema: StructType? = null): DataFrame {
        return Read.csv(file, format, schema)
    }

    /** Reads a JSON file. */
    fun json(file: String): DataFrame {
        return Read.json(file)
    }

    /** Reads a JSON file. */
    fun json(file: Path): DataFrame {
        return Read.json(file)
    }

    /** Reads a JSON file. */
    fun json(file: String, mode: JSON.Mode, schema: StructType): DataFrame {
        return Read.json(file, mode, schema)
    }

    /** Reads a JSON file. */
    fun json(file: Path, mode: JSON.Mode, schema: StructType): DataFrame {
        return Read.json(file, mode, schema)
    }

    /** Reads an ARFF file. */
    fun arff(file: String): DataFrame {
        return Read.arff(file)
    }

    /** Reads an ARFF file. */
    fun arff(file: Path): DataFrame {
        return Read.arff(file)
    }

    /** Reads a SAS7BDAT file. */
    fun sas(file: String): DataFrame {
        return Read.sas(file)
    }

    /** Reads a SAS7BDAT file. */
    fun sas(file: Path): DataFrame {
        return Read.sas(file)
    }

    /** Reads an Apache Arrow file. */
    fun arrow(file: String): DataFrame {
        return Read.arrow(file)
    }

    /** Reads an Apache Arrow file. */
    fun arrow(file: Path): DataFrame {
        return Read.arrow(file)
    }

    /** Reads an Apache Avro file. */
    fun avro(file: String, schema: InputStream): DataFrame {
        return Read.avro(file, schema)
    }

    /** Reads an Apache Avro file. */
    fun avro(file: String, schema: String): DataFrame {
        return Read.avro(file, schema)
    }

    /** Reads an Apache Avro file. */
    fun avro(file: Path, schema: InputStream): DataFrame {
        return Read.avro(file, schema)
    }

    /** Reads an Apache Avro file. */
    fun avro(file: Path, schema: Path): DataFrame {
        return Read.avro(file, schema)
    }

    /** Reads an Apache Parquet file. */
    fun parquet(file: String): DataFrame {
        return Read.parquet(file)
    }

    /** Reads an Apache Parquet file. */
    fun parquet(file: Path): DataFrame {
        return Read.parquet(file)
    }

    /** Reads a LivSVM file. */
    fun libsvm(file: String): Dataset<Instance<SparseArray>> {
        return Read.libsvm(file)
    }

    /** Reads a LivSVM file. */
    fun libsvm(file: Path): Dataset<Instance<SparseArray>> {
        return Read.libsvm(file)
    }
}
