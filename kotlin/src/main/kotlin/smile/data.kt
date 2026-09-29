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
package smile.data

import java.util.Locale
import java.util.stream.IntStream
import smile.data.measure.CategoricalMeasure
import smile.data.vector.ValueVector
import smile.math.MathEx
import smile.util.Index

// ─────────────────────────────────────────────────────────────────────────────
// Summary statistics
// ─────────────────────────────────────────────────────────────────────────────

/** Prints the statistics of min, q1, median, mean, q3, and max. */
fun summary(x: IntArray) {
    println("min\tq1\tmedian\tmean\tq3\tmax")
    val min = MathEx.min(x)
    val q1 = MathEx.q1(x)
    val median = MathEx.median(x)
    val mean = Math.round(MathEx.mean(x)).toInt()
    val q3 = MathEx.q3(x)
    val max = MathEx.max(x)
    println(String.format("%d\t%d\t%d\t%d\t%d\t%d", min, q1, median, mean, q3, max))
}

/** Prints the statistics of min, q1, median, mean, q3, and max. */
fun summary(x: DoubleArray) {
    println("min\t\tq1\t\tmedian\t\tmean\t\tq3\t\tmax")
    val min = MathEx.min(x)
    val q1 = MathEx.q1(x)
    val median = MathEx.median(x)
    val mean = MathEx.mean(x)
    val q3 = MathEx.q3(x)
    val max = MathEx.max(x)
    println(String.format(Locale.US, "%-10.4f\t%-10.4f\t%-10.4f\t%-10.4f\t%-10.4f\t%-10.4f", min, q1, median, mean, q3, max))
}

// ─────────────────────────────────────────────────────────────────────────────
// DataFrame functional extensions
// ─────────────────────────────────────────────────────────────────────────────

/** Selects a new DataFrame with given column indices. */
fun DataFrame.select(range: IntProgression): DataFrame = select(*range.toList().toIntArray())

/** Returns a new DataFrame without given column indices. */
fun DataFrame.drop(range: IntProgression): DataFrame = drop(*range.toList().toIntArray())

/** Returns a new DataFrame with row indexing by range. */
fun DataFrame.of(range: IntProgression): DataFrame {
    return if (range.step == 1) {
        slice(range.first, range.last + 1)
    } else {
        val indices = range.toList().toIntArray()
        get(Index.of(*indices))
    }
}

/** Finds the first row satisfying a predicate, or null if none. */
fun DataFrame.find(predicate: (Tuple) -> Boolean): Tuple? = stream().filter(predicate).findAny().orElse(null)

/** Tests if a predicate holds for at least one row of the data frame. */
fun DataFrame.exists(predicate: (Tuple) -> Boolean): Boolean = stream().anyMatch(predicate)

/** Tests if a predicate holds for all rows of the data frame. */
fun DataFrame.forall(predicate: (Tuple) -> Boolean): Boolean = stream().allMatch(predicate)

/** Selects all rows which satisfy a predicate. */
fun DataFrame.filter(predicate: (Tuple) -> Boolean): DataFrame {
    val indices = IntStream.range(0, size()).filter { i -> predicate(get(i)) }.toArray()
    return get(Index.of(*indices))
}

/**
 * Partitions this DataFrame into two according to a predicate.
 *
 * @param predicate the condition on which to partition.
 * @return a pair of DataFrames: first satisfies the predicate, second does not.
 */
fun DataFrame.partition(predicate: (Tuple) -> Boolean): Pair<DataFrame, DataFrame> {
    val l = ArrayList<Int>()
    val r = ArrayList<Int>()
    for (i in 0 until size()) {
        if (predicate(get(i))) l.add(i) else r.add(i)
    }
    return Pair(get(Index.of(*l.toIntArray())), get(Index.of(*r.toIntArray())))
}

/**
 * Partitions the DataFrame into a map of DataFrames according to a discriminator function.
 *
 * @param keySelector the discriminator function.
 * @return a map from keys to partitioned DataFrames.
 */
