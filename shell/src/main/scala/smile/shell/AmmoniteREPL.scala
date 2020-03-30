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
 *******************************************************************************/

package smile.shell

import ammonite.ops.Path
import ammonite.runtime.Storage

/** Ammonite REPL based shell.
  *
  * @author Haifeng Li
  */
case class AmmoniteREPL(predefCode: String) {
  val home = Path(System.getProperty("user.home")) / ".smile"
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

  val repl = ammonite.Main(
    predefCode = predefCode,
    defaultPredef = true,
    storageBackend = new Storage.Folder(home),
    welcomeBanner = Some(welcome),
    verboseOutput = false
  )

  def run() = repl.run()
  def runCode(code: String) = repl.runCode(code)
  def runScript(path: Path, args: Seq[(String, Option[String])]) = repl.runScript(path, args)
}
