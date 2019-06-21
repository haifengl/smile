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
 * Statistical hypothesis tests. A statistical hypothesis test is a method
 * of making decisions using data, whether from a controlled experiment or
 * an observational study (not controlled). In statistics, a result is called
 * statistically significant if it is unlikely to have occurred by chance alone,
 * according to a pre-determined threshold probability, the significance level.
 * <p>
 * Hypothesis testing is sometimes called confirmatory data analysis, in
 * contrast to exploratory data analysis. In frequency probability, these
 * decisions are almost always made using null-hypothesis tests (i.e., tests
 * that answer the question Assuming that the null hypothesis is true, what
 * is the probability of observing a value for the test statistic that is at
 * least as extreme as the value that was actually observed?) One use of
 * hypothesis testing is deciding whether experimental results contain enough
 * information to cast doubt on conventional wisdom.
 * <p>
 * A result that was found to be statistically significant is also called a
 * positive result; conversely, a result that is not unlikely under the null
 * hypothesis is called a negative result or a null result.
 * <p>
 * Statistical hypothesis testing is a key technique of frequentist statistical
 * inference. The Bayesian approach to hypothesis testing is to base rejection
 * of the hypothesis on the posterior probability. Other approaches to reaching
 * a decision based on data are available via decision theory and optimal
 * decisions.
 *
 * @author Haifeng Li
 */
package smile.stat.hypothesis;