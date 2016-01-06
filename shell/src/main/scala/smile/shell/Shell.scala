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

import scala.sys.SystemProperties
import scala.tools.nsc.interpreter.ILoop
import scala.tools.nsc.Settings

/**
 * SMILE shell.
 * 
 * @author Haifeng Li
 */
object Shell extends App {
  val settings = new Settings
  settings.usejavacp.value = true
  settings.deprecation.value = true

  new SmileILoop().process(settings)

  class SmileILoop extends ILoop  {
    override def prompt = "smile> "
    override def printWelcome = echo(
      s"""
         |                                                        ..::''''::..
         |                                                      .;''        ``;.
         |      ....                                           ::    ::  ::    ::
         |    ,;' .;:                ()  ..:                  ::     ::  ::     ::
         |    ::.      ..:,:;.,:;.    .   ::   .::::.         :: .:' ::  :: `:. ::
         |     '''::,   ::  ::  ::  `::   ::  ;:   .::        ::  :          :  ::
         |   ,:';  ::;  ::  ::  ::   ::   ::  ::,::''.         :: `:.      .:' ::
         |   `:,,,,;;' ,;; ,;;, ;;, ,;;, ,;;, `:,,,,:'          `;..``::::''..;'
         |                                                        ``::,,,,::''
         |
         |   Welcome to SMILE Shell; enter 'help<RETURN>' for list of supported commands.
         |   Type ":quit<RETURN>" to leave the SMILE Shell
         |   Version ${BuildInfo.version}, Scala ${BuildInfo.scalaVersion}, SBT ${BuildInfo.sbtVersion}, Built at ${BuildInfo.builtAtString}
         |===============================================================================
       """.stripMargin
    )
  }
}
