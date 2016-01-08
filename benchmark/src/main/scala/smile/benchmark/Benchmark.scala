/*******************************************************************************
 * Copyright (c) 2010 Haifeng Li
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
