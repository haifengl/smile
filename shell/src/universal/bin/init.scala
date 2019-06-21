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

/** Shell initialization script.
  *
  * @author Haifeng Li
  */

import smile._
import smile.util._
import smile.math._
import java.lang.Math._
import MathEx.{log2, logistic, factorial, choose, random, randomInt, permutate, c, cbind, rbind, sum, mean, median, q1, q3, `var` => variance, sd, mad, min, max, whichMin, whichMax, unique, dot, distance, pdist, KullbackLeiblerDivergence => kld, JensenShannonDivergence => jsd, cov, cor, spearman, kendall, norm, norm1, norm2, normInf, standardize, normalize, scale, unitize, unitize1, unitize2, root}
import smile.math.distance._
import smile.math.kernel._
import smile.math.matrix._
import smile.math.matrix.Matrix._
import smile.stat.distribution._
import smile.data._
import java.awt.Color, smile.plot._
import smile.interpolation._
import smile.validation._
import smile.association._
import smile.classification._
import smile.regression.{ols, ridge, lasso, svr, gpr}
import smile.feature._
import smile.clustering._
import smile.vq._
import smile.manifold._
import smile.mds._
import smile.sequence._
import smile.projection._
import smile.nlp._
import smile.wavelet._
import smile.shell._