fun <K> DataFrame.groupBy(keySelector: (Tuple) -> K): Map<K, DataFrame> {
    val groups = LinkedHashMap<K, MutableList<Int>>()
    for (i in 0 until size()) {
        val key = keySelector(get(i))
        groups.computeIfAbsent(key) { ArrayList() }.add(i)
    }
    return groups.mapValues { (_, indices) -> get(Index.of(*indices.toIntArray())) }
}

/** Builds a new list by applying a function to all rows. */
fun <U> DataFrame.map(transform: (Tuple) -> U): List<U> = (0 until size()).map { transform(get(it)) }

// ─────────────────────────────────────────────────────────────────────────────
// Operator overloading: indexing ([])
// ─────────────────────────────────────────────────────────────────────────────

/** Returns a column as a ValueVector by name: `df["columnName"]`. */
operator fun DataFrame.get(name: String): ValueVector = column(name)

/** Selects multiple columns by names: `df["col1", "col2"]`. */
operator fun DataFrame.get(vararg names: String): DataFrame = select(*names)

/** Slices rows by IntProgression: `df[0 until 10]` or `df[0..9]`. */
operator fun DataFrame.get(range: IntProgression): DataFrame = of(range)

/** Returns a cell value by row index and column name: `df[0, "colName"]`. */
operator fun DataFrame.get(i: Int, colName: String): Any? = get(i, schema().indexOf(colName))

/** Sets a cell value by row index and column name: `df[0, "colName"] = value`. */
operator fun DataFrame.set(i: Int, colName: String, value: Any?) {
    set(i, schema().indexOf(colName), value)
}

// ─────────────────────────────────────────────────────────────────────────────
// Operator overloading: invocation ()
// ─────────────────────────────────────────────────────────────────────────────

/** Returns row at index i: `df(i)`. */
operator fun DataFrame.invoke(i: Int): Tuple = get(i)

/** Returns column by name: `df("name")`. */
operator fun DataFrame.invoke(name: String): ValueVector = column(name)

/** Selects columns by names: `df("col1", "col2")`. */
operator fun DataFrame.invoke(vararg names: String): DataFrame = select(*names)

/** Returns cell value at (i, j): `df(i, j)`. */
operator fun DataFrame.invoke(i: Int, j: Int): Any? = get(i, j)

/** Returns cell value at row i and column name: `df(i, "colName")`. */
operator fun DataFrame.invoke(i: Int, colName: String): Any? = get(i, schema().indexOf(colName))

/** Slices rows by IntProgression: `df(0 until 10)`. */
operator fun DataFrame.invoke(range: IntProgression): DataFrame = of(range)

/** Filters rows by boolean mask: `df(mask)`. */
operator fun DataFrame.invoke(index: BooleanArray): DataFrame = get(index)

/** Filters rows by Index: `df(index)`. */
operator fun DataFrame.invoke(index: Index): DataFrame = get(index)

/** Filters rows using a predicate: `df { it.getDouble("col") > 5.0 }`. */
operator fun DataFrame.invoke(predicate: (Tuple) -> Boolean): DataFrame = filter(predicate)

/** Returns field value at position i: `tuple(i)`. */
operator fun Tuple.invoke(i: Int): Any? = get(i)

/** Returns field value by name: `tuple("field")`. */
operator fun Tuple.invoke(field: String): Any? = get(field)

/** Returns element at index i: `vector(i)`. */
operator fun ValueVector.invoke(i: Int): Any? = get(i)

// ─────────────────────────────────────────────────────────────────────────────
// Operator overloading: containment (in)
// ─────────────────────────────────────────────────────────────────────────────

/** Checks if a column exists in the DataFrame: `"col" in df`. */
operator fun DataFrame.contains(columnName: String): Boolean = schema().indexOf(columnName) >= 0

/** Checks if a field exists in the Tuple: `"field" in tuple`. */
operator fun Tuple.contains(fieldName: String): Boolean = schema().indexOf(fieldName) >= 0

// ─────────────────────────────────────────────────────────────────────────────
// Operator overloading: arithmetic (+ and -)
// ─────────────────────────────────────────────────────────────────────────────

