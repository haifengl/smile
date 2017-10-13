/*******************************************************************************
 * (C) Copyright 2015 Haifeng Li
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

package smile.shell

import ammonite.ops.Path
import ammonite.runtime.Storage

/** Ammonite REPL based shell.
  *
  * @author Haifeng Li
  */
object AmmoniteREPL {
  val home = Path(System.getProperty("user.home")) / ".smile"
  val prompt = "smile> "
  val welcome =
    s"""
       |                                                       ..::''''::..
       |                                                     .;''        ``;.
       |     ....                                           ::    ::  ::    ::
       |   ,;' .;:                ()  ..:                  ::     ::  ::     ::
       |   ::.      ..:,:;.,:;.    .   ::   .::::.         :: .:' ::  :: `:. ::
       |    '''::,   ::  ::  ::  `::   ::  ;:   .::        ::  :          :  ::
       |  ,:';  ::;  ::  ::  ::   ::   ::  ::,::''.         :: `:.      .:' ::
       |  `:,,,,;;' ,;; ,;;, ;;, ,;;, ,;;, `:,,,,:'          `;..``::::''..;'
       |                                                       ``::,,,,::''
       |
       |  Welcome to Smile Shell; enter 'help<RETURN>' for the list of commands.
       |  Type "exit<RETURN>" to leave the Smile Shell
       |  Version ${BuildInfo.version}, Scala ${BuildInfo.scalaVersion}, SBT ${BuildInfo.sbtVersion}, Built at ${BuildInfo.builtAtString}
       |===============================================================================
     """.stripMargin

  val imports =
    s"""
       |import smile._
       |import smile.util._
       |import smile.math._
       |import java.lang.Math._
       |import smile.math.Math.{log2, logistic, factorial, choose, random, randomInt, permutate, c, cbind, rbind, sum, mean, median, q1, q3, `var` => variance, sd, mad, min, max, whichMin, whichMax, unique, dot, distance, pdist, KullbackLeiblerDivergence => kld, JensenShannonDivergence => jsd, cov, cor, spearman, kendall, norm, norm1, norm2, normInf, standardize, normalize, scale, unitize, unitize1, unitize2, root}
       |import smile.math.distance._
       |import smile.math.kernel._
       |import smile.math.matrix._
       |import smile.math.matrix.Matrix._
       |import smile.stat.distribution._
       |import smile.data._
       |import java.awt.Color, smile.plot._
       |import smile.interpolation._
       |import smile.validation._
       |import smile.association._
       |import smile.classification._
       |import smile.regression.{ols, ridge, lasso, svr, gpr}
       |import smile.feature._
       |import smile.clustering._
       |import smile.vq._
       |import smile.manifold._
       |import smile.mds._
       |import smile.sequence._
       |import smile.projection._
       |import smile.nlp._
       |import smile.wavelet._
       |import smile.shell._
       |repl.prompt() = "smile> "
     """.stripMargin

  val repl = ammonite.Main(
    predefCode = imports,
    defaultPredef = true,
    storageBackend = new Storage.Folder(home),
    welcomeBanner = Some(welcome),
    verboseOutput = false
  )

  def run() = repl.run()
  def runCode(code: String) = repl.runCode(code)
  def runScript(path: Path) = repl.runScript(path, Seq.empty)
}
