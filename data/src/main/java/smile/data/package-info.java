/*******************************************************************************
 * Copyright (c) 2010 Haifeng Li
 *   
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *  
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
 * @author Haifeng Li
 */
package smile.data;
