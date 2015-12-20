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

import smile.data._
import smile.data.parser.DelimitedTextParser
import smile.classification.{SVM, RandomForest}
import smile.math.Math
import smile.math.kernel.GaussianKernel

/**
 *
 * @author Haifeng Li
*/
object Benchmark {

  def main(args: Array[String]): Unit = {
    usps
  }

  def benchm() {
    val parser = new DelimitedTextParser()
    parser.setDelimiter(",")
    parser.setColumnNames(true)
    parser.setResponseIndex(new NominalAttribute("class"), 8)
    val attributes = new Array[Attribute](8)
    attributes(0) = new NominalAttribute("V1")
    attributes(1) = new NominalAttribute("V2")
    attributes(2) = new NominalAttribute("V3")
    attributes(3) = new NumericAttribute("V4")
    attributes(4) = new NominalAttribute("V5")
    attributes(5) = new NominalAttribute("V6")
    attributes(6) = new NominalAttribute("V7")
    attributes(7) = new NumericAttribute("V8")

    val train = parser.parse("Benchmark train", attributes, "test-data/src/main/resources/smile/data/benchm-ml/train-1m.csv")
    val test = parser.parse("Benchmark Test", attributes, "test-data/src/main/resources//smile/data/benchm-ml/test.csv")

    val x = train.toArray(new Array[Array[Double]](train.size))
    val y = train.toArray(new Array[Int](train.size))
    val testx = test.toArray(new Array[Array[Double]](test.size))
    val testy = test.toArray(new Array[Int](test.size))

    val start = System.currentTimeMillis()
    val forest = new RandomForest(x, y, 200)
    val end = System.currentTimeMillis()
    println("Random forest 200 trees training time: %.2fs" format ((end-start)/1000.0))

    val error = (0 until testx.length).foldLeft(0) { (e, i) =>
      if (forest.predict(testx(i)) != testy(i)) e + 1 else e
    }

    println("Benchmark OOB error rate = %.2f%%" format (100.0 * forest.error()))
    println("Benchmark error rate = %.2f%%" format (100.0 * error / testx.length))
  }

  def usps() {
    println("USPS")
    val parser = new DelimitedTextParser
    parser.setResponseIndex(new NominalAttribute("class"), 0)

    val train = parser.parse("USPS Train", this.getClass.getResourceAsStream("/smile/data/usps/zip.train"))
    val test = parser.parse("USPS Test", this.getClass.getResourceAsStream("/smile/data/usps/zip.test"))
    val x = train.toArray(new Array[Array[Double]](train.size))
    val y = train.toArray(new Array[Int](train.size))
    val testx = test.toArray(new Array[Array[Double]](test.size))
    val testy = test.toArray(new Array[Int](test.size))

    var start = System.currentTimeMillis
    val forest = new RandomForest(x, y, 200)
    var end = System.currentTimeMillis
    println("Random Forest 200 trees training time: %.2fs" format ((end-start)/1000.0))

    var error = (0 until testx.length).foldLeft(0) { (e, i) =>
      if (forest.predict(testx(i)) != testy(i)) e + 1 else e
    }

    println("Random Forest OOB error rate = %.2f%%" format (100.0 * forest.error()))
    println("Random Forest error rate = %.2f%%" format (100.0 * error / testx.length))

    start = System.currentTimeMillis
    val svm = new SVM[Array[Double]](new GaussianKernel(8.0), 5.0, Math.max(y: _*) + 1, SVM.Multiclass.ONE_VS_ONE)
    svm.learn(x, y)
    svm.finish
    end = System.currentTimeMillis
    println("SVM one epoch training time: %.2fs" format ((end-start)/1000.0))

    error = (0 until testx.length).foldLeft(0) { (e, i) =>
      if (svm.predict(testx(i)) != testy(i)) e + 1 else e
    }

    println("SVM error rate = %.2f%%" format (100.0 * error / testx.length))

    println("SVM one more epoch...")
    start = System.currentTimeMillis
    (0 until x.length) foreach { _ =>
      val j = Math.randomInt(x.length)
      svm.learn(x(j), y(j))
    }

    svm.finish
    end = System.currentTimeMillis
    println("SVM one more epoch training time: %.2fs" format ((end-start)/1000.0))

    error = (0 until testx.length).foldLeft(0) { (e, i) =>
      if (svm.predict(testx(i)) != testy(i)) e + 1 else e
    }

    println("SVM error rate = %.2f%%" format (100.0 * error / testx.length))
  }
}
