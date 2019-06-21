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

package smile.shell

import scala.tools.nsc.interpreter.ILoop

/** Scala REPL based shell.
  *
  * @author Haifeng Li
  */
class ScalaREPL extends ILoop  {
  override def prompt = "smile> "
  override def printWelcome = echo(
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
       |  Type ":quit<RETURN>" to leave the Smile Shell
       |  Version ${BuildInfo.version}, Scala ${BuildInfo.scalaVersion}, SBT ${BuildInfo.sbtVersion}, Built at ${BuildInfo.builtAtString}
       |===============================================================================
     """.stripMargin
  )
}