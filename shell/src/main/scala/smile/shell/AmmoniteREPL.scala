/*
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
 */

package smile.shell

/** Ammonite REPL based shell.
  *
  * @author Haifeng Li
  */
object AmmoniteREPL {
  def main0(clazz: Class[_], args0: Array[String]): Unit = {
    val home = System.getProperty("user.home") + "/.smile"
    val code =
      """
        |repl.prompt() = "smile> "
        |/*
        |if (System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("windows")) {
        |  import $ivy.`io.github.alexarchambault.windows-ansi:windows-ansi:0.0.3`
        |  // Change the terminal mode so that it accepts ANSI escape codes
        |  if (!io.github.alexarchambault.windowsansi.WindowsAnsi.setup)
        |    println("Your Windows doesn't support ANSI escape codes. Please use Windows 10 build 10586 onwards.")
        |}*/""".stripMargin
    
    val args = "--home" :: home ::
               "--predef" :: System.getProperty("scala.repl.autoruncode") ::
               "--predef-code" :: code ::
               "--banner" :: welcome("exit") :: args0.toList

    val method = clazz.getMethod("main", classOf[Array[String]])
    val main = clazz.getField("MODULE$").get(null)
    method.invoke(main, args.toArray)
  }
}
