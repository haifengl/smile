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

/** Java REPL.
  *
  * @author Haifeng Li
  */
object JavaREPL {
  def main0(args: Array[String]): Unit = {
    val home = System.getProperty("smile.home", ".")
    val startup = Array(
      "--startup", "DEFAULT",
      "--startup", "PRINTING",
      "--startup", s"$home/bin/predef.jsh",
      "--feedback", "smile")
    jdk.jshell.tool.JavaShellToolBuilder
      .builder()
      .start(Array.concat(startup, args)*)
  }
}
