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

package smile

/** Common shell commands.
  *
  * @author Haifeng Li
  */
package object shell {
  def welcome(exit: String): String =
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
       |  Welcome to Smile Shell! Type "$exit<RETURN>" to leave the Smile Shell.
       |  Version ${BuildInfo.version}, Scala ${BuildInfo.scalaVersion}, SBT ${BuildInfo.sbtVersion}, Built at ${BuildInfo.builtAtString}
       |===============================================================================
     """.stripMargin

  /** Built in benchmarks */
  def benchmark(tests: String*) = {
    smile.benchmark.Benchmark.main(tests.toArray)
  }

  /** Show demo window */
  def demo = {
    javax.swing.SwingUtilities.invokeLater(new Runnable {
      override def run(): Unit = {
        smile.demo.SmileDemo.createAndShowGUI(false)
      }
    })
  }
}
