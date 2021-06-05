/*******************************************************************************
 * Copyright (c) 2010-2020 Haifeng Li. All rights reserved.
 *
 * Smile Shell is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile Shell is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 ******************************************************************************/

// Imports Smile packages.
import scala.language.existentials
import scala.language.postfixOps
import org.apache.commons.csv.CSVFormat
import smile._
import smile.util._
import smile.math._
import java.lang.Math._
import smile.math.MathEx.{log2, sigmoid, factorial, lfactorial, choose, lchoose, random, randomInt, permutate, c, cbind, rbind, sum, mean, median, q1, q3, `var` => variance, sd, mad, min, max, whichMin, whichMax, unique, dot, distance, pdist, pdot, KullbackLeiblerDivergence => kld, JensenShannonDivergence => jsd, cov, cor, spearman, kendall, norm, norm1, norm2, normInf, standardize, normalize, scale, unitize, unitize1, unitize2}
import smile.math.distance._
import smile.math.kernel._
import smile.math.matrix._
import smile.math.matrix.Matrix._
import smile.math.rbf._
import smile.stat.distribution._
import smile.data._
import smile.data.formula._
import smile.data.formula.Terms.$
import smile.data.measure._
import smile.data.`type`._
import java.awt.Color.{BLACK, BLUE, CYAN, DARK_GRAY, GRAY, GREEN, LIGHT_GRAY, MAGENTA, ORANGE, PINK, RED, WHITE, YELLOW}
import smile.plot.swing.Palette.{DARK_RED, VIOLET_RED, DARK_GREEN, LIGHT_GREEN, PASTEL_GREEN, FOREST_GREEN, GRASS_GREEN, NAVY_BLUE, SLATE_BLUE, ROYAL_BLUE, CADET_BLUE, MIDNIGHT_BLUE, SKY_BLUE, STEEL_BLUE, DARK_BLUE, DARK_MAGENTA, DARK_CYAN, PURPLE, LIGHT_PURPLE, DARK_PURPLE, GOLD, BROWN, SALMON, TURQUOISE, BURGUNDY, PLUM}
import smile.plot.swing._
import smile.plot.vega._
import smile.plot.show
import smile.plot.Render.{renderVega, renderCanvas, renderPlotGrid}
import smile.json._
import smile.interpolation._
import smile.validation._
import smile.validation.metric._
import smile.anomaly.IsolationForest
import smile.association._
import smile.base.cart.SplitRule
import smile.base.mlp._
import smile.base.rbf.RBF
import smile.classification._
import smile.regression.{lm, ridge, lasso, gpr}
import smile.feature._
import smile.clustering._
import smile.vq._
import smile.manifold._
import smile.sequence._
import smile.projection._
import smile.nlp._
import smile.wavelet._
import smile.shell._

// In the below, add anything you want to execute at the start of the Shell session.
