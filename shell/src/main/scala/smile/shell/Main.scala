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

import smile.studio.SmileStudio

/** An object that runs Smile script or interactive shell.
  *
  * @author Haifeng Li
  */
object Main {
  def main(args: Array[String]): Unit = {
    val command = args.headOption.getOrElse("")
    command match {
      case "train" => Train(args.drop(1))
      case "predict" => Predict(args.drop(1))
      case "serve" => Serve(args.drop(1))
      case "scala" => ScalaREPL(args.drop(1))
      case "shell" => JavaREPL(args.drop(1))
      case _ => SmileStudio.start(args)
    }
  }
}
