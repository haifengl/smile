/*******************************************************************************
 * Copyright (c) 2010-2024 Haifeng Li. All rights reserved.
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

// Define Smile feedback mode and prompt
/set mode smile -command
/set prompt smile "%nsmile> " "  ...> "
/set format smile action "created" added-primary
/set format smile action "modified" modified-primary
/set format smile action "replaced" replaced-primary
/set format smile action "overwrote" overwrote-primary
/set format smile action "dropped" dropped-primary
/set format smile action "  update created" added-update
/set format smile action "  update modified" modified-update
/set format smile action "  update replaced" replaced-update
/set format smile action "  update overwrote" overwrote-update
/set format smile action "  update dropped" dropped-update
/set format smile display "{result}{pre}created scratch variable {name} : {type}{post}" expression-added,modified,replaced-primary
/set format smile display "{result}{pre}value of {name} : {type}{post}" varvalue-added,modified,replaced-primary
/set format smile display "{result}{pre}assigned to {name} : {type}{post}" assignment-primary
/set format smile display "{result}{pre}{action} variable {name} : {type}{resolve}{post}" varinit,vardecl
/set format smile display "{pre}{action} variable {name}{resolve}{post}" vardecl,varinit-notdefined
/set format smile display "{pre}{action} variable {name}{post}" dropped-vardecl,varinit,expression
/set format smile display "{pre}{action} {typeKind} {name}{resolve}{post}" class,interface,enum,annotation,record
/set format smile display "{pre}{action} method {name}({type}){resolve}{post}" method
/set format smile display "{pre}attempted to use {typeKind} {name}{resolve}{post}" used-class,interface,enum,annotation,record
/set format smile display "{pre}attempted to call method {name}({type}){resolve}{post}" used-method
/set format smile display "" added,modified,replaced,overwrote,dropped-update
/set format smile display "{pre}{action} variable {name}, reset to null{post}" replaced-vardecl,varinit-ok-update
/set format smile display "{pre}{action} variable {name}{resolve}{post}" replaced-vardecl,varinit-notdefined
/set format smile display "{result}" added,modified,replaced-expression,varvalue,assignment,varinit,vardecl-ok-primary
/set format smile err "%6$s"
/set format smile errorline "{post}{pre}    {err}"
/set format smile errorpost "%n"
/set format smile errorpre "|  "
/set format smile errors "%5$s"
/set format smile name "%1$s"
/set format smile post "%n"
/set format smile pre "|  "
/set format smile resolve "{until}{unrerr}" defined,notdefined-added,modified,replaced,used
/set format smile result "{name} ==> {value}{post}" added,modified,replaced-ok-primary
/set format smile type "%2$s"
/set format smile typeKind "class" class
/set format smile typeKind "interface" interface
/set format smile typeKind "enum" enum
/set format smile typeKind "annotation interface" annotation
/set format smile typeKind "record" record
/set format smile unrerr "{unresolved} is declared" unresolved1-error0
/set format smile unrerr "{unresolved} are declared" unresolved2-error0
/set format smile unrerr " this error is corrected: {errors}" unresolved0-error1
/set format smile unrerr "{unresolved} is declared and this error is corrected: {errors}" unresolved1-error1
/set format smile unrerr "{unresolved} are declared and this error is corrected: {errors}" unresolved2-error1
/set format smile unrerr " these errors are corrected: {errors}" unresolved0-error2
/set format smile unrerr "{unresolved} is declared and these errors are corrected: {errors}" unresolved1-error2
/set format smile unrerr "{unresolved} are declared and these errors are corrected: {errors}" unresolved2-error2
/set format smile unresolved "%4$s"
/set format smile until ", however, it cannot be instantiated or its methods invoked until" defined-class,record-primary
/set format smile until ", however, its methods cannot be invoked until" defined-interface-primary
/set format smile until ", however, it cannot be used until" defined-enum,annotation-primary
/set format smile until ", however, it cannot be invoked until" defined-method-primary
/set format smile until ", however, it cannot be referenced until" notdefined-primary
/set format smile until " which cannot be instantiated or its methods invoked until" defined-class,record-update
/set format smile until " whose methods cannot be invoked until" defined-interface-update
/set format smile until " which cannot be invoked until" defined-method-update
/set format smile until " which cannot be referenced until" notdefined-update
/set format smile value "%3$s"

// Welcome message
println("""

                                                       ..::''''::..
                                                     .;''        ``;.
     ....                                           ::    ::  ::    ::
   ,;' .;:                ()  ..:                  ::     ::  ::     ::
   ::.      ..:,:;.,:;.    .   ::   .::::.         :: .:' ::  :: `:. ::
    '''::,   ::  ::  ::  `::   ::  ;:   .::        ::  :          :  ::
  ,:';  ::;  ::  ::  ::   ::   ::  ::,::''.         :: `:.      .:' ::
  `:,,,,;;' ,;; ,;;, ;;, ,;;, ,;;, `:,,,,:'          `;..``::::''..;'
                                                       ``::,,,,::''
|  Welcome to Smile  -- Version  4.1.0
===============================================================================""")


// Imports Smile packages.
import java.awt.Color;
import java.time.*;
import org.apache.commons.csv.CSVFormat;
import smile.util.*;
import smile.graph.*;
import smile.math.*;
import static java.lang.Math.*;
import static smile.math.MathEx.*;
import smile.math.distance.*;
import smile.math.kernel.*;
import smile.math.rbf.*;
import smile.stat.*;
import smile.stat.distribution.*;
import smile.stat.hypothesis.*;
import smile.tensor.*;
import smile.data.*;
import smile.data.formula.*;
import static smile.data.formula.Terms.*;
import smile.data.measure.*;
import smile.data.type.*;
import smile.data.vector.*;
import smile.io.*;
import static smile.plot.swing.Palette.*;
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
import smile.feature.extraction.*;
import smile.feature.importance.*;
import smile.feature.imputation.*;
import smile.feature.selection.*;
import smile.feature.transform.*;
import smile.clustering.*;
import smile.hpo.*;
import smile.vq.*;
import smile.manifold.*;
import smile.sequence.*;
import smile.nlp.*;
import smile.wavelet.*;

// In the below, add anything you want to execute at the start of the Shell session.
