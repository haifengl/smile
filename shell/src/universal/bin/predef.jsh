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
import org.apache.commons.csv.CSVFormat;
import smile.util.*;
import smile.math.*;
import static java.lang.Math.*;
import static smile.math.MathEx.*;
import smile.math.distance.*;
import smile.math.kernel.*;
import smile.math.matrix.*;
import smile.math.rbf.*;
import smile.stat.*;
import smile.stat.distribution.*;
import smile.stat.hypothesis.*;
import smile.data.*;
import smile.data.formula.*;
import static smile.data.formula.Terms.*;
import smile.data.measure.*;
import smile.data.type.*;
import smile.io.*;
import static java.awt.Color.*;
import smile.plot.swing.*;
import smile.interpolation.*;
import smile.validation.*;
import smile.validation.metric.*;
import smile.anomaly.IsolationForest;
import smile.association.*;
import smile.base.cart.SplitRule;
import smile.base.mlp.*;
import smile.base.rbf.RBF;
import smile.classification.*;
import smile.regression.OLS;
import smile.regression.LASSO;
import smile.regression.ElasticNet;
import smile.regression.RidgeRegression;
import smile.regression.GaussianProcessRegression;
import smile.regression.RegressionTree;
import smile.feature.*;
import smile.clustering.*;
import smile.vq.*;
import smile.manifold.*;
import smile.sequence.*;
import smile.projection.*;
import smile.nlp.*;
import smile.wavelet.*;

// In the below, add anything you want to execute at the start of the Shell session.
