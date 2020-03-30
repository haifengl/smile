/*******************************************************************************
 * Copyright (c) 2010-2019 Haifeng Li
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

package smile.shell

import ammonite.main.{Cli, Scripts}
import ammonite.ops.{Path, pwd}
import ammonite.util.Res

/** An object that runs Smile script or interactive shell.
  *
  * @author Haifeng Li
  */
object Main extends App {

  val help =
    s"""
      |Smile Shell & Script-Runner, ${BuildInfo.version}
      |usage: smile [smile-options] [script-file [script-options]]
      |
      |  --predef-code        Any commands you want to execute at the start of the Shell session
      |  -c, --code           Pass in code to be run immediately in the Shell
      |  -h, --home           The home directory of the Shell; where it looks for config and caches
      |  -p, --predef         Lets you load your predef from a custom location, rather than the
      |                       default location in your Smile home
      |  --no-home-predef     Disables the default behavior of loading predef files from your
      |                       ~/.smile/predef.sc, predefScript.sc, or predefShared.sc. You can
      |                       choose an additional predef to use using `--predef
      |  --no-default-predef  Disable the default predef and run Smile with the minimal predef
      |                       possible
      |  --help               Print this message
    """.stripMargin

  val imports =
    s"""
       |import scala.language.postfixOps
       |import org.apache.commons.csv.CSVFormat
       |import smile._
       |import smile.util._
       |import smile.math._
       |import java.lang.Math._
       |import smile.math.MathEx.{log2, logistic, factorial, lfactorial, choose, lchoose, random, randomInt, permutate, c, cbind, rbind, sum, mean, median, q1, q3, `var` => variance, sd, mad, min, max, whichMin, whichMax, unique, dot, distance, pdist, KullbackLeiblerDivergence => kld, JensenShannonDivergence => jsd, cov, cor, spearman, kendall, norm, norm1, norm2, normInf, standardize, normalize, scale, unitize, unitize1, unitize2, root}
       |import smile.math.distance._
       |import smile.math.kernel._
       |import smile.math.matrix._
       |import smile.math.matrix.Matrix._
       |import smile.math.rbf._
       |import smile.stat.distribution._
       |import smile.data._
       |import smile.data.formula._
       |import smile.data.measure._
       |import smile.data.`type`._
       |import java.awt.Color.{BLACK, BLUE, CYAN, DARK_GRAY, GRAY, GREEN, LIGHT_GRAY, MAGENTA, ORANGE, PINK, RED, WHITE, YELLOW}
       |import smile.plot.swing.Palette.{DARK_RED, VIOLET_RED, DARK_GREEN, LIGHT_GREEN, PASTEL_GREEN, FOREST_GREEN, GRASS_GREEN, NAVY_BLUE, SLATE_BLUE, ROYAL_BLUE, CADET_BLUE, MIDNIGHT_BLUE, SKY_BLUE, STEEL_BLUE, DARK_BLUE, DARK_MAGENTA, DARK_CYAN, PURPLE, LIGHT_PURPLE, DARK_PURPLE, GOLD, BROWN, SALMON, TURQUOISE, BURGUNDY, PLUM}
       |import smile.plot.swing._
       |import smile.plot.{desktop, javafx, show}
       |import smile.interpolation._
       |import smile.validation._
       |import smile.association._
       |import smile.base.cart.SplitRule
       |import smile.base.mlp._
       |import smile.base.rbf.RBF
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
     """.stripMargin

  val prompt =
    """
      |repl.prompt() = "smile> "
    """.stripMargin

  if (System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("windows")) {
    // Change the terminal mode so that it accepts ANSI escape codes
    if (!io.github.alexarchambault.windowsansi.WindowsAnsi.setup)
      println("Your Windows doesn't support ANSI escape codes. Please use Windows 10 build 10586 onwards.")
  }

  /** Handle the Ammonite results. */
  def isSuccess(res: Res[Any]): Boolean = res match {
    case Res.Failure(msg) =>
      println(msg)
      false

    case ammonite.util.Res.Exception(t, msg) =>
      println(msg)
      t.printStackTrace
      false

    case _ => true
  }

  Cli.groupArgs(args.toList, Cli.ammoniteArgSignature, Cli.Config()) match {
    case Left(msg) =>
      println(msg)
      false

    case Right((cliConfig, leftoverArgs)) =>
      if (cliConfig.help) {
        println(help)
        true
      } else {
        (cliConfig.code, leftoverArgs) match {
          case (Some(code), Nil) =>
            val runner = AmmoniteREPL(imports)
            runner.runCode(code)

          case (None, Nil) =>
            val runner = AmmoniteREPL(imports + prompt)
            val (res, _) = runner.run()
            isSuccess(res)

          case (None, head :: _) if head.startsWith("-") =>
            println(s"Unknown option: $head\nUse --help to list possible options")
            false

          case (None, head :: rest) =>
            val runner = AmmoniteREPL(imports)
            val (res, _) = runner.runScript(Path(head, pwd), Scripts.groupArgs(rest))
            isSuccess(res)
        }
      }
  }
}
