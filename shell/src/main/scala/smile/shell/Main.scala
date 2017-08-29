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

import ammonite.main.Cli
import ammonite.ops.{Path, pwd}

/** An object that runs Smile script or interactive shell.
  * Based on Scala MainGenericRunner.
  *
  * @author Haifeng Li
  */
object Main extends App {

  Cli.groupArgs(args.toList, Cli.ammoniteArgSignature, Cli.Config()) match{
    case Left(msg) =>
      println(msg)
      false
    case Right((cliConfig, leftoverArgs)) =>
      if (cliConfig.help) {
        println(Cli.ammoniteHelp)
        true
      } else {
        (cliConfig.code, leftoverArgs) match{
          case (Some(code), Nil) =>
            Shell.runCode(code)

          case (None, Nil) =>
            Shell.run()
            true

          case (None, head :: rest) if head.startsWith("-") =>
            val failureMsg = s"Unknown option: $head\nUse --help to list possible options"
            println(failureMsg)
            false

          case (None, head :: rest) =>
            val success = Shell.runScript(Path(head, pwd)) // ignore script args for now
            success
        }
      }
  }
}