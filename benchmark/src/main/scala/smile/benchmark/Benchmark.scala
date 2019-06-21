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
 
package smile.benchmark

/**
 *
 * @author Haifeng Li
*/
object Benchmark {

  def main(args: Array[String]): Unit = {
    val tests = if (args.isEmpty) Array("airline-100k", "usps") else args

    tests foreach ( _ match {
      case "airline" => Airline.benchmark
      case "airline-100k" => Airline.benchmark("0.1m")
      case "airline-1m" => Airline.benchmark("1m")
      case "usps" => USPS.benchmark
      case test => println(
        s"""
           |Unknown benchmark $test
           |Available benchmarks: airline-100k, airline-1m, airline, usps
         """.stripMargin
      )
    })
  }
}
