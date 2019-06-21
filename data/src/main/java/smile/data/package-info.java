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

/**
 * Data and attribute encapsulation classes. A data is a set of datum objects,
 * which are usually defined by attribute-value pairs. The datum object could
 * be very sparse and thus is stored in a list to save space. A datum object
 * may have an associated class label (for classification) or real-valued
 * response value (for regression). Optionally, a datum object or attribute
 * may have a (positive) weight value, whose meaning depends on applications.
 * However, most machine learning methods are not able to utilize this extra
 * weight information. There are, generally speaking, two major types of attributes:
 * <dl>
 * <dt> <i>Qualitative variables:</i> </dt>
 * <dd> The data values are non-numeric categories. Examples: Blood type, Gender. </dd>
 * <dt> <i>Quantitative variables:</i> </dt>
 * <dd> The data values are counts or numerical measurements. A quantitative
 * variable can be either discrete such as the number of students receiving
 * an 'A' in a class, or continuous such as GPA, salary and so on.</dd>
 * </dl>
 * Another way of classifying data is by the measurement scales. In statistics,
 * there are four generally used measurement scales:
 * <dl>
 * <dt> <i>Nominal data:</i> </dt>
 * <dd> data values are non-numeric group labels. For example, Gender variable
 * can be defined as male = 0 and female =1. </dd>
 * <dt> <i>Ordinal data:</i> </dt>
 * <dd> data values are categorical and may be ranked in some numerically
 * meaningful way. For example, strongly disagree to strong agree may be
 * defined as 1 to 5. </dd>
 * <dt> <i>Continuous data:</i> </dt>
 * <dd>
 * <i> Interval data: </i>
 * data values are ranged in a real interval, which can be as large as
 * from negative infinity to positive infinity. The difference between two
 * values are meaningful, however, the ratio of two interval data is not
 * meaningful. For example temperature, IQ.
 * <br>
 * <i> Ratio data: </i>
 * both difference and ratio of two values are meaningful. For example,
 * salary, weight.
 * </dd>
 * </dl>
 *
 * Besides the semantics of data, it is also very important to pay attention
 * to the memory efficiency of data. In the Java memory model, all fields in
 * an object are either a primitive data type, such as byte or int, or a
 * reference or pointer to an object. Arrays of primitive data types, such as
 * char[], are also objects. One disadvantage of this model is that, when you
 * follow object-oriented design practices, data types are often composed of
 * many different types in order to encapsulate both state and behavior. As a
 * result, one data type can represent an entire tree of objects, which has
 * the high cost of overhead and locality.
 *
 * The cost of having many objects is that each object in a JVM must have some
 * metadata that is associated with it. For example, the java.lang.Class value
 * that represents the type of that object, or the length of an array object.
 * The most common approach is to place this metadata at the start of the
 * object, creating an object header.
 *
 * For a large or complex object, the size of the header is relatively
 * insignificant. For a small object, however, the size of the header can
 * become significant. For byte[1], 64 bits of metadata are often required
 * for a single 8-bit value. Additionally, the JVM is likely to add at least
 * 3 bytes of padding to ensure that the subsequent object in the heap
 * starts on an aligned address. The total extra memory requirement for
 * 8 bits of data is therefore 88 bits. Every object has a similar
 * associated overhead, so the more objects you have, the greater
 * the effect on system resources.
 *
 * The structure of Java arrays can exaggerate this overhead. Consider
 * an array of Complex objects. Each instance of the Complex class has
 * two double values, of 64 bits each, plus the object header. Assuming
 * that the header is just the class reference, and occupies only 32 bits,
 * each Point instance is 8 bytes of data and 4 bytes of extra overhead.
 * An array of 10 Complex objects consists of the header (class + length
 * = 8 bytes), plus 10 object references (assuming 4 bytes each = 40 bytes).
 * If each element of the array contains a unique Complex object, the total
 * is 160 bytes of data, but 88 bytes of additional overhead.
 *
 * The data locality of a tree of objects also has huge impact to compute
 * efficiency. Modern hardware relies heavily on caching and prefetching
 * to provide efficient access. Caching exploits the observation that
 * memory that was recently accessed is likely to be accessed again soon,
 * so keeping the most recently accessed data in very fast memory usually
 * results in the best performance. Data is cached in small blocks, which
 * are known as cache lines, to exploit another observation: data that is
 * stored in sequence is often accessed in sequence. Code that accesses
 * array[i] often proceeds to access array[i+1].
 *
 * When a data structure is composed of many different objects, an operation
 * on the information might need to access several objects to locate the
 * actual data. However, a tree of related objects cannot be guaranteed
 * to be close enough in memory to appear in the same block of cached memory.
 * Some JVM configurations attempt to keep related objects close to
 * each other in memory, but this result is not always possible.
 * Even when the JVM can place objects next to each other, the space
 * that is required by the object header lies between the objects, possibly
 * disrupting the benefit.
 *
 *
 * @author Haifeng Li
 */
package smile.data;
