/*
 * Copyright (c) 2010-2021 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */

/**
 * Missing value imputation. In statistics, missing data, or missing values,
 * occur when no data value is stored for the variable in the current
 * observation. Missing data are a common occurrence and can have a
 * significant effect on the conclusions that can be drawn from the data.
 * <p>
 * Data are missing for many reasons. Missing data can occur because of
 * non-response: no information is provided for several items or no information
 * is provided for a whole unit. Some items are more sensitive for non-response
 * than others, for example items about private subjects such as income.
 * <p>
 * Dropout is a type of missingness that occurs mostly when studying
 * development over time. In this type of study the measurement is repeated
 * after a certain period of time. Missingness occurs when participants drop
 * out before the test ends and one or more measurements are missing.
 * <p>
 * Sometimes missing values are caused by the device failure or even by
 * researchers themselves. It is important to question why the data is missing,
 * this can help with finding a solution to the problem. If the values are
 * missing at random there is still information about each variable in each
 * unit but if the values are missing systematically the problem is more severe
 * because the sample cannot be representative of the population.
 * <p>
 * All the causes for missing data fit into four classes, which are based
 * on the relationship between the missing data mechanism and the missing and
 * observed values. These classes are important to understand because the
 * problems caused by missing data and the solutions to these problems are
 * different for the four classes.
 * <p>
 * The first is Missing Completely at Random (MCAR). MCAR means that the
 * missing data mechanism is unrelated to the values of any variables, whether
 * missing or observed. Data that are missing because a researcher dropped the
 * test tubes or survey participants accidentally skipped questions are
 * likely to be MCAR. If the observed values are essentially a random sample
 * of the full data set, complete case analysis gives the same results as
 * the full data set would have. Unfortunately, most missing data are not MCAR.
 * <p>
 * At the opposite end of the spectrum is Non-Ignorable (NI). NI means that
 * the missing data mechanism is related to the missing values. It commonly
 * occurs when people do not want to reveal something very personal or
 * unpopular about themselves. For example, if individuals with higher incomes
 * are less likely to reveal them on a survey than are individuals with lower
 * incomes, the missing data mechanism for income is non-ignorable. Whether
 * income is missing or observed is related to its value. Complete case
 * analysis can give highly biased results for NI missing data. If
 * proportionally more low and moderate income individuals are left in
 * the sample because high income people are missing, an estimate of the
 * mean income will be lower than the actual population mean.
 * <p>
 * In between these two extremes are Missing at Random (MAR) and Covariate
 * Dependent (CD). Both of these classes require that the cause of the missing
 * data is unrelated to the missing values, but may be related to the observed
 * values of other variables. MAR means that the missing values are related to
 * either observed covariates or response variables, whereas CD means that the
 * missing values are related only to covariates. As an example of CD missing
 * data, missing income data may be unrelated to the actual income values, but
 * are related to education. Perhaps people with more education are less likely
 * to reveal their income than those with less education.
 * <p>
 * A key distinction is whether the mechanism is ignorable (i.e., MCAR, CD, or
 * MAR) or non-ignorable. There are excellent techniques for handling ignorable
 * missing data. Non-ignorable missing data are more challenging and require a
 * different approach.
 * <p>
 * If it is known that the data analysis technique which is to be used isn't
 * content robust, it is good to consider imputing the missing data.
 * Once all missing values have been imputed, the dataset can then be analyzed
 * using standard techniques for complete data. The analysis should ideally
 * take into account that there is a greater degree of uncertainty than if
 * the imputed values had actually been observed, however, and this generally
 * requires some modification of the standard complete-data analysis methods.
 * Many imputation techniques are available.
 * <p>
 * Imputation is not the only method available for handling missing data.
 * The expectation-maximization algorithm is a method for finding maximum
 * likelihood estimates that has been widely applied to missing data problems.
 * In machine learning, it is sometimes possible to train a classifier directly
 * over the original data without imputing it first. That was shown to yield
 * better performance in cases where the missing data is structurally absent,
 * rather than missing due to measurement noise.
 * 
 * @author Haifeng Li
 */
package smile.feature.imputation;
