/*
 * Copyright (c) 2010-2025 Haifeng Li. All rights reserved.
 *
 * Smile Shell is free software: you can redistribute it and/or modify
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile Shell is distributed in the hope that it will be useful,
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.shell

/** Scala REPL.
  *
  * @author Haifeng Li
  */
object ScalaREPL extends scala.tools.nsc.MainGenericRunner {
  def apply(args: Array[String]): Unit = {
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
         |  Welcome to Smile $version! Type ":quit<RETURN>" to leave the Smile Shell.
         |===============================================================================
     """.stripMargin
    System.setProperty("scala.repl.prompt", "%nsmile> ")
    System.setProperty("scala.repl.welcome", welcome)
    
    if (!process(args)) System.exit(1)
  }
}
