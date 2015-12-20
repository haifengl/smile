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

import smile.data.Attribute
import smile.data.AttributeDataset
import smile.data.NominalAttribute
import smile.data.parser.DelimitedTextParser
import smile.classification.RandomForest

/**
 *
 * @author Haifeng Li
*/
object Benchmark {

  def main(args: Array[String]) {
    val parser = new DelimitedTextParser()
    parser.setDelimiter(",")
    parser.setColumnNames(true)
    parser.setResponseIndex(new NominalAttribute("class"), 8)
    val attributes = (0 until 8) map { i => new NominalAttribute("V"+i) }.toArray

    val train = parser.parse("Benchmark train", attributes, "benchm-ml/train-1m.csv")
    val test = parser.parse("Benchmark Test", attributes, "/smile/data/benchm-ml/test.csv")

    val x = train.toArray(new Array[Array[Double]](train.size))
    val y = train.toArray(new Array[Int](train.size))
    val testx = test.toArray(new Array[Array[Double]](test.size))
    val testy = test.toArray(new Array[Int](test.size))

    val start = System.currentTimeMillis()
    val forest = new RandomForest(x, y, 100)
    val end = System.currentTimeMillis()
    println("Random forest 100 trees training time: %.2f" format ((end-start)/1000.0))

    val error = (0 until testx.length).foldLeft(0) { (e, i) =>
      if (forest.predict(testx(i)) != testy(i)) e + 1 else e
    }

    println("Benchmark OOB error rate = %.2f" format (100.0 * forest.error()))
    println("Benchmark error rate = %.2f" format (100.0 * error / testx.length))
  }
}
