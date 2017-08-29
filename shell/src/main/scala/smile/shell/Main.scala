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

import scala.tools.nsc._, GenericRunnerCommand._, io.File

/** An object that runs Smile script or interactive shell.
  * Based on Scala MainGenericRunner.
  *
  * @author Haifeng Li
  */
object Main extends App {

  // This is actually the main function
  if (!process(args)) sys.exit(1)

  def errorFn(str: String, e: Option[Throwable] = None, isFailure: Boolean = true): Boolean = {
    if (str.nonEmpty) Console.err println str
    e foreach (_.printStackTrace())
    !isFailure
  }

  def process(args: Array[String]) = {
    val command = new GenericRunnerCommand(args.toList, (x: String) => errorFn(x))
    import command.{ settings, shortUsageMsg, shouldStopWithInfo }
    settings.usejavacp.value = true
    settings.deprecation.value = true
    def sampleCompiler = new Global(settings)   // def so it's not created unless needed

    def run(): Boolean = {
      def isE   = !settings.execute.isDefault
      def dashe = settings.execute.value

      def isI   = !settings.loadfiles.isDefault
      def dashi = settings.loadfiles.value

      // Deadlocks on startup under -i unless we disable async.
      if (isI)
        settings.Yreplsync.value = true

      /** If -e and -i were both given, we want to execute the -e code after the
        *  -i files have been included, so they are read into strings and prepended to
        *  the code given in -e.  The -i option is documented to only make sense
        *  interactively so this is a pretty reasonable assumption.
        */
      if (isI)
        dashi.foreach(file => Shell.runScript(file, Seq()))//command.arguments))

      if (isE) {
        val code = dashe.mkString("\n\n")
        Shell.runCode(code)
      }

      if (!isI && !isE)
        Shell.run()

      true
    }

    if (!command.ok)
      errorFn(f"%n$shortUsageMsg")
    else if (shouldStopWithInfo)
      errorFn(command getInfoMessage sampleCompiler, isFailure = false)
    else
      run()
  }
}