/*
 * Copyright (c) 2026 Haifeng Li. All rights reserved.
 *
 * SPDX-License-Identifier: BUSL-1.1
 *
 * This software is licensed under the Business Source License version 1.1 (BSL 1.1).
 * Use of this work is governed by the BSL 1.1 terms and conditions set forth in
 * the studio/LICENSE file (or LICENSE file in standalone distributions) and at
 * https://mariadb.com/bsl11.
 *
 * Use of this work is strictly for evaluation and/or non-production purposes.
 * For commercial production use, please contact sales@aihalo.dev.
 *
 * Effective on the Change Date (four years from the first publication of this
 * version), this file automatically converts to the GNU Affero General Public
 * License version 3.0 (AGPLv3) or later.
 */

// Welcome message
println("\n" + smile.shell.JShell.logo);
println("Welcome to SMILE " + smile.shell.JShell.version);
println("===============================================================================");

// Imports Smile packages.
import scala.language.existentials
import scala.language.postfixOps
import java.awt.Color
import java.lang.Math.*
import java.time.*
import org.apache.commons.csv.CSVFormat
import smile.*
import smile.util.*
import smile.graph.*
import smile.math.*
import smile.math.MathEx.{log2, sigmoid, factorial, lfactorial, choose, lchoose, random, randomInt, permutate, c, cbind, rbind, sum, mean, median, q1, q3, `var` => variance, stdev, mad, min, max, whichMin, whichMax, unique, dot, distance, pdist, pdot, KullbackLeiblerDivergence => kld, JensenShannonDivergence => jsd, cov, cor, spearman, kendall, norm, norm1, norm2, normInf, standardize, normalize, scale, unitize, unitize1, unitize2}
import smile.math.distance.*
import smile.math.kernel.*
import smile.math.rbf.*
import smile.stat.distribution.*
import smile.tensor.*
import smile.data.*
import smile.data.formula.*
import smile.data.formula.Terms.$
import smile.data.measure.*
import smile.data.`type`.*
import smile.data.vector.*
import java.awt.Color.{BLACK, BLUE, CYAN, DARK_GRAY, GRAY, GREEN, LIGHT_GRAY, MAGENTA, ORANGE, PINK, RED, WHITE, YELLOW}
import smile.plot.swing.Palette.{DARK_RED, DARK_GREEN, LIGHT_GREEN, FOREST_GREEN, SLATE_BLUE, ROYAL_BLUE, CADET_BLUE, MIDNIGHT_BLUE, SKY_BLUE, STEEL_BLUE, DARK_BLUE, DARK_MAGENTA, DARK_CYAN, PURPLE, GOLD, BROWN, SALMON, TURQUOISE, PLUM}
import smile.plot.swing.*
import smile.plot.show
import smile.json.*
import smile.interpolation.*
import smile.validation.*
import smile.validation.metric.*
import smile.anomaly.IsolationForest
import smile.association.*
import smile.model.cart.SplitRule
import smile.model.mlp.*
import smile.model.rbf.RBF
import smile.classification.*
import smile.regression.{lm, ridge, lasso, gpr}
import smile.feature.*
import smile.feature.extraction.*
import smile.clustering.*
import smile.hpo.*
import smile.vq.*
import smile.manifold.*
import smile.sequence.*
import smile.nlp.*
import smile.wavelet.*

// In the below, add anything you want to execute at the start of the Shell session.