/** Concatenates two DataFrames vertically: `df1 + df2`. */
operator fun DataFrame.plus(other: DataFrame): DataFrame = concat(other)

/** Adds a column to a new DataFrame copy: `df + vector`. */
operator fun DataFrame.plus(column: ValueVector): DataFrame {
    val newCols = ArrayList(columns)
    newCols.add(column)
    return if (index != null) {
        DataFrame(index, *newCols.toTypedArray())
    } else {
        DataFrame(*newCols.toTypedArray())
    }
}

/** Drops a column by name, returning a new DataFrame: `df - "unwantedCol"`. */
operator fun DataFrame.minus(columnName: String): DataFrame = drop(columnName)

/** Drops columns by names, returning a new DataFrame: `df - listOf("col1", "col2")`. */
operator fun DataFrame.minus(columnNames: Collection<String>): DataFrame = drop(*columnNames.toTypedArray())

/** Drops columns by names, returning a new DataFrame: `df - arrayOf("col1", "col2")`. */
operator fun DataFrame.minus(columnNames: Array<String>): DataFrame = drop(*columnNames)

// ─────────────────────────────────────────────────────────────────────────────
// Destructuring support for Tuple (component1 .. component16)
// ─────────────────────────────────────────────────────────────────────────────

operator fun Tuple.component1(): Any? = get(0)
operator fun Tuple.component2(): Any? = get(1)
operator fun Tuple.component3(): Any? = get(2)
operator fun Tuple.component4(): Any? = get(3)
operator fun Tuple.component5(): Any? = get(4)
operator fun Tuple.component6(): Any? = get(5)
operator fun Tuple.component7(): Any? = get(6)
operator fun Tuple.component8(): Any? = get(7)
operator fun Tuple.component9(): Any? = get(8)
operator fun Tuple.component10(): Any? = get(9)
operator fun Tuple.component11(): Any? = get(10)
operator fun Tuple.component12(): Any? = get(11)
operator fun Tuple.component13(): Any? = get(12)
operator fun Tuple.component14(): Any? = get(13)
operator fun Tuple.component15(): Any? = get(14)
operator fun Tuple.component16(): Any? = get(15)

// ─────────────────────────────────────────────────────────────────────────────
// JSON serialization
// ─────────────────────────────────────────────────────────────────────────────

/** Converts this Tuple to a JSON string representation. */
fun Tuple.toJSON(): String {
    val schema = schema()
    val sb = StringBuilder("{")
    for (i in 0 until length()) {
        if (i > 0) sb.append(", ")
        val field = schema.field(i)
        sb.append('"').append(escapeJson(field.name())).append("\": ")
        if (isNullAt(i)) {
            sb.append("null")
        } else if (field.measure() is CategoricalMeasure) {
            sb.append('"').append(escapeJson(getString(i))).append('"')
        } else {
            val v = get(i)
            when (v) {
                null -> sb.append("null")
                is Boolean, is Number -> sb.append(v)
                else -> sb.append('"').append(escapeJson(v.toString())).append('"')
            }
        }
    }
    sb.append("}")
    return sb.toString()
}

/** Converts this DataFrame to a JSON string representation. */
fun DataFrame.toJSON(): String {
    val sb = StringBuilder("[\n")
    for (i in 0 until size()) {
        if (i > 0) sb.append(",\n")
        sb.append("  ").append(get(i).toJSON())
    }
    sb.append("\n]")
    return sb.toString()
}

private fun escapeJson(s: String): String {
    val sb = StringBuilder()
    for (c in s) {
        when (c) {
            '"' -> sb.append("\\\"")
            '\\' -> sb.append("\\\\")
            '\b' -> sb.append("\\b")
            '\n' -> sb.append("\\n")
            '\r' -> sb.append("\\r")
            '\t' -> sb.append("\\t")
            else -> {
                if (c.code in 0..0x1F) {
                    sb.append(String.format("\\u%04x", c.code))
                } else {
                    sb.append(c)
                }
            }
        }
    }
    return sb.toString()
}
